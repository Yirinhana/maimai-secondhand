package com.maimai.payment.controller;

import com.maimai.payment.dto.PaymentDtos;
import com.maimai.payment.service.MockPaymentService;
import jakarta.validation.Valid;
import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 开发隔离入口（仅 local/test profile）：模拟支付确认与失败，不发生真实资金。 */
@RestController
@Profile({"local", "test"})
@RequestMapping("/api/v1/dev/mock-pay")
public class DevMockPayController {

    private final MockPaymentService mockPaymentService;

    public DevMockPayController(MockPaymentService mockPaymentService) {
        this.mockPaymentService = mockPaymentService;
    }

    @PostMapping("/confirm")
    public PaymentDtos.MockPayResultResponse confirm(@RequestBody @Valid PaymentDtos.MockPayConfirmRequest request) {
        return mockPaymentService.confirm(request);
    }

    @PostMapping("/fail")
    public PaymentDtos.MockPayResultResponse fail(@RequestBody @Valid PaymentDtos.MockPayFailRequest request) {
        return mockPaymentService.fail(request);
    }
}
