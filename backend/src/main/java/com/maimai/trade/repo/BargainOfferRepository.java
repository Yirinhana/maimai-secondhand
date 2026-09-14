package com.maimai.trade.repo;

import com.maimai.trade.domain.BargainOffer;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface BargainOfferRepository extends JpaRepository<BargainOffer, Long> {

    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query("select b from BargainOffer b where b.id=:id")
    java.util.Optional<BargainOffer> lockById(@org.springframework.data.repository.query.Param("id") Long id);

    List<BargainOffer> findByBuyerIdOrderByCreatedAtDesc(Long buyerId);

    List<BargainOffer> findByProductIdOrderByCreatedAtDesc(Long productId);
}
