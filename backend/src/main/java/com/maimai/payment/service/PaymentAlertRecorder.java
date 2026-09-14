package com.maimai.payment.service;

import com.maimai.payment.domain.PaymentNotification;
import com.maimai.payment.repo.PaymentNotificationRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 支付告警事件记录：独立于外层事务提交（REQUIRES_NEW），
 * 保证"金额不一致拒绝并告警"在业务异常回滚后告警仍落库。
 */
@Service
public class PaymentAlertRecorder {

    private final PaymentNotificationRepository paymentNotificationRepository;

    public PaymentAlertRecorder(PaymentNotificationRepository paymentNotificationRepository) {
        this.paymentNotificationRepository = paymentNotificationRepository;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordAmountMismatch(String channel, String payNo, long expectedCents, long actualCents) {
        String eventId = payNo + "-amount-mismatch";
        if (paymentNotificationRepository.existsByChannelAndEventId(channel, eventId)) {
            return;
        }
        PaymentNotification notification = new PaymentNotification();
        notification.setChannel(channel);
        notification.setEventId(eventId);
        notification.setPayload("{\"payNo\":\"" + payNo + "\",\"expectedCents\":" + expectedCents
                + ",\"actualCents\":" + actualCents + ",\"alert\":\"AMOUNT_MISMATCH\"}");
        notification.setProcessed(false);
        paymentNotificationRepository.save(notification);
    }
}
