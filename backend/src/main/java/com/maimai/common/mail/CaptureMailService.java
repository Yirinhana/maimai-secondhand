package com.maimai.common.mail;

import com.maimai.config.MaimaiProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;

/**
 * 本地开发邮件捕获：不真实发送，将邮件写入 .local/mail-capture 目录。
 * 仅本地开发邮件入口可读取捕获内容；正式环境不提供该入口。
 * 仅 local/test profile 可用；其他环境配置 capture 将因缺少 Bean 直接启动失败（禁止静默模拟）。
 */
@Service
@Profile({"local", "test"})
@ConditionalOnProperty(prefix = "maimai.mail", name = "provider", havingValue = "capture")
public class CaptureMailService implements MailService {

    private static final Logger log = LoggerFactory.getLogger(CaptureMailService.class);

    private final Path captureDir;

    public CaptureMailService(MaimaiProperties properties) throws IOException {
        this.captureDir = Path.of(properties.getMail().getCaptureDir()).toAbsolutePath().normalize();
        Files.createDirectories(captureDir);
    }

    @Override
    public void send(String to, String subject, String textBody) {
        String fileName = System.currentTimeMillis() + "-" + to.replaceAll("[^A-Za-z0-9@._-]", "_") + ".eml";
        String content = "From: maimai-local-capture\nTo: " + to + "\nSubject: " + subject
                + "\nDate: " + Instant.now() + "\n\n" + textBody + "\n";
        try {
            Files.writeString(captureDir.resolve(fileName), content, StandardCharsets.UTF_8);
            log.info("[本地邮件捕获] 已写入 {} (收件人: {})", fileName, to);
        } catch (IOException e) {
            throw new IllegalStateException("本地邮件捕获写入失败", e);
        }
    }
}
