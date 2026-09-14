package com.maimai.catalog.repo;

import com.maimai.catalog.domain.Product;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.jpa.repository.Lock;
import jakarta.persistence.LockModeType;
import java.util.Optional;

public interface ProductRepository extends JpaRepository<Product, Long>, JpaSpecificationExecutor<Product> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from Product p where p.id=:id")
    Optional<Product> lockById(@Param("id") Long id);

    Page<Product> findBySellerIdOrderByCreatedAtDesc(Long sellerId, Pageable pageable);

    @Modifying
    @Query("update Product p set p.stockAvailable = p.stockAvailable - :q, p.stockReserved = p.stockReserved + :q, p.version = p.version + 1 where p.id = :id and p.stockAvailable >= :q")
    int reserveStock(@Param("id") Long id, @Param("q") int q);

    @Modifying
    @Query("update Product p set p.stockAvailable = p.stockAvailable + :q, p.stockReserved = p.stockReserved - :q where p.id = :id and p.stockReserved >= :q")
    int releaseStock(@Param("id") Long id, @Param("q") int q);

    @Modifying
    @Query("update Product p set p.stockReserved = p.stockReserved - :q, p.stockSold = p.stockSold + :q where p.id = :id and p.stockReserved >= :q")
    int consumeReserved(@Param("id") Long id, @Param("q") int q);
}
