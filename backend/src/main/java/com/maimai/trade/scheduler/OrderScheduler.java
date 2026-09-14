package com.maimai.trade.scheduler;

import com.maimai.trade.api.TradeOrderOps;
import com.maimai.trade.domain.Order;
import com.maimai.trade.repo.OrderRepository;
import com.maimai.trade.service.OrderMaintenanceService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;

/** 订单时限调度：30分钟未付款自动关单释放库存；发货满10天自动确认收货（暂停的跳过）。 */
@Component
@org.springframework.context.annotation.Profile("!test")
public class OrderScheduler {

    private static final Logger log = LoggerFactory.getLogger(OrderScheduler.class);

    private final OrderRepository orderRepository;
    private final TradeOrderOps tradeOrderOps;
    private final OrderMaintenanceService orderMaintenanceService;

    public OrderScheduler(OrderRepository orderRepository,
                          TradeOrderOps tradeOrderOps,
                          OrderMaintenanceService orderMaintenanceService) {
        this.orderRepository = orderRepository;
        this.tradeOrderOps = tradeOrderOps;
        this.orderMaintenanceService = orderMaintenanceService;
    }

    @Scheduled(fixedDelay = 60_000)
    public void closeExpiredUnpaid() {
        List<Order> expired = orderRepository.findByFulfillmentStatusAndExpiresAtBefore(
                Order.FulfillmentStatus.PENDING_PAYMENT, Instant.now());
        for (Order order : expired) {
            try {
                tradeOrderOps.closeUnpaid(order.getId(), "30分钟未付款自动关闭");
            } catch (Exception e) {
                log.warn("auto close unpaid order failed, orderId={}", order.getId(), e);
            }
        }
    }

    @Scheduled(fixedDelay = 60_000)
    public void autoConfirmShipped() {
        List<Order> due = orderRepository
                .findByFulfillmentStatusAndAutoConfirmAtBeforeAndConfirmPausedFalse(
                        Order.FulfillmentStatus.SHIPPED, Instant.now());
        for (Order order : due) {
            try {
                orderMaintenanceService.autoComplete(order.getId());
            } catch (Exception e) {
                log.warn("auto confirm order failed, orderId={}", order.getId(), e);
            }
        }
    }
}
