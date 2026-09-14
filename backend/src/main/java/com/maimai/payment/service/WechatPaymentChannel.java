package com.maimai.payment.service;

import com.maimai.common.BizException;
import com.maimai.config.MaimaiProperties;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

/**
 * 微信支付渠道占位：真实接入待资质与渠道开通。
 * 任何情况下都绝不伪造成功——未配置凭据或已配置但未实现真实调用时一律拒绝。
 */
@Component
public class WechatPaymentChannel {

    private final MaimaiProperties properties;

    public WechatPaymentChannel(MaimaiProperties properties) {
        this.properties = properties;
    }

    /** 校验微信支付可用性；当前阶段必定抛出拒绝异常。 */
    public void ensureReady() {
        if (!properties.getPayment().getWechat().configured()) {
            throw new BizException("PAYMENT_NOT_CONFIGURED", "微信支付未配置真实凭据，拒绝发起",
                    HttpStatus.SERVICE_UNAVAILABLE);
        }
        throw new BizException("PAYMENT_NOT_CONFIGURED", "微信支付真实接入待渠道开通",
                HttpStatus.SERVICE_UNAVAILABLE);
    }
}
