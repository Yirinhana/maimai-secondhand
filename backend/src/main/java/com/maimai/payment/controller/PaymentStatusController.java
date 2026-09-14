package com.maimai.payment.controller;

import com.maimai.payment.dto.PaymentDtos;
import com.maimai.payment.service.PaymentService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 订单最新支付单状态查询（买卖双方与后台可见）。 */
@RestController
@RequestMapping("/api/v1/orders")
public class PaymentStatusController {

    private final PaymentService paymentService;

    public PaymentStatusController(PaymentService paymentService) {
        this.paymentService = paymentService;
    }

    @GetMapping("/{orderNo}/payment")
    public PaymentDtos.PaymentStatusResponse latest(@PathVariable String orderNo) {
        return paymentService.latestStatus(orderNo);
    }
}
