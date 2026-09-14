package com.maimai.common.mail;

/** 邮件发送窄接口：隔离真实发送实现，便于替换为腾讯云 SES 或其他服务。 */
public interface MailService {

    void send(String to, String subject, String textBody);
}
