package com.maimai.common.mail;

import com.maimai.common.BizException;
import com.maimai.config.MaimaiProperties;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

/**
 * 腾讯云 SES 发信适配器占位：真实接入待配置（发信域名验证、SPF/DKIM、最小权限 API 凭据）。
 * 未接入前一律拒绝发送，绝不静默成功。
 */
@Service
@ConditionalOnProperty(prefix = "maimai.mail", name = "provider", havingValue = "ses")
public class SesMailService implements MailService {

    public SesMailService(MaimaiProperties properties) {
    }

    @Override
    public void send(String to, String subject, String textBody) {
        throw new BizException("MAIL_NOT_CONFIGURED",
                "邮件服务尚未完成腾讯云 SES 真实接入，拒绝发送", HttpStatus.SERVICE_UNAVAILABLE);
    }
}
