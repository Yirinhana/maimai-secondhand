package com.maimai.common.mail;

import com.maimai.common.BizException;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.AddressException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.stereotype.Service;

/** 专用邮箱SMTP授权码适配；本地默认仍使用邮件捕获，不向外部发信。 */
@Service
@ConditionalOnProperty(prefix = "maimai.mail", name = "provider", havingValue = "smtp")
public class SmtpMailService implements MailService {
    private final JavaMailSender sender;
    private final String from;
    private final boolean configured;

    @Autowired
    public SmtpMailService(@Value("${MAIMAI_SMTP_HOST:}") String host,
                           @Value("${MAIMAI_SMTP_PORT:465}") int port,
                           @Value("${MAIMAI_SMTP_USERNAME:}") String username,
                           @Value("${MAIMAI_SMTP_PASSWORD:}") String password,
                           @Value("${MAIMAI_SMTP_SSL:true}") boolean ssl,
                           @Value("${MAIMAI_MAIL_FROM:}") String from) {
        this(createSender(host, port, username, password, ssl), from,
                !host.isBlank() && port > 0 && port <= 65535 && !username.isBlank() && !password.isBlank() && validAddress(from));
    }

    SmtpMailService(JavaMailSender sender, String from, boolean configured) {
        this.sender = sender;
        this.from = from;
        this.configured = configured;
    }

    static JavaMailSenderImpl createSender(String host, int port, String username, String password, boolean ssl) {
        var sender = new JavaMailSenderImpl();
        sender.setHost(host); sender.setPort(port); sender.setUsername(username); sender.setPassword(password);
        sender.setDefaultEncoding("UTF-8");
        var properties = sender.getJavaMailProperties();
        properties.setProperty("mail.smtp.auth", "true");
        properties.setProperty("mail.smtp.ssl.enable", Boolean.toString(ssl));
        properties.setProperty("mail.smtp.starttls.enable", Boolean.toString(!ssl));
        properties.setProperty("mail.smtp.starttls.required", Boolean.toString(!ssl));
        properties.setProperty("mail.smtp.ssl.checkserveridentity", "true");
        properties.setProperty("mail.smtp.connectiontimeout", "3000");
        properties.setProperty("mail.smtp.timeout", "5000");
        properties.setProperty("mail.smtp.writetimeout", "5000");
        properties.setProperty("mail.debug", "false");
        return sender;
    }

    @Override public void send(String to, String subject, String textBody) {
        if (!configured) throw new BizException("MAIL_NOT_CONFIGURED", "邮件服务尚未配置", HttpStatus.SERVICE_UNAVAILABLE);
        if (!validAddress(to) || subject == null || subject.contains("\r") || subject.contains("\n") || textBody == null)
            throw BizException.badRequest("MAIL_INVALID_MESSAGE", "邮件收件地址或内容无效");
        var message = new SimpleMailMessage();
        message.setFrom(from); message.setTo(to); message.setSubject(subject); message.setText(textBody);
        try { sender.send(message); }
        catch (MailException error) {
            // 供应商异常可能包含邮箱或授权码；不记录或透传原始错误。
            throw new BizException("MAIL_SEND_FAILED", "邮件暂时发送失败，请稍后重试", HttpStatus.SERVICE_UNAVAILABLE);
        }
    }

    private static boolean validAddress(String value) {
        if (value == null || value.isBlank() || value.contains("\r") || value.contains("\n")) return false;
        try {new InternetAddress(value, true).validate(); return true;}
        catch (AddressException error) {return false;}
    }
}
