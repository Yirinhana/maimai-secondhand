package com.maimai.trade.service;

import com.maimai.catalog.domain.Product;
import com.maimai.catalog.domain.ProductImage;
import com.maimai.catalog.domain.StockLog;
import com.maimai.catalog.repo.ProductImageRepository;
import com.maimai.catalog.repo.ProductRepository;
import com.maimai.catalog.repo.StockLogRepository;
import com.maimai.common.BizException;
import com.maimai.common.FeeCalculator;
import com.maimai.common.NoGenerator;
import com.maimai.identity.domain.Address;
import com.maimai.identity.repo.AddressRepository;
import com.maimai.trade.domain.BargainOffer;
import com.maimai.trade.domain.CheckoutBatch;
import com.maimai.trade.domain.MeetupAppointment;
import com.maimai.trade.domain.Order;
import com.maimai.trade.domain.OrderItem;
import com.maimai.trade.dto.TradeDtos.CheckoutItem;
import com.maimai.trade.dto.TradeDtos.CheckoutRequest;
import com.maimai.trade.dto.TradeDtos.CheckoutResponse;
import com.maimai.trade.dto.TradeDtos.OrderDto;
import com.maimai.trade.repo.BargainOfferRepository;
import com.maimai.trade.repo.CartItemRepository;
import com.maimai.trade.repo.CheckoutBatchRepository;
import com.maimai.trade.repo.MeetupAppointmentRepository;
import com.maimai.trade.repo.OrderItemRepository;
import com.maimai.trade.repo.OrderQueryRepository;
import com.maimai.trade.repo.OrderRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * 结算：按 卖家+交付方式+目的地 拆子订单；事务内条件更新预留库存，任一不足整批回滚；
 * 幂等键重复提交返回原批次。
 */
@Service
public class CheckoutService {

    private static final Duration PAY_TTL = Duration.ofMinutes(30);

    private final CheckoutBatchRepository checkoutBatchRepository;
    private final CheckoutBatchCreator checkoutBatchCreator;
    private final OrderRepository orderRepository;
    private final OrderQueryRepository orderQueryRepository;
    private final OrderItemRepository orderItemRepository;
    private final ProductRepository productRepository;
    private final ProductImageRepository productImageRepository;
    private final StockLogRepository stockLogRepository;
    private final BargainOfferRepository bargainOfferRepository;
    private final AddressRepository addressRepository;
    private final MeetupAppointmentRepository meetupAppointmentRepository;
    private final CartItemRepository cartItemRepository;
    private final OrderDtoMapper orderDtoMapper;
    private final org.springframework.jdbc.core.JdbcTemplate jdbc;

    public CheckoutService(CheckoutBatchRepository checkoutBatchRepository,
                           CheckoutBatchCreator checkoutBatchCreator,
                           OrderRepository orderRepository,
                           OrderQueryRepository orderQueryRepository,
                           OrderItemRepository orderItemRepository,
                           ProductRepository productRepository,
                           ProductImageRepository productImageRepository,
                           StockLogRepository stockLogRepository,
                           BargainOfferRepository bargainOfferRepository,
                           AddressRepository addressRepository,
                           MeetupAppointmentRepository meetupAppointmentRepository,
                           CartItemRepository cartItemRepository,
                           OrderDtoMapper orderDtoMapper, org.springframework.jdbc.core.JdbcTemplate jdbc) {
        this.checkoutBatchRepository = checkoutBatchRepository;
        this.checkoutBatchCreator = checkoutBatchCreator;
        this.orderRepository = orderRepository;
        this.orderQueryRepository = orderQueryRepository;
        this.orderItemRepository = orderItemRepository;
        this.productRepository = productRepository;
        this.productImageRepository = productImageRepository;
        this.stockLogRepository = stockLogRepository;
        this.bargainOfferRepository = bargainOfferRepository;
        this.addressRepository = addressRepository;
        this.meetupAppointmentRepository = meetupAppointmentRepository;
        this.cartItemRepository = cartItemRepository;
        this.orderDtoMapper = orderDtoMapper;
        this.jdbc = jdbc;
    }

