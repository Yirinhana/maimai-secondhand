package com.maimai.identity.controller;

import com.maimai.common.BizException;
import com.maimai.config.MaimaiProperties;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

/**
 * 开发调试邮箱：读取本地邮件捕获目录中指定邮箱最近 10 封邮件。
 * 仅 local/test profile 注册，且仅允许本机 loopback 访问。
 */
@RestController
@RequestMapping("/api/v1/dev")
@Profile({"local", "test"})
public class DevMailboxController {

    private static final int MAX_ENTRIES = 10;

    private final Path captureDir;

    public DevMailboxController(MaimaiProperties properties) {
        this.captureDir = Path.of(properties.getMail().getCaptureDir()).toAbsolutePath().normalize();
    }

    public record MailboxEntry(String fileName, String content) {
    }

    @GetMapping("/mailbox")
    public List<MailboxEntry> mailbox(@RequestParam String email, HttpServletRequest request) {
        String remote = request.getRemoteAddr();
        if (!"127.0.0.1".equals(remote) && !"0:0:0:0:0:0:0:1".equals(remote)) {
            throw BizException.forbidden("开发邮箱仅允许本机访问");
        }
        if (!Files.isDirectory(captureDir)) {
            return List.of();
        }
        // 文件名后缀与 CaptureMailService 的写入格式保持一致：<时间戳>-<清洗后的邮箱>.eml
        String suffix = "-" + email.replaceAll("[^A-Za-z0-9@._-]", "_") + ".eml";
        List<Path> files;
        try (Stream<Path> stream = Files.list(captureDir)) {
            files = stream.filter(path -> path.getFileName().toString().endsWith(suffix))
                    .sorted(Comparator.comparing((Path path) -> path.getFileName().toString()).reversed())
                    .limit(MAX_ENTRIES)
                    .toList();
        } catch (IOException e) {
            throw new UncheckedIOException("读取邮件捕获目录失败", e);
        }
        List<MailboxEntry> entries = new ArrayList<>();
        for (Path file : files) {
            try {
                entries.add(new MailboxEntry(file.getFileName().toString(),
                        Files.readString(file, StandardCharsets.UTF_8)));
            } catch (IOException e) {
                throw new UncheckedIOException("读取捕获邮件失败: " + file.getFileName(), e);
            }
        }
        return entries;
    }
}
