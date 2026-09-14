package com.maimai.payment.controller;

import com.maimai.payment.dto.PaymentDtos;
import com.maimai.payment.service.PaymentService;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 买家发起支付。 */
@RestController
@RequestMapping("/api/v1/orders")
public class PaymentController {

    private final PaymentService paymentService;

    public PaymentController(PaymentService paymentService) {
        this.paymentService = paymentService;
    }

    @PostMapping("/{orderNo}/pay")
    public PaymentDtos.PayResponse pay(@PathVariable String orderNo) {
        return paymentService.pay(orderNo);
    }
}
