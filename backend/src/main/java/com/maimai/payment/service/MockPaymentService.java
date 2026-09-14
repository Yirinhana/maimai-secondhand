package com.maimai.payment.service;

import com.maimai.common.BizException;
import com.maimai.notification.NotificationService;
import com.maimai.payment.domain.LedgerEntry;
import com.maimai.payment.domain.PaymentNotification;
import com.maimai.payment.domain.PaymentRequest;
import com.maimai.payment.dto.PaymentDtos;
import com.maimai.payment.repo.LedgerEntryRepository;
import com.maimai.payment.repo.PaymentNotificationRepository;
import com.maimai.payment.repo.PaymentRequestRepository;
import com.maimai.trade.api.TradeOrderOps;
import com.maimai.trade.domain.Order;
import com.maimai.trade.repo.OrderRepository;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

/**
 * 开发隔离环境模拟支付确认/失败（仅 local/test profile 注册）。
 * 确认幂等：重复请求不重复入账；金额不一致拒绝并告警；已关单后的迟付转人工，不直接确认订单。
 */
@Service
@Profile({"local", "test"})
@Transactional
public class MockPaymentService {

    private static final String MOCK_CHANNEL = "MOCK_LOCAL";

    private final PaymentRequestRepository paymentRequestRepository;
    private final PaymentNotificationRepository paymentNotificationRepository;
    private final LedgerEntryRepository ledgerEntryRepository;
    private final OrderRepository orderRepository;
    private final TradeOrderOps tradeOrderOps;
    private final NotificationService notificationService;
    private final PaymentAlertRecorder paymentAlertRecorder;
    private final JdbcTemplate jdbcTemplate;
    private final com.maimai.payment.finance.FinanceService finance;

    public MockPaymentService(PaymentRequestRepository paymentRequestRepository,
                              PaymentNotificationRepository paymentNotificationRepository,
                              LedgerEntryRepository ledgerEntryRepository,
                              OrderRepository orderRepository,
                              TradeOrderOps tradeOrderOps,
                              NotificationService notificationService,
                              PaymentAlertRecorder paymentAlertRecorder,
                              JdbcTemplate jdbcTemplate, com.maimai.payment.finance.FinanceService finance) {
        this.paymentRequestRepository = paymentRequestRepository;
        this.paymentNotificationRepository = paymentNotificationRepository;
        this.ledgerEntryRepository = ledgerEntryRepository;
        this.orderRepository = orderRepository;
        this.tradeOrderOps = tradeOrderOps;
        this.notificationService = notificationService;
        this.paymentAlertRecorder = paymentAlertRecorder;
        this.jdbcTemplate = jdbcTemplate;
        this.finance = finance;
    }

