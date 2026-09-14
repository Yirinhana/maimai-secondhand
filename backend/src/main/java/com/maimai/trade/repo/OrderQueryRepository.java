package com.maimai.trade.repo;

import com.maimai.trade.domain.Order;
import com.maimai.trade.domain.Order.FulfillmentStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/**
 * Order 的补充查询（OrderRepository 为既有文件，本模块不改动，新增派生查询放这里）。
 * Spring Data JPA 允许同一实体对应多个 Repository 接口。
 */
public interface OrderQueryRepository extends JpaRepository<Order, Long> {

    List<Order> findByBatchIdOrderById(Long batchId);

    Page<Order> findByBuyerIdAndFulfillmentStatusOrderByCreatedAtDesc(Long buyerId, FulfillmentStatus status, Pageable pageable);

    Page<Order> findBySellerIdAndFulfillmentStatusOrderByCreatedAtDesc(Long sellerId, FulfillmentStatus status, Pageable pageable);
}
