package com.maimai.trade.repo;

import com.maimai.trade.domain.CheckoutBatch;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface CheckoutBatchRepository extends JpaRepository<CheckoutBatch, Long> {

    Optional<CheckoutBatch> findByUserIdAndIdempotencyKey(Long userId, String idempotencyKey);

    Optional<CheckoutBatch> findByBatchNo(String batchNo);
}
