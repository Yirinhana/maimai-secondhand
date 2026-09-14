package com.maimai.trade.controller;

import com.maimai.common.security.SecurityUtils;
import com.maimai.trade.dto.TradeDtos.CheckoutRequest;
import com.maimai.trade.dto.TradeDtos.CheckoutResponse;
import com.maimai.trade.service.CheckoutService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 结算端点。 */
@RestController
@RequestMapping("/api/v1")
public class CheckoutController {

    private final CheckoutService checkoutService;

    public CheckoutController(CheckoutService checkoutService) {
        this.checkoutService = checkoutService;
    }

    @PostMapping("/checkout")
    public CheckoutResponse checkout(@RequestBody @Valid CheckoutRequest request) {
        return checkoutService.checkout(SecurityUtils.currentUserId(), request);
    }
}
