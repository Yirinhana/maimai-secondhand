package com.maimai.aftersales.repo;

import com.maimai.aftersales.domain.Aftersale;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;

/**
 * 售后补充查询（既有 AftersaleRepository 之外的派生/联表查询，独立接口避免改动已交付文件）。
 */
public interface AftersaleQueryRepository extends JpaRepository<Aftersale, Long> {

    /** 卖家视角：按订单 sellerId 分页。 */
    @Query("select a from Aftersale a, Order o where a.orderId = o.id and o.sellerId = :sellerId order by a.createdAt desc")
    Page<Aftersale> findBySellerId(@Param("sellerId") Long sellerId, Pageable pageable);

    /** 调度器：卖家响应超时的售后单。 */
    List<Aftersale> findByStatusAndSellerDeadlineBefore(Aftersale.Status status, Instant deadline);

    /** 调度器：退货寄回超时的售后单。 */
    List<Aftersale> findByStatusAndReturnDeadlineBefore(Aftersale.Status status, Instant deadline);

    List<Aftersale> findByStatusAndReturnInspectionDeadlineBefore(Aftersale.Status status, Instant deadline);

    @Query(value = "SELECT a.* FROM aftersales a JOIN orders o ON o.id=a.order_id JOIN users u ON u.id=o.seller_id "
            + "WHERE a.status IN ('PENDING_SELLER','PENDING_RETURN','RETURN_SHIPPED') AND u.status<>'ACTIVE'", nativeQuery = true)
    List<Aftersale> findActiveWithUnavailableSeller();
}
