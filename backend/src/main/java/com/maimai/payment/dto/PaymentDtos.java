package com.maimai.payment.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;

import java.time.Instant;

/** 支付模块请求/响应 DTO。金额一律整数分出参，前端负责格式化。 */
public final class PaymentDtos {

    private PaymentDtos() {
    }

    /** 发起支付响应：mock 渠道 simulated=true，message 明确标注模拟支付、未发生真实资金。 */
    public record PayResponse(String payNo, String channel, long amountCents, boolean simulated, String message) {
    }

    /** 支付单状态查询响应（买卖双方可见）。 */
    public record PaymentStatusResponse(String payNo, String status, boolean simulated, Instant paidAt) {
    }

    /** 开发环境模拟支付确认请求；amountCents 可选，提供时用于金额一致性校验。 */
    public record MockPayConfirmRequest(@NotBlank String payNo, @Positive Long amountCents) {
    }

    /** 开发环境模拟支付失败请求。 */
    public record MockPayFailRequest(@NotBlank String payNo) {
    }

    /** 开发环境模拟支付操作结果。 */
    public record MockPayResultResponse(String payNo, String status, boolean simulated, String message) {
    }
}
