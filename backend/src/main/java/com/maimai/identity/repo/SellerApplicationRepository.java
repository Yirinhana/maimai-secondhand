package com.maimai.identity.repo;

import com.maimai.identity.domain.SellerApplication;
import com.maimai.identity.domain.SellerApplication.Status;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface SellerApplicationRepository extends JpaRepository<SellerApplication, Long> {

    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query("select a from SellerApplication a where a.id=:id")
    Optional<SellerApplication> lockById(@org.springframework.data.repository.query.Param("id") Long id);

    Optional<SellerApplication> findTopByUserIdOrderByCreatedAtDesc(Long userId);

    Page<SellerApplication> findByStatus(Status status, Pageable pageable);
}
