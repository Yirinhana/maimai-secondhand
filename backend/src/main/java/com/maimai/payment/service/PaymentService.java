package com.maimai.payment.service;

import com.maimai.common.BizException;
import com.maimai.common.NoGenerator;
import com.maimai.common.security.SecurityUtils;
import com.maimai.config.MaimaiProperties;
import com.maimai.payment.domain.PaymentRequest;
import com.maimai.payment.dto.PaymentDtos;
import com.maimai.payment.repo.PaymentRequestRepository;
import com.maimai.trade.domain.Order;
import com.maimai.trade.repo.OrderRepository;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Optional;

/** 支付发起与状态查询。模拟渠道仅限 local/test 隔离环境；真实渠道未开通时一律拒绝，绝不假成功。 */
@Service
@Transactional
public class PaymentService {

    static final String MOCK_MESSAGE = "模拟支付（本地隔离环境，未发生真实资金）";

    private final OrderRepository orderRepository;
    private final PaymentRequestRepository paymentRequestRepository;
    private final MaimaiProperties properties;
    private final WechatPaymentChannel wechatPaymentChannel;
    private final Environment environment;

    public PaymentService(OrderRepository orderRepository,
                          PaymentRequestRepository paymentRequestRepository,
                          MaimaiProperties properties,
                          WechatPaymentChannel wechatPaymentChannel,
                          Environment environment) {
        this.orderRepository = orderRepository;
        this.paymentRequestRepository = paymentRequestRepository;
        this.properties = properties;
        this.wechatPaymentChannel = wechatPaymentChannel;
        this.environment = environment;
    }

    /** 买家本人对未过期待付款订单发起支付；已有 CREATED 支付单直接复用（幂等）。 */
    public PaymentDtos.PayResponse pay(String orderNo) {
        Order order = orderRepository.lockByOrderNo(orderNo)
                .orElseThrow(() -> BizException.notFound("订单不存在"));
        SecurityUtils.requireOwner(order.getBuyerId());
        if (order.getFulfillmentStatus() != Order.FulfillmentStatus.PENDING_PAYMENT
                || order.getPayStatus() != Order.PayStatus.UNPAID) {
            throw BizException.conflict("ORDER_NOT_PAYABLE", "订单当前状态不可支付");
        }
        if (!order.getExpiresAt().isAfter(Instant.now())) {
            throw BizException.conflict("ORDER_EXPIRED", "订单已超过支付时限，不可支付");
        }
        // Check the current channel before reusing a payment from a previous local environment.
        String channel = properties.getPayment().getChannel();
        if ("mock".equals(channel)) {
            requireMockEnvironment();
        } else {
            if ("wechat".equals(channel)) wechatPaymentChannel.ensureReady();
            throw new BizException("PAYMENT_NOT_CONFIGURED", "支付渠道配置无效", HttpStatus.SERVICE_UNAVAILABLE);
        }
        Optional<PaymentRequest> existing = paymentRequestRepository
                .findTopByOrderIdOrderByCreatedAtDesc(order.getId())
                .filter(p -> p.getStatus() == PaymentRequest.Status.CREATED);
        if (existing.isPresent()) {
            if (existing.get().getChannel() != PaymentRequest.Channel.MOCK_LOCAL || !existing.get().isSimulated()) {
                throw new BizException("PAYMENT_NOT_CONFIGURED", "历史支付单渠道与当前配置不一致", HttpStatus.SERVICE_UNAVAILABLE);
            }
            return toPayResponse(existing.get());
        }
        return createMockPayment(order);
    }

    /** 查询订单最新支付单状态（买卖双方与后台可见）。 */
    @Transactional(readOnly = true)
    public PaymentDtos.PaymentStatusResponse latestStatus(String orderNo) {
        Order order = orderRepository.findByOrderNo(orderNo)
                .orElseThrow(() -> BizException.notFound("订单不存在"));
        Long currentUserId = SecurityUtils.currentUserId();
        if (!order.getBuyerId().equals(currentUserId) && !order.getSellerId().equals(currentUserId)
                && !SecurityUtils.current().isAdmin()) {
            throw BizException.forbidden("无权查看该订单支付信息");
        }
        PaymentRequest payment = paymentRequestRepository
                .findTopByOrderIdOrderByCreatedAtDesc(order.getId())
                .orElseThrow(() -> BizException.notFound("该订单尚未发起支付"));
        return new PaymentDtos.PaymentStatusResponse(payment.getPayNo(), payment.getStatus().name(),
                payment.isSimulated(), payment.getPaidAt());
    }

    private PaymentDtos.PayResponse createMockPayment(Order order) {
        requireMockEnvironment();
        PaymentRequest payment = new PaymentRequest();
        payment.setPayNo(NoGenerator.next("MP"));
        payment.setOrderId(order.getId());
        payment.setAmountCents(order.getTotalCents());
        payment.setChannel(PaymentRequest.Channel.MOCK_LOCAL);
        payment.setSimulated(true);
        paymentRequestRepository.save(payment);
        return toPayResponse(payment);
    }

    private void requireMockEnvironment() {
        if (!environment.acceptsProfiles(Profiles.of("local", "test"))) {
            throw new BizException("PAYMENT_NOT_CONFIGURED", "模拟支付渠道仅限本地/测试隔离环境",
                    HttpStatus.SERVICE_UNAVAILABLE);
        }
    }

    private PaymentDtos.PayResponse toPayResponse(PaymentRequest payment) {
        return new PaymentDtos.PayResponse(payment.getPayNo(), payment.getChannel().name(),
                payment.getAmountCents(), payment.isSimulated(), MOCK_MESSAGE);
    }
}