    @Transactional(isolation=org.springframework.transaction.annotation.Isolation.READ_COMMITTED)
    public CheckoutResponse checkout(Long userId, CheckoutRequest request) {
        if (request.items() == null || request.items().isEmpty() || request.items().size() > 100) {
            throw BizException.badRequest("CHECKOUT_ITEMS_INVALID", "每次结算需要1至100个商品项");
        }
        // Lock buyer and sellers in one stable order before product locks; closure cannot race a new trade.
        var users=new java.util.TreeSet<Long>();users.add(userId);
        for(var item:request.items())users.addAll(jdbc.queryForList("SELECT seller_id FROM products WHERE id=?",Long.class,item.productId()));
        for(Long id:users) {
            if(jdbc.queryForList("SELECT id FROM users WHERE id=? AND status='ACTIVE' FOR UPDATE",id).isEmpty()) {
                if(id.equals(userId))throw BizException.unauthorized("请先登录");
                throw BizException.conflict("SELLER_INACTIVE","卖家账号已停用，暂不可成交");
            }
        }
        Optional<CheckoutBatch> existing =
                checkoutBatchRepository.findByUserIdAndIdempotencyKey(userId, request.idempotencyKey());
        if (existing.isPresent()) {
            return responseOf(existing.get());
        }
        CheckoutBatch batch;
        try {
            batch = checkoutBatchCreator.create(userId, request.idempotencyKey());
        } catch (DataIntegrityViolationException e) {
            // 并发下同幂等键已由其他请求创建：回查返回原批次
            return responseOf(checkoutBatchRepository
                    .findByUserIdAndIdempotencyKey(userId, request.idempotencyKey())
                    .orElseThrow(() -> e));
        }

        Address address = null;
        List<Line> lines = resolveLines(userId, request);
        if (lines.stream().anyMatch(l -> l.method() == Order.DeliveryMethod.EXPRESS)) {
            if (request.addressId() == null) {
                throw BizException.badRequest("ADDRESS_REQUIRED", "快递交付必须选择收货地址");
            }
            address = addressRepository.findById(request.addressId())
                    .orElseThrow(() -> BizException.notFound("收货地址不存在"));
            if (!address.getUserId().equals(userId)) {
                throw BizException.forbidden("无权使用该收货地址");
            }
            for(Line line:lines)if(line.method()==Order.DeliveryMethod.EXPRESS)
                com.maimai.catalog.service.ProductShipping.requireCovered(line.product().getShippingProvinces(),address.getRegion());
        }

        Map<String, List<Line>> groups = new LinkedHashMap<>();
        for (Line line : lines) {
            groups.computeIfAbsent(groupKey(line, request), k -> new ArrayList<>()).add(line);
        }

        List<Order> orders = new ArrayList<>();
        Instant now = Instant.now();
        for (List<Line> group : groups.values()) {
            orders.add(createGroupOrder(userId, batch, group, request, address, now));
        }

        if (request.removeCartItemIds() != null && !request.removeCartItemIds().isEmpty()) {
            List<com.maimai.trade.domain.CartItem> owned = cartItemRepository
                    .findAllById(request.removeCartItemIds()).stream()
                    .filter(item -> item.getUserId().equals(userId))
                    .toList();
            cartItemRepository.deleteAll(owned);
        }

        return new CheckoutResponse(batch.getBatchNo(),
                orders.stream().map(orderDtoMapper::toDto).toList());
    }

    private CheckoutResponse responseOf(CheckoutBatch batch) {
        List<OrderDto> orders = orderQueryRepository.findByBatchIdOrderById(batch.getId()).stream()
                .map(orderDtoMapper::toDto)
                .toList();
        return new CheckoutResponse(batch.getBatchNo(), orders);
    }

