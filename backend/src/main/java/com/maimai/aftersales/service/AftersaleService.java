package com.maimai.aftersales.service;

import com.maimai.aftersales.domain.Aftersale;
import com.maimai.aftersales.domain.AftersaleLog;
import com.maimai.aftersales.dto.AftersaleDtos;
import com.maimai.aftersales.repo.AftersaleLogRepository;
import com.maimai.aftersales.repo.AftersaleQueryRepository;
import com.maimai.aftersales.repo.AftersaleRepository;
import com.maimai.common.BizException;
import com.maimai.common.NoGenerator;
import com.maimai.common.security.SecurityUtils;
import com.maimai.notification.NotificationService;
import com.maimai.payment.service.RefundService;
import com.maimai.trade.api.TradeOrderOps;
import com.maimai.trade.domain.Order;
import com.maimai.trade.repo.OrderRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/** 售后业务：申请、卖家响应、退货寄回、验退确认。状态机见 Aftersale.Status。 */
@Service
@Transactional
public class AftersaleService {

    /** 卖家响应时限 48 小时；退货寄回窗口 7 天；完成后可申请售后的期限 15 天。 */
    private static final Duration SELLER_RESPONSE_LIMIT = Duration.ofHours(48);
    private static final Duration RETURN_SHIP_LIMIT = Duration.ofDays(7);
    private static final Duration COMPLETED_AFTERSALE_LIMIT = Duration.ofDays(15);

    private final AftersaleRepository aftersaleRepository;
    private final AftersaleLogRepository aftersaleLogRepository;
    private final AftersaleQueryRepository aftersaleQueryRepository;
    private final OrderRepository orderRepository;
    private final RefundService refundService;
    private final TradeOrderOps tradeOrderOps;
    private final NotificationService notificationService;
    private final JdbcTemplate jdbcTemplate;

    public AftersaleService(AftersaleRepository aftersaleRepository,
                            AftersaleLogRepository aftersaleLogRepository,
                            AftersaleQueryRepository aftersaleQueryRepository,
                            OrderRepository orderRepository,
                            RefundService refundService,
                            TradeOrderOps tradeOrderOps,
                            NotificationService notificationService,
                            JdbcTemplate jdbcTemplate) {
        this.aftersaleRepository = aftersaleRepository;
        this.aftersaleLogRepository = aftersaleLogRepository;
        this.aftersaleQueryRepository = aftersaleQueryRepository;
        this.orderRepository = orderRepository;
        this.refundService = refundService;
        this.tradeOrderOps = tradeOrderOps;
        this.notificationService = notificationService;
        this.jdbcTemplate = jdbcTemplate;
    }

