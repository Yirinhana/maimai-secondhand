package com.maimai.trade.repo;

import com.maimai.trade.domain.DeliveryCode;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface DeliveryCodeRepository extends JpaRepository<DeliveryCode, Long> {

    Optional<DeliveryCode> findTopByOrderIdAndInvalidatedFalseOrderByCreatedAtDesc(Long orderId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select d from DeliveryCode d where d.id = :id")
    Optional<DeliveryCode> lockById(@Param("id") Long id);
}