    /** 解析并校验每个结算项：商品在售、交付方式支持、议价有效并确定成交单价。 */
    private List<Line> resolveLines(Long userId, CheckoutRequest request) {
        List<Line> lines = new ArrayList<>();
        Set<Long> usedBargainIds = new HashSet<>();
        Instant now = Instant.now();
        for (CheckoutItem item : request.items().stream().sorted(java.util.Comparator.comparing(CheckoutItem::productId)).toList()) {
            if (item.quantity() == null || item.quantity() < 1) throw BizException.badRequest("QUANTITY_INVALID", "购买数量必须大于0");
            Product product = productRepository.lockById(item.productId())
                    .orElseThrow(() -> BizException.notFound("商品不存在"));
            if (product.getSellerId().equals(userId)) throw BizException.badRequest("SELF_PURCHASE", "不能购买自己的商品");
            if (product.getExperienceSource() != null)
                throw BizException.conflict("EXPERIENCE_PRODUCT", "该商品使用体验库存，可浏览、收藏和交流，不生成真实付款订单");
            var eligibility = jdbc.queryForList("SELECT status,channel_status FROM seller_applications WHERE user_id=? ORDER BY created_at DESC,id DESC LIMIT 1", product.getSellerId());
            if (eligibility.isEmpty() || !"APPROVED".equals(eligibility.getFirst().get("status"))
                    || !"QUALIFIED".equals(eligibility.getFirst().get("channel_status"))) {
                throw BizException.conflict("SELLER_NOT_QUALIFIED", "卖家当前资格不允许成交");
            }
            if (product.getStatus() != Product.Status.ON_SALE) {
                throw BizException.conflict("PRODUCT_NOT_ON_SALE",
                        "商品「" + product.getTitle() + "」当前不可成交");
            }
            Order.DeliveryMethod method;
            try {
                method = Order.DeliveryMethod.valueOf(item.deliveryMethod().trim().toUpperCase());
            } catch (IllegalArgumentException e) {
                throw BizException.badRequest("INVALID_DELIVERY_METHOD", "不支持的交付方式");
            }
            if (!CartService.supports(product, method.name())) {
                throw BizException.badRequest("DELIVERY_METHOD_NOT_SUPPORTED",
                        "商品「" + product.getTitle() + "」不支持所选交付方式");
            }
            long unitPriceCents = product.getPriceCents();
            BargainOffer bargain = null;
            if (item.bargainId() != null) {
                if (!usedBargainIds.add(item.bargainId())) {
                    throw BizException.badRequest("BARGAIN_REPEATED", "同一议价单不能重复使用");
                }
                bargain = bargainOfferRepository.lockById(item.bargainId())
                        .orElseThrow(() -> BizException.notFound("议价单不存在"));
                if (!bargain.getBuyerId().equals(userId)) {
                    throw BizException.forbidden("无权使用该议价单");
                }
                if (!bargain.getProductId().equals(product.getId())) {
                    throw BizException.badRequest("BARGAIN_PRODUCT_MISMATCH", "议价单与商品不匹配");
                }
                if (bargain.getStatus() == BargainOffer.Status.USED) {
                    throw BizException.conflict("BARGAIN_ALREADY_USED", "议价单已被使用");
                }
                if ((bargain.getStatus() == BargainOffer.Status.PENDING
                        || bargain.getStatus() == BargainOffer.Status.COUNTERED
                        || bargain.getStatus() == BargainOffer.Status.CONFIRMED)
                        && now.isAfter(bargain.getExpiresAt())) {
                    bargain.setStatus(BargainOffer.Status.EXPIRED);
                    bargainOfferRepository.save(bargain);
                    throw BizException.conflict("BARGAIN_EXPIRED", "议价单已过期");
                }
                if (bargain.getStatus() != BargainOffer.Status.CONFIRMED) {
                    throw BizException.conflict("BARGAIN_NOT_CONFIRMED", "议价单尚未达成，不能用于下单");
                }
                if (bargain.getQuantity() != item.quantity()) {
                    throw BizException.badRequest("BARGAIN_QUANTITY_MISMATCH", "议价数量与下单数量不一致");
                }
                unitPriceCents = bargain.getCounterPriceCents() != null
                        ? bargain.getCounterPriceCents()
                        : bargain.getOfferPriceCents();
            }
            lines.add(new Line(product, item.quantity(), method, unitPriceCents, bargain));
        }
        return lines;
    }

    /** 分组键 = 卖家 + 交付方式 + 目的地（EXPRESS 用 addressId，MEETUP 用地点+时间）。 */
    private String groupKey(Line line, CheckoutRequest request) {
        String destination;
        if (line.method() == Order.DeliveryMethod.EXPRESS) {
            destination = "ADDR:" + request.addressId();
        } else {
            if (request.meetupLocation() == null || request.meetupLocation().isBlank()
                    || request.meetupTime() == null) {
                throw BizException.badRequest("MEETUP_INFO_REQUIRED", "面交交付必须提供面交地点与时间");
            }
            destination = "MEETUP:" + request.meetupLocation().trim() + "|" + request.meetupTime();
        }
        return line.product().getSellerId() + "|" + line.method() + "|" + destination;
    }