    /** 买家本人对已支付订单发起售后；完成超 15 天的订单直接转人工（仍允许创建）。 */
    @Transactional(isolation = org.springframework.transaction.annotation.Isolation.READ_COMMITTED)
    public AftersaleDtos.AftersaleDetail create(String orderNo, AftersaleDtos.CreateAftersaleRequest request) {
        Long actor = SecurityUtils.currentUserId();
        List<String> actorStates = jdbcTemplate.queryForList("SELECT status FROM users WHERE id=? FOR UPDATE", String.class, actor);
        if (actorStates.isEmpty() || !"ACTIVE".equals(actorStates.getFirst()))
            throw BizException.forbidden("账号当前不可发起新的售后申请");
        Order order = orderRepository.lockByOrderNo(orderNo)
                .orElseThrow(() -> BizException.notFound("订单不存在"));
        SecurityUtils.requireOwner(order.getBuyerId());
        order.requireMutableRecord();
        if (order.getFulfillmentStatus() == Order.FulfillmentStatus.PENDING_PAYMENT
                || order.getFulfillmentStatus() == Order.FulfillmentStatus.CLOSED
                || order.getPayStatus() != Order.PayStatus.PAID) {
            throw BizException.conflict("AFTERSALE_NOT_ALLOWED", "订单当前状态不能申请售后");
        }
        long goods = request.goodsAmountCents();
        long freight = request.freightAmountCents();
        if (goods < 0 || freight < 0 || (goods == 0 && freight == 0)) {
            throw BizException.badRequest("AFTERSALE_AMOUNT_INVALID", "售后金额必须大于 0");
        }
        long[] refunded = refundedSums(order.getId());
        if (goods > order.getGoodsAmountCents() - refunded[0]) {
            throw BizException.badRequest("AFTERSALE_AMOUNT_EXCEEDED", "商品款超过剩余可退金额");
        }
        if (freight > order.getFreightCents() - refunded[1]) {
            throw BizException.badRequest("AFTERSALE_AMOUNT_EXCEEDED", "运费超过剩余可退金额");
        }
        if (aftersaleRepository.existsByOrderIdAndStatusIn(order.getId(), List.of(
                Aftersale.Status.PENDING_SELLER, Aftersale.Status.PENDING_RETURN,
                Aftersale.Status.RETURN_SHIPPED, Aftersale.Status.PENDING_MANUAL))) {
            throw BizException.conflict("AFTERSALE_IN_PROGRESS", "该订单存在进行中的售后单");
        }

        boolean overdueManual = order.getCompletedAt() != null
                && order.getCompletedAt().plus(COMPLETED_AFTERSALE_LIMIT).isBefore(Instant.now());
        boolean sellerUnavailable = !"ACTIVE".equals(jdbcTemplate.queryForObject("SELECT status FROM users WHERE id=?", String.class, order.getSellerId()));
        boolean manualRequired = overdueManual || sellerUnavailable;

        Aftersale aftersale = new Aftersale();
        aftersale.setAftersaleNo(NoGenerator.next("MA"));
        aftersale.setOrderId(order.getId());
        aftersale.setBuyerId(order.getBuyerId());
        aftersale.setType(request.type());
        aftersale.setReason(request.reason());
        aftersale.setGoodsAmountCents(goods);
        aftersale.setFreightAmountCents(freight);
        aftersale.setEvidence(request.evidence());
        aftersale.setSellerDeadline(Instant.now().plus(SELLER_RESPONSE_LIMIT));
        if (manualRequired) {
            aftersale.setStatus(Aftersale.Status.PENDING_MANUAL);
        }
        aftersaleRepository.save(aftersale);
        writeLog(aftersale.getId(), order.getBuyerId(), "BUYER", "CREATE",
                manualRequired ? "买家发起售后；超常规窗口或卖家账号不可用，直接转人工" : "买家发起售后");

        tradeOrderOps.pauseAutoConfirm(order.getId(), "售后单 " + aftersale.getAftersaleNo() + " 处理中");
        notificationService.notify(order.getSellerId(), "AFTERSALE", "收到新的售后申请",
                "订单 " + order.getOrderNo() + " 售后单 " + aftersale.getAftersaleNo()
                        + "（" + request.type().name() + "），请在 48 小时内响应");
        if (manualRequired) {
            notifySuperAdmins("售后需要人工处理",
                    "订单 " + order.getOrderNo() + " 超常规售后窗口或卖家账号不可用，售后单 "
                            + aftersale.getAftersaleNo() + " 直接转人工处理");
        }
        return detail(aftersale.getId());
    }

    @Transactional(readOnly = true)
    public AftersaleDtos.PageResult<AftersaleDtos.AftersaleSummary> listMine(int page, int size) {
        Pageable pageable = PageRequest.of(page, size);
        Page<Aftersale> result = aftersaleRepository
                .findByBuyerIdOrderByCreatedAtDesc(SecurityUtils.currentUserId(), pageable);
        return toPage(result);
    }

    @Transactional(readOnly = true)
    public AftersaleDtos.PageResult<AftersaleDtos.AftersaleSummary> listSeller(int page, int size) {
        Pageable pageable = PageRequest.of(page, size);
        Page<Aftersale> result = aftersaleQueryRepository.findBySellerId(SecurityUtils.currentUserId(), pageable);
        return toPage(result);
    }

