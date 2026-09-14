package com.maimai.trade.service;

import com.maimai.identity.repo.UserRepository;
import com.maimai.trade.domain.MeetupAppointment;
import com.maimai.trade.domain.Order;
import com.maimai.trade.domain.OrderItem;
import com.maimai.trade.dto.TradeDtos.OrderDto;
import com.maimai.trade.dto.TradeDtos.OrderItemDto;
import com.maimai.trade.repo.MeetupAppointmentRepository;
import com.maimai.trade.repo.OrderItemRepository;
import com.maimai.identity.domain.User;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;

/** Order 聚合（订单 + 明细快照 + 面交约定 + 卖家昵称）→ 契约 OrderDto。 */
@Component
public class OrderDtoMapper {

    private final OrderItemRepository orderItemRepository;
    private final MeetupAppointmentRepository meetupAppointmentRepository;
    private final UserRepository userRepository;
    private final com.maimai.payment.finance.FinanceService finance;

    public OrderDtoMapper(OrderItemRepository orderItemRepository,
                          MeetupAppointmentRepository meetupAppointmentRepository,
                          UserRepository userRepository, com.maimai.payment.finance.FinanceService finance) {
        this.orderItemRepository = orderItemRepository;
        this.meetupAppointmentRepository = meetupAppointmentRepository;
        this.userRepository = userRepository;
        this.finance = finance;
    }

    public OrderDto toDto(Order order) {
        List<OrderItemDto> items = orderItemRepository.findByOrderId(order.getId()).stream()
                .map(this::toItemDto)
                .toList();
        String sellerNickname = userRepository.findById(order.getSellerId())
                .map(User::getNickname)
                .orElse(null);
        String meetupLocation = null;
        Instant meetupTime = null;
        if (order.getDeliveryMethod() == Order.DeliveryMethod.MEETUP) {
            MeetupAppointment appointment = meetupAppointmentRepository
                    .findTopByOrderIdOrderByCreatedAtDesc(order.getId())
                    .orElse(null);
            if (appointment != null) {
                meetupLocation = appointment.getLocation();
                meetupTime = appointment.getScheduledAt();
            }
        }
        var money = finance.money(order.getId());
        return new OrderDto(
                order.getId(), order.getOrderNo(), order.getBuyerId(), order.getSellerId(), sellerNickname,
                order.getDeliveryMethod(), items,
                order.getGoodsAmountCents(), order.getFreightCents(),
                order.getPlatformFeeCents(), order.getTotalCents(),
                order.getFulfillmentStatus(), order.getPayStatus(),
                order.getRefundStatus(), order.getSettleStatus(),
                order.getReceiver(), order.getPhone(), order.getRegion(), order.getAddressDetail(),
                meetupLocation, meetupTime,
                order.getExpiresAt(), order.getPaidAt(), order.getShippedAt(), order.getAutoConfirmAt(),
                order.getCreatedAt(), money.retainedPlatformFeeCents(), money.channelFeeCents(), money.channelFeeConfirmed(),
                money.expectedSellerNetCents(), money.simulated(), money.allocationStatus());
    }

    private OrderItemDto toItemDto(OrderItem item) {
        return new OrderItemDto(item.getProductId(), item.getTitle(), item.getItemCondition(),
                item.getDefects(), item.getPriceCents(), item.getQuantity(), com.maimai.catalog.service.ProductImagePaths.publicUrl(item.getImagePath()));
    }
}
