package com.maimai.aftersales.repo;

import com.maimai.aftersales.domain.Aftersale;
import com.maimai.aftersales.domain.Aftersale.Status;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface AftersaleRepository extends JpaRepository<Aftersale, Long> {

    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query("select a from Aftersale a where a.id=:id")
    Optional<Aftersale> lockById(@org.springframework.data.repository.query.Param("id") Long id);

    Optional<Aftersale> findByAftersaleNo(String aftersaleNo);

    Page<Aftersale> findByBuyerIdOrderByCreatedAtDesc(Long buyerId, Pageable pageable);

    Page<Aftersale> findByStatus(Status status, Pageable pageable);

    List<Aftersale> findByOrderId(Long orderId);

    boolean existsByOrderIdAndStatusIn(Long orderId, Collection<Status> statuses);
}
