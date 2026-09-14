package com.maimai.trade.repo;

import com.maimai.trade.domain.Order;
import com.maimai.trade.domain.Order.FulfillmentStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface OrderRepository extends JpaRepository<Order, Long> {

    Optional<Order> findByOrderNo(String orderNo);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select o from Order o where o.orderNo = :orderNo")
    Optional<Order> lockByOrderNo(@Param("orderNo") String orderNo);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select o from Order o where o.id = (select a.orderId from Aftersale a where a.id=:aftersaleId)")
    Optional<Order> lockByAftersaleId(@Param("aftersaleId") Long aftersaleId);

    Page<Order> findByBuyerIdOrderByCreatedAtDesc(Long buyerId, Pageable pageable);

    Page<Order> findBySellerIdOrderByCreatedAtDesc(Long sellerId, Pageable pageable);

    List<Order> findByFulfillmentStatusAndExpiresAtBefore(FulfillmentStatus fulfillmentStatus, Instant expiresAt);

    List<Order> findByFulfillmentStatusAndAutoConfirmAtBeforeAndConfirmPausedFalse(FulfillmentStatus fulfillmentStatus, Instant autoConfirmAt);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select o from Order o where o.id = :id")
    Optional<Order> lockById(@Param("id") Long id);
}
