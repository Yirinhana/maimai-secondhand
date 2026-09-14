package com.maimai.trade.controller;

import com.maimai.common.security.SecurityUtils;
import com.maimai.trade.dto.TradeDtos.DeliveryCodeResponse;
import com.maimai.trade.dto.TradeDtos.VerifyCodeRequest;
import com.maimai.trade.service.DeliveryCodeService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 面交交付码端点。 */
@RestController
@RequestMapping("/api/v1/orders")
public class DeliveryCodeController {

    private final DeliveryCodeService deliveryCodeService;

    public DeliveryCodeController(DeliveryCodeService deliveryCodeService) {
        this.deliveryCodeService = deliveryCodeService;
    }

    @PostMapping("/{orderNo}/delivery-code")
    public DeliveryCodeResponse generate(@PathVariable String orderNo) {
        return deliveryCodeService.generate(SecurityUtils.currentUserId(), orderNo);
    }

    @PostMapping("/{orderNo}/verify-code")
    public ResponseEntity<Void> verify(@PathVariable String orderNo,
                                       @RequestBody @Valid VerifyCodeRequest request) {
        deliveryCodeService.verify(SecurityUtils.currentUserId(), orderNo, request.code());
        return ResponseEntity.noContent().build();
    }
}
