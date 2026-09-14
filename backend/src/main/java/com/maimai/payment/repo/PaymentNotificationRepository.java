package com.maimai.payment.repo;

import com.maimai.payment.domain.PaymentNotification;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PaymentNotificationRepository extends JpaRepository<PaymentNotification, Long> {

    boolean existsByChannelAndEventId(String channel, String eventId);
}
