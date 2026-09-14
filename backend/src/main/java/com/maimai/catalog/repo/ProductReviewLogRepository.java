package com.maimai.catalog.repo;

import com.maimai.catalog.domain.ProductReviewLog;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ProductReviewLogRepository extends JpaRepository<ProductReviewLog, Long> {

    List<ProductReviewLog> findByProductIdOrderByCreatedAtDesc(Long productId);
}
