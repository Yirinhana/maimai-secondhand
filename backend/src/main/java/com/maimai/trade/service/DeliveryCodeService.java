package com.maimai.trade.service;

import com.maimai.common.BizException;
import com.maimai.common.security.SecurityUtils;
import com.maimai.trade.domain.DeliveryCode;
import com.maimai.trade.domain.MeetupAppointment;
import com.maimai.trade.domain.Order;
import com.maimai.trade.dto.TradeDtos.DeliveryCodeResponse;
import com.maimai.trade.repo.DeliveryCodeRepository;
import com.maimai.trade.repo.MeetupAppointmentRepository;
import com.maimai.trade.repo.OrderRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;

/** 面交一次性交付码：仅存 SHA-256 哈希，10 分钟有效，限 5 次尝试，核验即完成交付。 */
@Service
@Transactional
public class DeliveryCodeService {

    private static final Duration CODE_TTL = Duration.ofMinutes(10);
    private static final int MAX_ATTEMPTS = 5;
    private static final SecureRandom RANDOM = new SecureRandom();

    private final OrderRepository orderRepository;
    private final DeliveryCodeRepository deliveryCodeRepository;
    private final MeetupAppointmentRepository meetupAppointmentRepository;

    public DeliveryCodeService(OrderRepository orderRepository,
                               DeliveryCodeRepository deliveryCodeRepository,
                               MeetupAppointmentRepository meetupAppointmentRepository) {
        this.orderRepository = orderRepository;
        this.deliveryCodeRepository = deliveryCodeRepository;
        this.meetupAppointmentRepository = meetupAppointmentRepository;
    }

    /** 买家生成交付码：作废旧码，响应仅本次含明文。 */
    public DeliveryCodeResponse generate(Long buyerId, String orderNo) {
        Order order = requireOrder(orderNo);
        SecurityUtils.requireOwner(order.getBuyerId());
        if (order.getDeliveryMethod() != Order.DeliveryMethod.MEETUP
                || order.getFulfillmentStatus() != Order.FulfillmentStatus.AWAITING_MEETUP
                || order.getPayStatus() != Order.PayStatus.PAID || order.isConfirmPaused()
                || order.getRefundStatus() == Order.RefundStatus.FULL) {
            throw BizException.conflict("ORDER_STATE", "订单当前状态不能生成交付码");
        }
        deliveryCodeRepository.findTopByOrderIdAndInvalidatedFalseOrderByCreatedAtDesc(order.getId())
                .ifPresent(old -> {
                    old.setInvalidated(true);
                    deliveryCodeRepository.save(old);
                });
        String code = String.format("%06d", RANDOM.nextInt(1_000_000));
        Instant expiresAt = Instant.now().plus(CODE_TTL);
        DeliveryCode deliveryCode = new DeliveryCode();
        deliveryCode.setOrderId(order.getId());
        deliveryCode.setCodeHash(sha256Hex(code));
        deliveryCode.setExpiresAt(expiresAt);
        deliveryCodeRepository.save(deliveryCode);
        return new DeliveryCodeResponse(code, expiresAt);
    }

    /** 卖家核验：悲观锁防并发，仅一次成功；不匹配累计尝试次数并锁定。 */
    @Transactional(noRollbackFor = BizException.class)
    public void verify(Long sellerId, String orderNo, String code) {
        Order order = requireOrder(orderNo);
        SecurityUtils.requireOwner(order.getSellerId());
        Order locked = order;
        if (locked.getFulfillmentStatus() != Order.FulfillmentStatus.AWAITING_MEETUP
                || locked.getDeliveryMethod() != Order.DeliveryMethod.MEETUP
                || locked.getPayStatus() != Order.PayStatus.PAID || locked.isConfirmPaused()
                || locked.getRefundStatus() == Order.RefundStatus.FULL) {
            throw BizException.conflict("ORDER_STATE", "订单当前状态不可核验交付码");
        }
        DeliveryCode deliveryCode = deliveryCodeRepository
                .findTopByOrderIdAndInvalidatedFalseOrderByCreatedAtDesc(locked.getId())
                .orElseThrow(() -> BizException.badRequest("DELIVERY_CODE_NOT_FOUND", "请买家先生成交付码"));
        if (deliveryCode.getVerifiedAt() != null) {
            throw BizException.conflict("DELIVERY_CODE_ALREADY_VERIFIED", "交付码已核验");
        }
        Instant now = Instant.now();
        if (now.isAfter(deliveryCode.getExpiresAt())) {
            throw BizException.badRequest("DELIVERY_CODE_EXPIRED", "交付码已过期，请买家重新生成");
        }
        if (deliveryCode.getAttempts() >= MAX_ATTEMPTS) {
            throw BizException.conflict("DELIVERY_CODE_LOCKED", "尝试次数过多，交付码已锁定，请买家重新生成");
        }
        if (!MessageDigest.isEqual(deliveryCode.getCodeHash().getBytes(StandardCharsets.UTF_8),
                sha256Hex(code).getBytes(StandardCharsets.UTF_8))) {
            deliveryCode.setAttempts(deliveryCode.getAttempts() + 1);
            deliveryCodeRepository.save(deliveryCode);
            int left = MAX_ATTEMPTS - deliveryCode.getAttempts();
            throw BizException.badRequest("DELIVERY_CODE_MISMATCH",
                    "交付码不正确，剩余 " + left + " 次尝试机会");
        }
        deliveryCode.setVerifiedAt(now);
        deliveryCodeRepository.save(deliveryCode);
        locked.setFulfillmentStatus(Order.FulfillmentStatus.COMPLETED);
        locked.setCompletedAt(now);
        orderRepository.save(locked);
        meetupAppointmentRepository.findTopByOrderIdOrderByCreatedAtDesc(locked.getId())
                .ifPresent(appointment -> {
                    appointment.setStatus(MeetupAppointment.Status.COMPLETED);
                    meetupAppointmentRepository.save(appointment);
                });
    }

    private Order requireOrder(String orderNo) {
        return orderRepository.lockByOrderNo(orderNo)
                .orElseThrow(() -> BizException.notFound("订单不存在"));
    }

    private static String sha256Hex(String code) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(code.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }
}
