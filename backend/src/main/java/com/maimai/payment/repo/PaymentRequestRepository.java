package com.maimai.payment.repo;

import com.maimai.payment.domain.PaymentRequest;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface PaymentRequestRepository extends JpaRepository<PaymentRequest, Long> {

    Optional<PaymentRequest> findByPayNo(String payNo);

    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query("select p from PaymentRequest p where p.payNo=:payNo")
    Optional<PaymentRequest> lockByPayNo(@org.springframework.data.repository.query.Param("payNo") String payNo);

    Optional<PaymentRequest> findTopByOrderIdOrderByCreatedAtDesc(Long orderId);
}