    /** 售后详情：买卖双方与后台可见，含处理日志。 */
    @Transactional(readOnly = true)
    public AftersaleDtos.AftersaleDetail detail(Long id) {
        Aftersale aftersale = aftersaleRepository.findById(id)
                .orElseThrow(() -> BizException.notFound("售后单不存在"));
        Order order = orderRepository.findById(aftersale.getOrderId())
                .orElseThrow(() -> BizException.notFound("订单不存在"));
        Long currentUserId = SecurityUtils.currentUserId();
        if (!order.getBuyerId().equals(currentUserId) && !order.getSellerId().equals(currentUserId)
                && !SecurityUtils.current().hasRole("SUPER_ADMIN")
                && !SecurityUtils.current().hasRole("SUPPORT")) {
            throw BizException.notFound("售后单不存在或不可访问");
        }
        List<AftersaleDtos.AftersaleLogItem> logs = aftersaleLogRepository
                .findByAftersaleIdOrderByCreatedAtAsc(aftersale.getId()).stream()
                .map(l -> new AftersaleDtos.AftersaleLogItem(l.getId(), l.getActorId(), l.getActorRole(),
                        l.getAction(), l.getNote(), l.getCreatedAt()))
                .toList();
        return new AftersaleDtos.AftersaleDetail(aftersale.getId(), aftersale.getAftersaleNo(),
                order.getId(), order.getOrderNo(), aftersale.getBuyerId(), order.getSellerId(), aftersale.getType().name(),
                aftersale.getReason(), aftersale.getGoodsAmountCents(), aftersale.getFreightAmountCents(),
                aftersale.getStatus().name(), aftersale.getEvidence(), aftersale.getSellerReply(),
                aftersale.getSellerDeadline(), aftersale.getReturnDeadline(),
                aftersale.getReturnCarrier(), aftersale.getReturnTrackingNo(),
                aftersale.getCreatedAt(), aftersale.getUpdatedAt(), logs,
                aftersale.getReturnRecipient(), aftersale.getReturnPhone(), aftersale.getReturnAddress(),
                aftersale.getReturnShippedAt(), aftersale.getReturnReceivedAt(), aftersale.getReturnInspectionDeadline(), order.isInteractiveExperience());
    }

    /** 卖家响应：拒绝 → SELLER_REJECTED；同意仅退款 → 直接创建退款并解决；同意退货 → 进入 7 天寄回窗口。 */
    public AftersaleDtos.AftersaleDetail respond(Long id, AftersaleDtos.RespondRequest request) {
        Aftersale aftersale = lockAftersale(id);
        Order order = orderRepository.findById(aftersale.getOrderId())
                .orElseThrow(() -> BizException.notFound("订单不存在"));
        SecurityUtils.requireOwner(order.getSellerId());
        if (aftersale.getStatus() != Aftersale.Status.PENDING_SELLER) {
            throw BizException.conflict("AFTERSALE_STATUS_INVALID", "售后单当前状态不可响应");
        }
        if (expired(aftersale.getSellerDeadline())) {
            escalateExpired(id);
            return detail(id);
        }
        Long sellerId = SecurityUtils.currentUserId();
        if (!request.agree()) {
            if (request.reply() == null || request.reply().isBlank()) {
                throw BizException.badRequest("AFTERSALE_REPLY_REQUIRED", "拒绝售后时必须填写理由");
            }
            aftersale.setStatus(Aftersale.Status.SELLER_REJECTED);
            aftersale.setSellerReply(request.reply());
            aftersale.setUpdatedAt(Instant.now());
            aftersaleRepository.save(aftersale);
            writeLog(id, sellerId, "SELLER", "SELLER_REJECT", request.reply());
            tradeOrderOps.resumeAutoConfirm(order.getId());
            notificationService.notify(aftersale.getBuyerId(), "AFTERSALE", "售后申请被卖家拒绝",
                    "售后单 " + aftersale.getAftersaleNo() + " 被拒绝：" + request.reply());
            return detail(id);
        }
        aftersale.setSellerReply(request.reply());
        if (aftersale.getType() == Aftersale.Type.REFUND_ONLY) {
            refundService.createRefund(order, aftersale.getId(),
                    aftersale.getGoodsAmountCents(), aftersale.getFreightAmountCents());
            aftersale.setStatus(Aftersale.Status.RESOLVED);
            aftersale.setUpdatedAt(Instant.now());
            aftersaleRepository.save(aftersale);
            writeLog(id, sellerId, "SELLER", "SELLER_AGREE_REFUND", "卖家同意仅退款，退款已发起");
            tradeOrderOps.resumeAutoConfirm(order.getId());
            notificationService.notify(aftersale.getBuyerId(), "AFTERSALE", "售后已完成",
                    "售后单 " + aftersale.getAftersaleNo() + " 卖家已同意退款，退款处理完成");
        } else {
            String recipient = requiredReturnField(request.returnRecipient(), 50, "请填写退货收件人");
            String phone = requiredReturnField(request.returnPhone(), 30, "请填写退货联系电话");
            String address = requiredReturnField(request.returnAddress(), 500, "请填写完整有效的退货地址");
            if (!phone.matches("[+0-9() -]{7,30}") || phone.chars().filter(Character::isDigit).count() < 7 || address.length() < 8) {
                throw BizException.badRequest("RETURN_ADDRESS_INVALID", "请核对退货电话和完整地址");
            }
            aftersale.setReturnRecipient(recipient);
            aftersale.setReturnPhone(phone);
            aftersale.setReturnAddress(address);
            aftersale.setStatus(Aftersale.Status.PENDING_RETURN);
            aftersale.setReturnDeadline(Instant.now().plus(RETURN_SHIP_LIMIT));
            aftersale.setUpdatedAt(Instant.now());
            aftersaleRepository.save(aftersale);
            writeLog(id, sellerId, "SELLER", "SELLER_AGREE_RETURN", "卖家提供退货地址并同意退货，买家可在7天内寄回或申请人工核查地址");
            notificationService.notify(aftersale.getBuyerId(), "AFTERSALE", "卖家同意退货退款",
                    "售后单 " + aftersale.getAftersaleNo() + " 请在 7 天内填写退货物流寄回商品");
        }
        return detail(id);
    }

