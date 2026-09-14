package com.maimai.payment.repo;

import com.maimai.payment.domain.LedgerEntry;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface LedgerEntryRepository extends JpaRepository<LedgerEntry, Long> {

    List<LedgerEntry> findByOrderIdOrderByCreatedAtAsc(Long orderId);
}
