package com.maimai.trade.controller;

import com.maimai.common.security.SecurityUtils;
import com.maimai.trade.dto.TradeDtos.AddCartItemRequest;
import com.maimai.trade.dto.TradeDtos.CartItemDto;
import com.maimai.trade.dto.TradeDtos.UpdateCartItemRequest;
import com.maimai.trade.service.CartService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** 购物车端点（仅本人）。 */
@RestController
@RequestMapping("/api/v1/cart")
public class CartController {

    private final CartService cartService;

    public CartController(CartService cartService) {
        this.cartService = cartService;
    }

    @GetMapping
    public List<CartItemDto> list() {
        return cartService.list(SecurityUtils.currentUserId());
    }

    @PostMapping
    public CartItemDto add(@RequestBody @Valid AddCartItemRequest request) {
        return cartService.add(SecurityUtils.currentUserId(),
                request.productId(), request.quantity(), request.deliveryMethod());
    }

    @PutMapping("/{id}")
    public CartItemDto update(@PathVariable Long id, @RequestBody @Valid UpdateCartItemRequest request) {
        return cartService.update(SecurityUtils.currentUserId(), id, request.quantity());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        cartService.delete(SecurityUtils.currentUserId(), id);
        return ResponseEntity.noContent().build();
    }
}