    /** 买家填写退货物流单号：PENDING_RETURN → RETURN_SHIPPED。 */
    public AftersaleDtos.AftersaleDetail returnShip(Long id, AftersaleDtos.ReturnShipRequest request) {
        Aftersale aftersale = lockAftersale(id);
        SecurityUtils.requireOwner(aftersale.getBuyerId());
        if (aftersale.getStatus() != Aftersale.Status.PENDING_RETURN) {
            throw BizException.conflict("AFTERSALE_STATUS_INVALID", "售后单当前状态不可填写退货物流");
        }
        if (aftersale.getReturnDeadline() != null && !aftersale.getReturnDeadline().isAfter(Instant.now())) {
            escalateExpired(id);
            return detail(id);
        }
        if (request.carrier() == null || !request.carrier().strip().matches("[a-zA-Z0-9_]{2,50}")
                || request.trackingNo() == null || !request.trackingNo().strip().matches("[a-zA-Z0-9-]{6,32}"))
            throw BizException.badRequest("RETURN_TRACKING_INVALID", "请填写有效的承运商代码和6至32位物流单号");
        aftersale.setReturnCarrier(request.carrier().strip().toLowerCase(java.util.Locale.ROOT));
        aftersale.setReturnTrackingNo(request.trackingNo().strip());
        aftersale.setReturnShippedAt(Instant.now());
        aftersale.setStatus(Aftersale.Status.RETURN_SHIPPED);
        aftersale.setUpdatedAt(Instant.now());
        aftersaleRepository.save(aftersale);
        writeLog(id, aftersale.getBuyerId(), "BUYER", "RETURN_SHIPPED",
                request.carrier() + " " + request.trackingNo());
        Order order = orderRepository.findById(aftersale.getOrderId())
                .orElseThrow(() -> BizException.notFound("订单不存在"));
        notificationService.notify(order.getSellerId(), "AFTERSALE", "买家已寄回商品",
                "售后单 " + aftersale.getAftersaleNo() + " 退货物流：" + request.carrier()
                        + " " + request.trackingNo() + "，收货后请及时确认");
        return detail(id);
    }

    /** 卖家验退确认：创建退款 → RESOLVED，并恢复订单自动确认。 */
    public AftersaleDtos.AftersaleDetail confirmReturn(Long id) {
        Aftersale aftersale = lockAftersale(id);
        Order order = orderRepository.findById(aftersale.getOrderId())
                .orElseThrow(() -> BizException.notFound("订单不存在"));
        SecurityUtils.requireOwner(order.getSellerId());
        if (aftersale.getStatus() != Aftersale.Status.RETURN_SHIPPED) {
            throw BizException.conflict("AFTERSALE_STATUS_INVALID", "售后单当前状态不可确认收货退款");
        }
        if (expired(aftersale.getReturnInspectionDeadline())) {
            escalateExpired(id);
            return detail(id);
        }
        recordReturnReceived(aftersale);
        refundService.createRefund(order, aftersale.getId(),
                aftersale.getGoodsAmountCents(), aftersale.getFreightAmountCents());
        aftersale.setStatus(Aftersale.Status.RESOLVED);
        aftersale.setUpdatedAt(Instant.now());
        aftersaleRepository.save(aftersale);
        writeLog(id, SecurityUtils.currentUserId(), "SELLER", "CONFIRM_RETURN", "卖家确认收到退货，退款已发起");
        tradeOrderOps.resumeAutoConfirm(order.getId());
        notificationService.notify(aftersale.getBuyerId(), "AFTERSALE", "售后已完成",
                "售后单 " + aftersale.getAftersaleNo() + " 卖家已确认收到退货，退款处理完成");
        return detail(id);
    }

