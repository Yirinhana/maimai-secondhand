package com.maimai.trade.service;

import com.maimai.common.BizException;
import com.maimai.common.security.SecurityUtils;
import com.maimai.identity.domain.UserRole;
import com.maimai.identity.domain.UserRole.Role;
import com.maimai.notification.NotificationService;
import com.maimai.trade.domain.MeetupAppointment;
import com.maimai.trade.domain.Order;
import com.maimai.trade.domain.Shipment;
import com.maimai.trade.dto.TradeDtos.ShipmentDto;
import com.maimai.trade.logistics.LogisticsTrackingCache;
import com.maimai.trade.repo.MeetupAppointmentRepository;
import com.maimai.trade.repo.OrderRepository;
import com.maimai.trade.repo.ShipmentRepository;
import com.maimai.trade.repo.UserRoleQueryRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

/** 订单履约：快递发货、物流轨迹、确认收货、面交约定维护。 */
@Service
@Transactional
public class FulfillmentService {

    private static final Logger log = LoggerFactory.getLogger(FulfillmentService.class);
    private static final Duration AUTO_CONFIRM_AFTER_SHIP = Duration.ofDays(10);

    private final OrderRepository orderRepository;
    private final ShipmentRepository shipmentRepository;
    private final MeetupAppointmentRepository meetupAppointmentRepository;
    private final UserRoleQueryRepository userRoleQueryRepository;
    private final LogisticsTrackingCache logisticsCache;
    private final JdbcTemplate jdbc;
    private final NotificationService notificationService;

    public FulfillmentService(OrderRepository orderRepository,
                              ShipmentRepository shipmentRepository,
                              MeetupAppointmentRepository meetupAppointmentRepository,
                              UserRoleQueryRepository userRoleQueryRepository,
                              LogisticsTrackingCache logisticsCache,
                              JdbcTemplate jdbc,
                              NotificationService notificationService) {
        this.orderRepository = orderRepository;
        this.shipmentRepository = shipmentRepository;
        this.meetupAppointmentRepository = meetupAppointmentRepository;
        this.userRoleQueryRepository = userRoleQueryRepository;
        this.logisticsCache = logisticsCache;
        this.jdbc = jdbc;
        this.notificationService = notificationService;
    }

    public ShipmentDto ship(Long sellerId, String orderNo, String carrier, String trackingNo) {
        Order order = lockedOrder(orderNo);
        SecurityUtils.requireOwner(order.getSellerId());
        if (!order.getSellerId().equals(sellerId) || order.getDeliveryMethod() != Order.DeliveryMethod.EXPRESS
                || order.getFulfillmentStatus() != Order.FulfillmentStatus.PAID_PENDING_SHIP
                || order.isConfirmPaused() || order.getRefundStatus() == Order.RefundStatus.FULL) {
            throw BizException.conflict("ORDER_STATE", "订单当前状态不可发货");
        }
        carrier = LogisticsTrackingCache.normalizeCarrier(carrier);
        trackingNo = LogisticsTrackingCache.validateNumber(trackingNo);
        Instant now = Instant.now();
        order.setFulfillmentStatus(Order.FulfillmentStatus.SHIPPED);
        order.setShippedAt(now);
        order.setAutoConfirmAt(now.plus(AUTO_CONFIRM_AFTER_SHIP));
        orderRepository.save(order);

        Shipment shipment = new Shipment();
        shipment.setOrderId(order.getId());
        shipment.setCarrier(carrier);
        shipment.setTrackingNo(trackingNo);
        shipment.setStatus(Shipment.Status.UNKNOWN);
        shipmentRepository.save(shipment);

        if (order.getShipDeadline() != null && now.isAfter(order.getShipDeadline())) {
            notifySuperAdmins(order);
        }
        notificationService.notify(order.getBuyerId(), "ORDER", order.isInteractiveExperience() ? "体验发货已登记" : "订单已发货",
                order.isInteractiveExperience() ? "订单 " + orderNo + " 已登记体验发货，未安排实际寄送。"
                        : "订单 " + orderNo + " 已发货，承运商 " + carrier);
        return toDto(shipment);
    }

    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public ShipmentDto shipment(Long viewerId, String orderNo) {
        Order order = requireOrder(orderNo);
        requireVisible(order, viewerId);
        Shipment shipment = shipmentRepository.findByOrderId(order.getId())
                .orElseThrow(() -> BizException.notFound("运单不存在"));
        if (order.isInteractiveExperience()) return new ShipmentDto(shipment.getCarrier(), shipment.getTrackingNo(),
                shipment.getStatus().name(), null, null, "EXPERIENCE_NO_TRACKING", null);
        var trace = logisticsCache.query(shipment.getCarrier(), shipment.getTrackingNo(), order.getPhone());
        if (trace.fetchedAt() != null) {
            Shipment.Status status;
            try { status = Shipment.Status.valueOf(trace.status()); }
            catch (IllegalArgumentException | NullPointerException ignored) { status = Shipment.Status.UNKNOWN; }
            // 已在缓存中保存成功快照；只更新比当前记录新的轨迹，避免并发旧响应覆盖新状态。
            jdbc.update("""
                    UPDATE shipments SET status=?,traces=?,last_trace_at=?,updated_at=CURRENT_TIMESTAMP(6)
                    WHERE id=? AND (last_trace_at IS NULL OR last_trace_at<=?)
                    """, status.name(), trace.traces(), java.sql.Timestamp.from(trace.fetchedAt()),
                    shipment.getId(), java.sql.Timestamp.from(trace.fetchedAt()));
            shipment = shipmentRepository.findById(shipment.getId()).orElseThrow();
        }
        return new ShipmentDto(shipment.getCarrier(), shipment.getTrackingNo(), shipment.getStatus().name(),
                shipment.getTraces(), shipment.getLastTraceAt(), trace.errorCode(), trace.attemptedAt());
    }

