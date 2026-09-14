package com.maimai.catalog.repo;

import com.maimai.catalog.domain.StockLog;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface StockLogRepository extends JpaRepository<StockLog, Long> {

    List<StockLog> findByProductIdOrderByCreatedAtDesc(Long productId);
}
