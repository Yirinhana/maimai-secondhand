package com.maimai.trade.service;

import com.maimai.catalog.domain.StockLog;
import com.maimai.catalog.repo.ProductRepository;
import com.maimai.catalog.repo.StockLogRepository;
import com.maimai.common.BizException;
import com.maimai.notification.NotificationService;
import com.maimai.trade.api.TradeOrderOps;
import com.maimai.trade.domain.Order;
import com.maimai.trade.domain.OrderItem;
import com.maimai.trade.repo.OrderItemRepository;
import com.maimai.trade.repo.OrderRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

/** 交易模块对外操作实现：供 payment/aftersales 在各自事务内调用，状态推进与库存变化原子一致。 */
@Service
@Transactional
public class TradeOrderOpsImpl implements TradeOrderOps {

    private static final Logger log = LoggerFactory.getLogger(TradeOrderOpsImpl.class);
    private static final Duration SHIP_DEADLINE = Duration.ofHours(72);

    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;
    private final ProductRepository productRepository;
    private final StockLogRepository stockLogRepository;
    private final NotificationService notificationService;

    public TradeOrderOpsImpl(OrderRepository orderRepository,
                             OrderItemRepository orderItemRepository,
                             ProductRepository productRepository,
                             StockLogRepository stockLogRepository,
                             NotificationService notificationService) {
        this.orderRepository = orderRepository;
        this.orderItemRepository = orderItemRepository;
        this.productRepository = productRepository;
        this.stockLogRepository = stockLogRepository;
        this.notificationService = notificationService;
    }

    @Override
    public boolean markPaid(Long orderId, Instant paidAt) {
        Order order = lock(orderId);
        if (order.getFulfillmentStatus() != Order.FulfillmentStatus.PENDING_PAYMENT
                || order.getPayStatus() == Order.PayStatus.PAID) {
            return false;
        }
        order.setPayStatus(Order.PayStatus.PAID);
        order.setPaidAt(paidAt);
        order.setShipDeadline(paidAt.plus(SHIP_DEADLINE));
        order.setFulfillmentStatus(order.getDeliveryMethod() == Order.DeliveryMethod.EXPRESS
                ? Order.FulfillmentStatus.PAID_PENDING_SHIP
                : Order.FulfillmentStatus.AWAITING_MEETUP);

        for (OrderItem item : orderItemRepository.findByOrderId(order.getId())) {
            int updated = productRepository.consumeReserved(item.getProductId(), item.getQuantity());
            if (updated == 0) {
                throw BizException.conflict("STOCK_RESERVATION_INVALID", "订单预留库存异常，需要人工核查");
            }
            stockLogRepository.save(stockLog(item.getProductId(), 0, -item.getQuantity(),
                    item.getQuantity(), "CONSUME", order.getId()));
        }
        orderRepository.save(order);

        boolean express = order.getDeliveryMethod() == Order.DeliveryMethod.EXPRESS;
        notificationService.notify(order.getSellerId(), "ORDER",
                express ? "新订单待发货" : "新订单待面交",
                "订单 " + order.getOrderNo() + " 已支付，"
                        + (express ? "请在72小时内发货" : "请与买家确认面交约定"));
        return true;
    }

    @Override
    public void closeUnpaid(Long orderId, String reason) {
        Order order = lock(orderId);
        if (order.getFulfillmentStatus() == Order.FulfillmentStatus.CLOSED) {
            return;
        }
        if (order.getFulfillmentStatus() != Order.FulfillmentStatus.PENDING_PAYMENT) {
            throw BizException.conflict("ORDER_STATE", "订单当前状态不可关闭");
        }
        order.setFulfillmentStatus(Order.FulfillmentStatus.CLOSED);
        order.setPayStatus(Order.PayStatus.CLOSED);
        order.setClosedAt(Instant.now());
        order.setCloseReason(reason);

        for (OrderItem item : orderItemRepository.findByOrderId(order.getId())) {
            int updated = productRepository.releaseStock(item.getProductId(), item.getQuantity());
            if (updated == 0) {
                throw BizException.conflict("STOCK_RESERVATION_INVALID", "订单预留库存异常，需要人工核查");
            }
            stockLogRepository.save(stockLog(item.getProductId(), item.getQuantity(),
                    -item.getQuantity(), 0, "RELEASE", order.getId()));
        }
        orderRepository.save(order);
    }

    @Override
    public void pauseAutoConfirm(Long orderId, String reason) {
        Order order = lock(orderId);
        order.setConfirmPaused(true);
        orderRepository.save(order);
        log.info("auto-confirm paused, orderId={} reason={}", orderId, reason);
    }

    @Override
    public void resumeAutoConfirm(Long orderId) {
        Order order = lock(orderId);
        order.setConfirmPaused(false);
        orderRepository.save(order);
    }

    private Order lock(Long orderId) {
        return orderRepository.lockById(orderId)
                .orElseThrow(() -> BizException.notFound("订单不存在"));
    }

    private StockLog stockLog(Long productId, int deltaAvailable, int deltaReserved, int deltaSold,
                              String reason, Long refId) {
        StockLog stockLog = new StockLog();
        stockLog.setProductId(productId);
        stockLog.setDeltaAvailable(deltaAvailable);
        stockLog.setDeltaReserved(deltaReserved);
        stockLog.setDeltaSold(deltaSold);
        stockLog.setReason(reason);
        stockLog.setRefType("ORDER");
        stockLog.setRefId(refId);
        return stockLog;
    }
}
