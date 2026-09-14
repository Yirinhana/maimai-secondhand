package com.maimai.trade.service;

import com.maimai.trade.domain.Order;
import com.maimai.trade.repo.OrderRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

/** 调度器逐单事务处理：单条失败仅回滚本单，不影响同批其他订单。 */
@Service
public class OrderMaintenanceService {

    private final OrderRepository orderRepository;
    private final com.maimai.trade.repo.ShipmentRepository shipments;
    private final com.maimai.aftersales.repo.AftersaleRepository aftersales;
    private final TradeReminderService reminders;

    public OrderMaintenanceService(OrderRepository orderRepository,
            com.maimai.trade.repo.ShipmentRepository shipments,
            com.maimai.aftersales.repo.AftersaleRepository aftersales,
            TradeReminderService reminders) {
        this.orderRepository = orderRepository;
        this.shipments = shipments;
        this.aftersales = aftersales;
        this.reminders = reminders;
    }

    /** 自动确认收货：锁内复核状态，confirmPaused 或已推进的订单跳过。 */
    @Transactional
    public void autoComplete(Long orderId) {
        Order order = orderRepository.lockById(orderId).orElse(null);
        if (order == null) {
            return;
        }
        Instant now = Instant.now();
        if (order.getFulfillmentStatus() == Order.FulfillmentStatus.SHIPPED
                && order.getDeliveryMethod() == Order.DeliveryMethod.EXPRESS
                && order.getPayStatus() == Order.PayStatus.PAID
                && order.getRefundStatus() != Order.RefundStatus.FULL
                && !order.isConfirmPaused()
                && order.getAutoConfirmAt() != null
                && !now.isBefore(order.getAutoConfirmAt())) {
            if (aftersales.existsByOrderIdAndStatusIn(orderId, java.util.List.of(
                    com.maimai.aftersales.domain.Aftersale.Status.PENDING_SELLER,
                    com.maimai.aftersales.domain.Aftersale.Status.PENDING_RETURN,
                    com.maimai.aftersales.domain.Aftersale.Status.RETURN_SHIPPED,
                    com.maimai.aftersales.domain.Aftersale.Status.PENDING_MANUAL))) return;
            var shipment = shipments.findByOrderId(orderId).orElse(null);
            if (shipment == null || shipment.getStatus() != com.maimai.trade.domain.Shipment.Status.DELIVERED) {
                reminders.deferReceipt(order);
                return;
            }
            order.setFulfillmentStatus(Order.FulfillmentStatus.COMPLETED);
            order.setCompletedAt(now);
            orderRepository.save(order);
            reminders.resolveReceiptReview(orderId);
        }
    }
}