    public void confirmReceipt(Long buyerId, String orderNo) {
        Order order = lockedOrder(orderNo);
        SecurityUtils.requireOwner(order.getBuyerId());
        if (!order.getBuyerId().equals(buyerId) || order.getDeliveryMethod() != Order.DeliveryMethod.EXPRESS
                || order.getFulfillmentStatus() != Order.FulfillmentStatus.SHIPPED
                || order.isConfirmPaused() || order.getRefundStatus() == Order.RefundStatus.FULL) {
            throw BizException.conflict("ORDER_STATE", "订单当前状态不可确认收货");
        }
        order.setFulfillmentStatus(Order.FulfillmentStatus.COMPLETED);
        order.setCompletedAt(Instant.now());
        orderRepository.save(order);
    }

    public void arrangeMeetup(Long sellerId, String orderNo, String location, Instant scheduledAt) {
        Order order = lockedOrder(orderNo);
        SecurityUtils.requireOwner(order.getSellerId());
        if (order.getDeliveryMethod() != Order.DeliveryMethod.MEETUP
                || order.getFulfillmentStatus() != Order.FulfillmentStatus.AWAITING_MEETUP) {
            throw BizException.conflict("ORDER_STATE", "订单当前状态不可维护面交约定");
        }
        if (!order.getSellerId().equals(sellerId) || location == null || location.isBlank()
                || scheduledAt == null || !scheduledAt.isAfter(Instant.now())) {
            throw BizException.badRequest("MEETUP_INVALID", "请填写面交地点并选择将来的时间");
        }
        MeetupAppointment appointment = meetupAppointmentRepository
                .findTopByOrderIdOrderByCreatedAtDesc(order.getId())
                .orElseGet(MeetupAppointment::new);
        appointment.setOrderId(order.getId());
        appointment.setLocation(location);
        appointment.setScheduledAt(scheduledAt);
        appointment.setStatus(MeetupAppointment.Status.ARRANGED);
        meetupAppointmentRepository.save(appointment);
        notificationService.notify(order.getBuyerId(), "ORDER", "面交约定已更新",
                "订单 " + orderNo + " 的面交约定已更新，请查看地点与时间");
    }

    public Order requireOrder(String orderNo) {
        return orderRepository.findByOrderNo(orderNo)
                .orElseThrow(() -> BizException.notFound("订单不存在"));
    }

    private Order lockedOrder(String orderNo) {
        return orderRepository.lockByOrderNo(orderNo).orElseThrow(() -> BizException.notFound("订单不存在"));
    }

    /** 买家/卖家本人或后台角色可见，否则按 404 处理（不泄露订单存在性）。 */
    public void requireVisible(Order order, Long viewerId) {
        boolean visible = order.getBuyerId().equals(viewerId)
                || order.getSellerId().equals(viewerId)
                || SecurityUtils.current().isAdmin();
        if (!visible) {
            throw BizException.notFound("订单不存在");
        }
    }

    private void notifySuperAdmins(Order order) {
        List<UserRole> admins = userRoleQueryRepository.findByRole(Role.SUPER_ADMIN);
        for (UserRole admin : admins) {
            notificationService.notify(admin.getUserId(), "ORDER", "发货超时人工提醒",
                    "订单 " + order.getOrderNo() + " 超过72小时发货时限才发货，请进入人工队列关注");
        }
    }

    private ShipmentDto toDto(Shipment shipment) {
        return new ShipmentDto(shipment.getCarrier(), shipment.getTrackingNo(),
                shipment.getStatus().name(), shipment.getTraces(), shipment.getLastTraceAt(), null, null);
    }
}