    public PaymentDtos.MockPayResultResponse confirm(PaymentDtos.MockPayConfirmRequest request) {
        PaymentRequest payment = paymentRequestRepository.lockByPayNo(request.payNo())
                .orElseThrow(() -> BizException.notFound("支付单不存在"));
        if (request.amountCents() != null && request.amountCents() != payment.getAmountCents()) {
            // 告警事件独立事务落库；业务状态不做任何变更
            paymentAlertRecorder.recordAmountMismatch(payment.getChannel().name(), payment.getPayNo(),
                    payment.getAmountCents(), request.amountCents());
            throw BizException.conflict("PAY_AMOUNT_MISMATCH", "金额不一致，已拒绝并记录告警");
        }
        if (payment.getStatus() == PaymentRequest.Status.PAID) {
            return new PaymentDtos.MockPayResultResponse(payment.getPayNo(), payment.getStatus().name(),
                    payment.isSimulated(), "支付单已是成功状态，幂等返回，未重复处理");
        }
        if (payment.getStatus() != PaymentRequest.Status.CREATED) {
            throw BizException.conflict("PAY_STATUS_INVALID", "支付单当前状态不可确认");
        }
        String eventId = payment.getPayNo() + "-paid";
        if (paymentNotificationRepository.existsByChannelAndEventId(MOCK_CHANNEL, eventId)) {
            return new PaymentDtos.MockPayResultResponse(payment.getPayNo(), payment.getStatus().name(),
                    payment.isSimulated(), "支付确认事件已处理过，幂等返回");
        }
        Instant paidAt = Instant.now();
        payment.setStatus(PaymentRequest.Status.PAID);
        payment.setPaidAt(paidAt);
        paymentRequestRepository.save(payment);

        PaymentNotification notification = new PaymentNotification();
        notification.setChannel(MOCK_CHANNEL);
        notification.setEventId(eventId);
        notification.setPayload("{\"payNo\":\"" + payment.getPayNo() + "\",\"amountCents\":"
                + payment.getAmountCents() + ",\"event\":\"PAID\"}");
        notification.setProcessed(true);
        paymentNotificationRepository.save(notification);

        Order order = orderRepository.lockById(payment.getOrderId())
                .orElseThrow(() -> BizException.notFound("订单不存在"));
        saveLedger(order.getId(), "PAYMENT", order.getTotalCents(), "PAYMENT_REQUEST", payment.getId());

        boolean advanced = tradeOrderOps.markPaid(order.getId(), paidAt);
        if (!advanced) {
            // 迟付：订单已关单/已处理，资金已确认但订单状态不动，转人工处理
            saveLedger(order.getId(), "LATE_PAID", order.getTotalCents(), "PAYMENT_REQUEST", payment.getId());
            for (Long adminId : superAdminIds()) {
                notificationService.notify(adminId, "LATE_PAYMENT", "迟付异常待人工处理",
                        "订单 " + order.getOrderNo() + " 支付单 " + payment.getPayNo()
                                + " 在订单非待付款状态下确认支付，请人工核实处理");
            }
            return new PaymentDtos.MockPayResultResponse(payment.getPayNo(), payment.getStatus().name(),
                    payment.isSimulated(), "订单已非待付款状态，迟付已标记并转人工处理");
        }
        jdbcTemplate.update("UPDATE orders SET channel_fee_cents=0,channel_fee_confirmed=1 WHERE id=?",order.getId());
        orderRepository.flush();
        finance.captureExpectedAllocation(order.getId());
        return new PaymentDtos.MockPayResultResponse(payment.getPayNo(), payment.getStatus().name(),
                payment.isSimulated(), "模拟支付确认成功（本地隔离环境，未发生真实资金）");
    }

    public PaymentDtos.MockPayResultResponse fail(PaymentDtos.MockPayFailRequest request) {
        PaymentRequest payment = paymentRequestRepository.lockByPayNo(request.payNo())
                .orElseThrow(() -> BizException.notFound("支付单不存在"));
        if (payment.getStatus() == PaymentRequest.Status.FAILED) {
            return new PaymentDtos.MockPayResultResponse(payment.getPayNo(), payment.getStatus().name(),
                    payment.isSimulated(), "支付单已是失败状态，幂等返回");
        }
        if (payment.getStatus() != PaymentRequest.Status.CREATED) {
            throw BizException.conflict("PAY_STATUS_INVALID", "支付单当前状态不可标记失败");
        }
        payment.setStatus(PaymentRequest.Status.FAILED);
        paymentRequestRepository.save(payment);
        Order order = orderRepository.findById(payment.getOrderId())
                .orElseThrow(() -> BizException.notFound("订单不存在"));
        notificationService.notify(order.getBuyerId(), "PAYMENT", "支付未成功",
                "订单 " + order.getOrderNo() + " 的模拟支付已标记失败，可在订单超时前重新发起支付");
        return new PaymentDtos.MockPayResultResponse(payment.getPayNo(), payment.getStatus().name(),
                payment.isSimulated(), "模拟支付已标记失败");
    }

    private void saveLedger(Long orderId, String entryType, long amountCents, String refType, Long refId) {
        LedgerEntry entry = new LedgerEntry();
        entry.setOrderId(orderId);
        entry.setEntryType(entryType);
        entry.setAmountCents(amountCents);
        entry.setRefType(refType);
        entry.setRefId(refId);
        ledgerEntryRepository.save(entry);
    }

    private List<Long> superAdminIds() {
        return jdbcTemplate.queryForList(
                "SELECT user_id FROM user_roles WHERE role = 'SUPER_ADMIN'", Long.class);
    }
}