    public AftersaleDtos.AftersaleDetail escalate(Long id) {
        Aftersale aftersale = lockAftersale(id);
        SecurityUtils.requireOwner(aftersale.getBuyerId());
        if (!List.of(Aftersale.Status.SELLER_REJECTED, Aftersale.Status.CLOSED,
                Aftersale.Status.PENDING_RETURN, Aftersale.Status.RETURN_SHIPPED,
                Aftersale.Status.RESOLVED).contains(aftersale.getStatus())) {
            throw BizException.conflict("AFTERSALE_STATUS_INVALID", "当前售后状态无需再次申请人工介入");
        }
        if (jdbcTemplate.queryForObject("SELECT COUNT(*) FROM aftersales WHERE order_id=? AND id<>? "
                + "AND status IN ('PENDING_SELLER','PENDING_RETURN','RETURN_SHIPPED','PENDING_MANUAL')", Long.class,
                aftersale.getOrderId(), id) > 0)
            throw BizException.conflict("AFTERSALE_IN_PROGRESS", "该订单已有其他售后处理中，请在进行中的售后补充材料");
        aftersale.setStatus(Aftersale.Status.PENDING_MANUAL);
        aftersale.setUpdatedAt(Instant.now());
        aftersaleRepository.save(aftersale);
        tradeOrderOps.pauseAutoConfirm(aftersale.getOrderId(), "买家申请人工介入");
        writeLog(id, aftersale.getBuyerId(), "BUYER", "ESCALATE_MANUAL", "买家对处理结果有异议，申请人工介入");
        notifySuperAdmins("售后争议待处理", "售后单 " + aftersale.getAftersaleNo() + " 买家申请人工介入");
        return detail(id);
    }

    private Aftersale lockAftersale(Long id) {
        orderRepository.lockByAftersaleId(id).orElseThrow(() -> BizException.notFound("售后单不存在"));
        return aftersaleRepository.lockById(id).orElseThrow(() -> BizException.notFound("售后单不存在"));
    }

    /** Each scheduled escalation locks and rechecks the current row in its own transaction. */
    public void escalateExpired(Long id) {
        Aftersale aftersale = lockAftersale(id);
        Instant now = Instant.now();
        boolean sellerTimeout = aftersale.getStatus() == Aftersale.Status.PENDING_SELLER
                && aftersale.getSellerDeadline() != null && !aftersale.getSellerDeadline().isAfter(now);
        boolean returnTimeout = aftersale.getStatus() == Aftersale.Status.PENDING_RETURN
                && aftersale.getReturnDeadline() != null && !aftersale.getReturnDeadline().isAfter(now);
        boolean inspectionTimeout = aftersale.getStatus() == Aftersale.Status.RETURN_SHIPPED
                && aftersale.getReturnInspectionDeadline() != null
                && !aftersale.getReturnInspectionDeadline().isAfter(now);
        boolean sellerUnavailable = List.of(Aftersale.Status.PENDING_SELLER, Aftersale.Status.PENDING_RETURN,
                Aftersale.Status.RETURN_SHIPPED).contains(aftersale.getStatus())
                && jdbcTemplate.queryForObject("SELECT COUNT(*) FROM orders o JOIN users u ON u.id=o.seller_id "
                    + "WHERE o.id=? AND u.status<>'ACTIVE'", Long.class, aftersale.getOrderId()) > 0;
        if (!sellerTimeout && !returnTimeout && !inspectionTimeout && !sellerUnavailable) return;
        String reason = sellerUnavailable ? "卖家账号不可用，转人工处理" : sellerTimeout ? "卖家48小时未响应，转人工处理" : inspectionTimeout
                ? "卖家确认退件签收后48小时未完成验退，转人工处理" : "买家7天退货寄回窗口超时，转人工处理";
        aftersale.setStatus(Aftersale.Status.PENDING_MANUAL);
        aftersale.setUpdatedAt(now);
        aftersaleRepository.save(aftersale);
        tradeOrderOps.pauseAutoConfirm(aftersale.getOrderId(), reason);
        writeLog(id, null, "SYSTEM", "ESCALATE_MANUAL", reason);
        notifySuperAdmins("售后超时转人工", "售后单 " + aftersale.getAftersaleNo() + " " + reason);
        notificationService.notify(aftersale.getBuyerId(), "AFTERSALE", "售后已转人工", reason);
        orderRepository.findById(aftersale.getOrderId()).ifPresent(order ->
                notificationService.notify(order.getSellerId(), "AFTERSALE", "售后已转人工", reason));
    }

