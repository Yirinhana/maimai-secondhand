package com.maimai.trade.repo;

import com.maimai.trade.domain.CartItem;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface CartItemRepository extends JpaRepository<CartItem, Long> {

    List<CartItem> findByUserId(Long userId);

    Optional<CartItem> findByUserIdAndProductIdAndDeliveryMethod(Long userId, Long productId, String deliveryMethod);
}
