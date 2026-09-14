package com.maimai.payment.repo;

import com.maimai.payment.domain.Refund;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface RefundRepository extends JpaRepository<Refund, Long> {

    List<Refund> findByOrderId(Long orderId);

    Optional<Refund> findByRefundNo(String refundNo);
}
