package com.maimai.aftersales.repo;

import com.maimai.aftersales.domain.AftersaleLog;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AftersaleLogRepository extends JpaRepository<AftersaleLog, Long> {

    List<AftersaleLog> findByAftersaleIdOrderByCreatedAtAsc(Long aftersaleId);
}