    /** 卖家主动确认实物签收；未确认的签收争议由买家随时请求人工，不能假造物流签收。 */
    public AftersaleDtos.AftersaleDetail receiveReturn(Long id) {
        Aftersale aftersale = lockAftersale(id);
        Order order = orderRepository.findById(aftersale.getOrderId())
                .orElseThrow(() -> BizException.notFound("订单不存在"));
        SecurityUtils.requireOwner(order.getSellerId());
        if (aftersale.getStatus() != Aftersale.Status.RETURN_SHIPPED)
            throw BizException.conflict("AFTERSALE_STATUS_INVALID", "当前售后状态不可确认退件签收");
        if (expired(aftersale.getReturnInspectionDeadline())) {
            escalateExpired(id);
            return detail(id);
        }
        if (aftersale.getReturnReceivedAt() == null) {
            recordReturnReceived(aftersale);
            aftersaleRepository.save(aftersale);
            notificationService.notify(aftersale.getBuyerId(), "AFTERSALE", "卖家已确认退件签收",
                    "售后单 " + aftersale.getAftersaleNo() + " 已进入48小时验退窗口，超时转人工处理");
        }
        return detail(id);
    }

    private void recordReturnReceived(Aftersale aftersale) {
        if (aftersale.getReturnReceivedAt() != null) return;
        Instant now = Instant.now().truncatedTo(java.time.temporal.ChronoUnit.MICROS);
        aftersale.setReturnReceivedAt(now);
        aftersale.setReturnInspectionDeadline(now.plus(SELLER_RESPONSE_LIMIT));
        aftersale.setUpdatedAt(now);
        writeLog(aftersale.getId(), SecurityUtils.currentUserId(), "SELLER", "RETURN_RECEIVED",
                "卖家主动确认实物退件签收，48小时内验退；不代表渠道退款已成功");
    }

    private static String requiredReturnField(String value, int max, String message) {
        if (value == null || value.isBlank() || value.length() > max || value.chars().anyMatch(Character::isISOControl))
            throw BizException.badRequest("RETURN_ADDRESS_REQUIRED", message);
        return value.strip();
    }

    private static boolean expired(Instant deadline) {
        return deadline != null && !deadline.isAfter(Instant.now());
    }

    /** 该订单累计已成功退款金额：[0]=商品款 [1]=运费。 */
    long[] refundedSums(Long orderId) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT COALESCE(SUM(goods_refund_cents),0) g, COALESCE(SUM(freight_refund_cents),0) f"
                        + " FROM refunds WHERE order_id = ? AND status = 'SUCCESS'", orderId);
        Map<String, Object> row = rows.getFirst();
        return new long[]{((Number) row.get("g")).longValue(), ((Number) row.get("f")).longValue()};
    }

    void writeLog(Long aftersaleId, Long actorId, String actorRole, String action, String note) {
        AftersaleLog log = new AftersaleLog();
        log.setAftersaleId(aftersaleId);
        log.setActorId(actorId);
        log.setActorRole(actorRole);
        log.setAction(action);
        log.setNote(note);
        aftersaleLogRepository.save(log);
    }

    void notifySuperAdmins(String title, String content) {
        List<Long> adminIds = jdbcTemplate.queryForList(
                "SELECT user_id FROM user_roles WHERE role = 'SUPER_ADMIN'", Long.class);
        for (Long adminId : adminIds) {
            notificationService.notify(adminId, "AFTERSALE", title, content);
        }
    }

    private AftersaleDtos.PageResult<AftersaleDtos.AftersaleSummary> toPage(Page<Aftersale> result) {
        Map<Long, Order> orders = orderRepository.findAllById(
                result.getContent().stream().map(Aftersale::getOrderId).toList()).stream()
                .collect(Collectors.toMap(Order::getId, Function.identity()));
        List<AftersaleDtos.AftersaleSummary> content = result.getContent().stream()
                .map(a -> {
                    Order order = orders.get(a.getOrderId());
                    return new AftersaleDtos.AftersaleSummary(a.getId(), a.getAftersaleNo(), a.getOrderId(),
                            order == null ? null : order.getOrderNo(), a.getType().name(),
                            a.getGoodsAmountCents(), a.getFreightAmountCents(), a.getStatus().name(),
                            a.getSellerDeadline(), a.getReturnDeadline(), a.getCreatedAt());
                })
                .toList();
        return new AftersaleDtos.PageResult<>(content, result.getTotalElements(),
                result.getNumber(), result.getSize());
    }
}