    private Order createGroupOrder(Long userId, CheckoutBatch batch, List<Line> group,
                                   CheckoutRequest request, Address address, Instant now) {
        Line first = group.get(0);
        long goodsAmountCents = 0;
        long freightCents = 0;
        for (Line line : group) {
            try { goodsAmountCents = Math.addExact(goodsAmountCents, Math.multiplyExact(line.unitPriceCents(), line.quantity())); }
            catch (ArithmeticException ex) { throw BizException.badRequest("AMOUNT_TOO_LARGE", "订单金额过大"); }
            if (first.method() == Order.DeliveryMethod.EXPRESS) freightCents = Math.max(freightCents, line.product().getFreightCents());
        }
        long platformFeeCents = FeeCalculator.platformFee(goodsAmountCents);

        Order order = new Order();
        order.setOrderNo(NoGenerator.next("MM"));
        order.setBatchId(batch.getId());
        order.setBuyerId(userId);
        order.setSellerId(first.product().getSellerId());
        order.setDeliveryMethod(first.method());
        if (first.method() == Order.DeliveryMethod.EXPRESS) {
            order.setReceiver(address.getReceiver());
            order.setPhone(address.getPhone());
            order.setRegion(address.getRegion());
            order.setAddressDetail(address.getDetail());
        }
        order.setGoodsAmountCents(goodsAmountCents);
        order.setFreightCents(freightCents);
        order.setPlatformFeeCents(platformFeeCents);
        try { order.setTotalCents(Math.addExact(goodsAmountCents, freightCents)); }
        catch (ArithmeticException ex) { throw BizException.badRequest("AMOUNT_TOO_LARGE", "订单金额过大"); }
        order.setFulfillmentStatus(Order.FulfillmentStatus.PENDING_PAYMENT);
        order.setPayStatus(Order.PayStatus.UNPAID);
        order.setExpiresAt(now.plus(PAY_TTL));
        group.stream().filter(l -> l.bargain() != null).findFirst()
                .ifPresent(l -> order.setBargainId(l.bargain().getId()));
        orderRepository.saveAndFlush(order);

        for (Line line : group) {
            Product product = line.product();
            int updated = productRepository.reserveStock(product.getId(), line.quantity());
            if (updated == 0) {
                throw BizException.conflict("INSUFFICIENT_STOCK",
                        "商品「" + product.getTitle() + "」库存不足");
            }
            stockLogRepository.save(stockLog(product.getId(), -line.quantity(), line.quantity(), 0,
                    "RESERVE", order.getId()));

            OrderItem orderItem = new OrderItem();
            orderItem.setOrderId(order.getId());
            orderItem.setProductId(product.getId());
            orderItem.setTitle(product.getTitle());
            orderItem.setItemCondition(product.getItemCondition().name());
            orderItem.setDefects(product.getDefects());
            orderItem.setPriceCents(line.unitPriceCents());
            orderItem.setQuantity(line.quantity());
            orderItem.setFreightCents(product.getFreightCents());
            orderItem.setReturnPromise(product.getReturnPromise());
            List<ProductImage> images = productImageRepository.findByProductIdOrderBySort(product.getId());
            orderItem.setImagePath(images.isEmpty() ? null : com.maimai.catalog.service.ProductImagePaths.publicUrl(images.get(0).getPath()));
            orderItemRepository.save(orderItem);

            if (line.bargain() != null) {
                line.bargain().setStatus(BargainOffer.Status.USED);
                line.bargain().setUsedOrderId(order.getId());
                bargainOfferRepository.save(line.bargain());
            }
        }

        if (first.method() == Order.DeliveryMethod.MEETUP) {
            MeetupAppointment appointment = new MeetupAppointment();
            appointment.setOrderId(order.getId());
            appointment.setLocation(request.meetupLocation().trim());
            appointment.setScheduledAt(request.meetupTime());
            appointment.setStatus(MeetupAppointment.Status.ARRANGED);
            meetupAppointmentRepository.save(appointment);
        }
        return order;
    }

    private StockLog stockLog(Long productId, int deltaAvailable, int deltaReserved, int deltaSold,
                              String reason, Long refId) {
        StockLog log = new StockLog();
        log.setProductId(productId);
        log.setDeltaAvailable(deltaAvailable);
        log.setDeltaReserved(deltaReserved);
        log.setDeltaSold(deltaSold);
        log.setReason(reason);
        log.setRefType("ORDER");
        log.setRefId(refId);
        return log;
    }

    /** 单个结算项解析结果。 */
    private record Line(Product product, int quantity, Order.DeliveryMethod method,
                        long unitPriceCents, BargainOffer bargain) {
    }
}
