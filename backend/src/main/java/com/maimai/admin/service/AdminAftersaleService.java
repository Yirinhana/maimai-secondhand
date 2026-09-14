package com.maimai.admin.service;

import com.maimai.admin.dto.AdminDtos;
import com.maimai.aftersales.domain.Aftersale;
import com.maimai.aftersales.domain.AftersaleLog;
import com.maimai.aftersales.repo.AftersaleLogRepository;
import com.maimai.aftersales.repo.AftersaleRepository;
import com.maimai.common.BizException;
import com.maimai.common.security.AuthenticatedUser;
import com.maimai.common.security.SecurityUtils;
import com.maimai.notification.NotificationService;
import com.maimai.payment.service.RefundService;
import com.maimai.trade.api.TradeOrderOps;
import com.maimai.trade.domain.Order;
import com.maimai.trade.repo.OrderRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/** 后台人工售后处理（须 SUPER_ADMIN 或 SUPPORT）。 */
@Service
@Transactional
public class AdminAftersaleService {

    private final AftersaleRepository aftersaleRepository;
    private final AftersaleLogRepository aftersaleLogRepository;
    private final OrderRepository orderRepository;
    private final RefundService refundService;
    private final TradeOrderOps tradeOrderOps;
    private final NotificationService notificationService;
    private final AdminAuditService adminAuditService;

    public AdminAftersaleService(AftersaleRepository aftersaleRepository,
                                 AftersaleLogRepository aftersaleLogRepository,
                                 OrderRepository orderRepository,
                                 RefundService refundService,
                                 TradeOrderOps tradeOrderOps,
                                 NotificationService notificationService,
                                 AdminAuditService adminAuditService) {
        this.aftersaleRepository = aftersaleRepository;
        this.aftersaleLogRepository = aftersaleLogRepository;
        this.orderRepository = orderRepository;
        this.refundService = refundService;
        this.tradeOrderOps = tradeOrderOps;
        this.notificationService = notificationService;
        this.adminAuditService = adminAuditService;
    }

    @Transactional(readOnly = true)
    public AdminDtos.PageResult<AdminDtos.AdminAftersaleItem> list(String status, int page, int size) {
        Aftersale.Status filter = Aftersale.Status.PENDING_MANUAL;
        if (status != null && !status.isBlank()) {
            try {
                filter = Aftersale.Status.valueOf(status.trim().toUpperCase());
            } catch (IllegalArgumentException ex) {
                throw BizException.badRequest("AFTERSALE_STATUS_INVALID", "非法的售后状态筛选");
            }
        }
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<Aftersale> result = aftersaleRepository.findByStatus(filter, pageable);
        Map<Long, Order> orders = orderRepository.findAllById(
                result.getContent().stream().map(Aftersale::getOrderId).distinct().toList())
                .stream().collect(Collectors.toMap(Order::getId, Function.identity()));
        List<AdminDtos.AdminAftersaleItem> content = result.getContent().stream()
                .map(a -> {
                    Order order = orders.get(a.getOrderId());
                    return new AdminDtos.AdminAftersaleItem(a.getId(), a.getAftersaleNo(), a.getOrderId(),
                            order == null ? null : order.getOrderNo(), a.getBuyerId(), a.getType().name(),
                            a.getReason(), a.getGoodsAmountCents(), a.getFreightAmountCents(),
                            a.getStatus().name(), a.getCreatedAt());
                })
                .toList();
        return new AdminDtos.PageResult<>(content, result.getTotalElements(),
                result.getNumber(), result.getSize());
    }

    /** 人工处理：REFUND → 创建退款后 RESOLVED；REJECT → CLOSED。两者都恢复订单自动确认并审计。 */
    public AdminDtos.AdminAftersaleItem resolve(Long id, AdminDtos.ResolveAftersaleRequest request) {
        AuthenticatedUser current = SecurityUtils.current();
        if (!current.hasRole("SUPER_ADMIN") && !current.hasRole("SUPPORT")) {
            throw BizException.forbidden("人工处理售后须超级管理员或客服角色");
        }
        orderRepository.lockByAftersaleId(id).orElseThrow(() -> BizException.notFound("售后单不存在"));
        Aftersale aftersale = aftersaleRepository.lockById(id)
                .orElseThrow(() -> BizException.notFound("售后单不存在"));
        if (aftersale.getStatus() != Aftersale.Status.PENDING_MANUAL) {
            throw BizException.conflict("AFTERSALE_STATUS_INVALID", "仅待人工处理的售后单可执行此操作");
        }
        Order order = orderRepository.findById(aftersale.getOrderId())
                .orElseThrow(() -> BizException.notFound("订单不存在"));
        String before = aftersale.getStatus().name();

        switch (request.action()) {
            case "REFUND" -> {
                if (!current.hasRole("SUPER_ADMIN")) {
                    throw BizException.forbidden("人工退款资金操作需超级管理员审核执行");
                }
                long goods = request.goodsAmountCents() != null
                        ? request.goodsAmountCents() : aftersale.getGoodsAmountCents();
                long freight = request.freightAmountCents() != null
                        ? request.freightAmountCents() : aftersale.getFreightAmountCents();
                refundService.createRefund(order, aftersale.getId(), goods, freight);
                aftersale.setStatus(Aftersale.Status.RESOLVED);
                writeLog(aftersale.getId(), current.id(), "ADMIN", "ADMIN_RESOLVE_REFUND",
                        request.note() + "（商品款 " + goods + " 分，运费 " + freight + " 分）");
            }
            case "REJECT" -> {
                aftersale.setStatus(Aftersale.Status.CLOSED);
                writeLog(aftersale.getId(), current.id(), "ADMIN", "ADMIN_RESOLVE_REJECT", request.note());
            }
            default -> throw BizException.badRequest("RESOLVE_ACTION_INVALID", "非法的处理动作");
        }
        aftersale.setUpdatedAt(Instant.now());
        aftersaleRepository.save(aftersale);
        tradeOrderOps.resumeAutoConfirm(order.getId());
        adminAuditService.record("AFTERSALE_RESOLVE_" + request.action(), "AFTERSALE",
                aftersale.getId(), request.note(), before, aftersale.getStatus().name());
        String resultText = aftersale.getStatus() == Aftersale.Status.RESOLVED ? "退款已完成" : "售后已驳回关闭";
        notificationService.notify(aftersale.getBuyerId(), "AFTERSALE", "售后已由平台处理",
                "售后单 " + aftersale.getAftersaleNo() + " " + resultText + "。说明：" + request.note());
        notificationService.notify(order.getSellerId(), "AFTERSALE", "售后已由平台处理",
                "售后单 " + aftersale.getAftersaleNo() + " " + resultText + "。说明：" + request.note());
        return new AdminDtos.AdminAftersaleItem(aftersale.getId(), aftersale.getAftersaleNo(),
                aftersale.getOrderId(), order.getOrderNo(), aftersale.getBuyerId(),
                aftersale.getType().name(), aftersale.getReason(), aftersale.getGoodsAmountCents(),
                aftersale.getFreightAmountCents(), aftersale.getStatus().name(), aftersale.getCreatedAt());
    }

    private void writeLog(Long aftersaleId, Long actorId, String actorRole, String action, String note) {
        AftersaleLog log = new AftersaleLog();
        log.setAftersaleId(aftersaleId);
        log.setActorId(actorId);
        log.setActorRole(actorRole);
        log.setAction(action);
        log.setNote(note);
        aftersaleLogRepository.save(log);
    }
}
