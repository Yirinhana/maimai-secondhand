package com.maimai.common;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * 平台服务费计算（唯一入口）。
 * 费率 r = 0.0003（万分之三），基数仅为商品实际成交金额（整数分），不含运费。
 * F = ROUND_HALF_UP(G × r)，结果单位为分，允许为 0。
 */
public final class FeeCalculator {

    public static final BigDecimal PLATFORM_RATE = new BigDecimal("0.0003");

    private FeeCalculator() {
    }

    /** 计算子订单商品金额对应的平台服务费（分）。 */
    public static long platformFee(long goodsAmountCents) {
        if (goodsAmountCents < 0) {
            throw new IllegalArgumentException("goodsAmountCents must be >= 0");
        }
        return BigDecimal.valueOf(goodsAmountCents)
                .multiply(PLATFORM_RATE)
                .setScale(0, RoundingMode.HALF_UP)
                .longValue();
    }

    /**
     * 连续退款时应保留的平台费：按剩余商品款 G剩 = G − R 重算。
     * 本次应退平台费差额 = (原平台费 − 应保留平台费) − 已累计退还的平台费。
     *
     * @param originalGoodsCents      原商品成交额（分）
     * @param refundedGoodsCentsTotal 累计已成功退还的商品款（分）
     * @param originalFeeCents        原收取平台费（分）
     * @param refundedFeeCentsTotal   累计已退平台费（分）
     * @return 本次可退平台费（分），不为负
     */
    public static long refundableFeeDelta(long originalGoodsCents, long refundedGoodsCentsTotal,
                                          long originalFeeCents, long refundedFeeCentsTotal) {
        long remainingGoods = originalGoodsCents - refundedGoodsCentsTotal;
        if (remainingGoods < 0) {
            throw new IllegalArgumentException("累计退款超过商品成交额");
        }
        long keepFee = platformFee(remainingGoods);
        long cumulativeRefundable = originalFeeCents - keepFee;
        long delta = cumulativeRefundable - refundedFeeCentsTotal;
        return Math.max(delta, 0);
    }
}
