package com.maimai.identity.service;

import com.maimai.common.BizException;
import com.maimai.common.SimpleRateLimiter;
import com.maimai.common.mail.MailService;
import com.maimai.common.security.AuthenticatedUser;
import com.maimai.identity.domain.EmailVerification;
import com.maimai.identity.domain.EmailVerification.Purpose;
import com.maimai.identity.domain.User;
import com.maimai.identity.domain.User.Status;
import com.maimai.identity.domain.UserRole;
import com.maimai.identity.domain.UserRole.Role;
import com.maimai.identity.dto.AuthDtos.LoginRequest;
import com.maimai.identity.dto.AuthDtos.RegisterRequest;
import com.maimai.identity.dto.AuthDtos.ResetPasswordRequest;
import com.maimai.identity.dto.AuthDtos.UserSummary;
import com.maimai.identity.repo.EmailVerificationRepository;
import com.maimai.identity.repo.SellerApplicationRepository;
import com.maimai.identity.repo.UserRepository;
import com.maimai.identity.repo.UserRoleRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.LinkedHashSet;
import java.util.Set;

/** 认证与账号服务：验证码签发/校验、注册、登录校验、找回密码。日志不打印验证码明文与密码。 */
@Service
@Transactional
public class AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthService.class);

    private static final Duration CODE_TTL = Duration.ofMinutes(10);
    private static final Duration RESEND_COOLDOWN = Duration.ofSeconds(60);
    private static final int MAX_ATTEMPTS = 5;
    private static final String BAD_CREDENTIALS_MESSAGE = "邮箱或密码不正确";

    private final UserRepository userRepository;
    private final UserRoleRepository userRoleRepository;
    private final EmailVerificationRepository emailVerificationRepository;
    private final SellerApplicationRepository sellerApplicationRepository;
    private final PasswordEncoder passwordEncoder;
    private final MailService mailService;
    private final SimpleRateLimiter rateLimiter;
    private final JdbcTemplate jdbcTemplate;
    private final EmailCodeVerifier codeVerifier;
    private final PolicyConsentService policyConsent;
    private final SecureRandom secureRandom = new SecureRandom();

    public AuthService(UserRepository userRepository, UserRoleRepository userRoleRepository,
                       EmailVerificationRepository emailVerificationRepository,
                       SellerApplicationRepository sellerApplicationRepository,
                       PasswordEncoder passwordEncoder, MailService mailService,
                       SimpleRateLimiter rateLimiter, JdbcTemplate jdbcTemplate, EmailCodeVerifier codeVerifier,
                       PolicyConsentService policyConsent) {
        this.userRepository = userRepository;
        this.userRoleRepository = userRoleRepository;
        this.emailVerificationRepository = emailVerificationRepository;
        this.sellerApplicationRepository = sellerApplicationRepository;
        this.passwordEncoder = passwordEncoder;
        this.mailService = mailService;
        this.rateLimiter = rateLimiter;
        this.jdbcTemplate = jdbcTemplate;
        this.codeVerifier = codeVerifier;
        this.policyConsent = policyConsent;
    }

    /** 注册验证码：同 email+IP 每小时最多 5 次，60 秒重发冷却，10 分钟有效。 */
    public void sendRegisterCode(String email, String ip) {
        rateLimiter.require("code:REGISTER:ip:" + ip, 20, 3600, "验证码发送过于频繁，请稍后再试");
        rateLimiter.require("code:REGISTER:email:" + email, 5, 3600, "验证码发送过于频繁，请稍后再试");
        rateLimiter.require("code:REGISTER:" + email + "|" + ip, 5, 3600, "验证码发送过于频繁，请稍后再试");
        createAndSendCode(email, Purpose.REGISTER, ip, "注册账号");
    }

    /** 找回密码验证码：无论邮箱是否注册都返回相同成功响应（防枚举），仅已注册邮箱实际发码。 */
    public void sendResetCode(String email, String ip) {
        rateLimiter.require("code:RESET:ip:" + ip, 20, 3600, "验证码发送过于频繁，请稍后再试");
        rateLimiter.require("code:RESET:email:" + email, 5, 3600, "验证码发送过于频繁，请稍后再试");
        rateLimiter.require("code:RESET:" + email + "|" + ip, 5, 3600, "验证码发送过于频繁，请稍后再试");
        if (userRepository.findByEmail(email).isEmpty()) {
            return;
        }
        createAndSendCode(email, Purpose.RESET, ip, "重置密码");
    }

    /** 注册：校验验证码（单次使用）→ 邮箱唯一 → 建号授 USER 角色。返回登录主体（注册即登录）。 */
    @Transactional(noRollbackFor = EmailCodeVerifier.InvalidCodeException.class)
    public AuthenticatedUser register(RegisterRequest request) {
        policyConsent.validate(request.acceptedTerms(),request.policyVersion());
        validatePasswordStrength(request.password());
        verifyCode(request.email(), Purpose.REGISTER, request.code());
        if (userRepository.findByEmail(request.email()).isPresent()) {
            throw BizException.conflict("EMAIL_TAKEN", "该邮箱已注册");
        }
        validatePasswordStrength(request.password());

        User user = new User();
        user.setEmail(request.email());
        user.setPasswordHash(passwordEncoder.encode(request.password()));
        user.setNickname(request.nickname());
        user.setStatus(Status.ACTIVE);
        userRepository.save(user);

        UserRole role = new UserRole();
        role.setUserId(user.getId());
        role.setRole(Role.USER);
        userRoleRepository.save(role);
        policyConsent.record(user.getId(),request.policyVersion());

        log.info("新用户注册 userId={} email={}", user.getId(), user.getEmail());
        return new AuthenticatedUser(user.getId(), user.getEmail(), user.getNickname(), Set.of(Role.USER.name()));
    }

    /** 登录校验：每 IP 每分钟最多 10 次；失败一律返回统一模糊错误。成功返回登录主体。 */
    public AuthenticatedUser login(LoginRequest request, String ip) {
        rateLimiter.require("login:" + ip, 10, 60, "登录尝试过于频繁，请稍后再试");
        User user = userRepository.findByEmail(request.email()).orElse(null);
        if (user == null || user.getStatus() != Status.ACTIVE
                || !passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw BizException.badRequest("BAD_CREDENTIALS", BAD_CREDENTIALS_MESSAGE);
        }
        Set<String> roles = rolesOf(user.getId());
        return new AuthenticatedUser(user.getId(), user.getEmail(), user.getNickname(), roles);
    }

    /** 重置密码：校验验证码后更新 BCrypt 哈希，并使该用户所有旧会话失效。 */
    @Transactional(noRollbackFor = EmailCodeVerifier.InvalidCodeException.class)
    public void resetPassword(ResetPasswordRequest request) {
        validatePasswordStrength(request.newPassword());
        verifyCode(request.email(), Purpose.RESET, request.code());
        validatePasswordStrength(request.newPassword());
        User user = userRepository.findByEmail(request.email())
                .orElseThrow(() -> BizException.badRequest("CODE_INVALID", "验证码不正确或已失效"));
        user.setPasswordHash(passwordEncoder.encode(request.newPassword()));
        userRepository.save(user);
        int invalidated = jdbcTemplate.update("DELETE FROM SPRING_SESSION WHERE PRINCIPAL_NAME = ?", user.getEmail());
        log.info("密码已重置 userId={}，失效旧会话 {} 个", user.getId(), invalidated);
    }

    /** 当前用户摘要（含角色与最新卖家申请状态）。 */
    @Transactional(readOnly = true)
    public UserSummary summaryOf(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> BizException.unauthorized("请先登录"));
        return new UserSummary(user.getId(), user.getEmail(), user.getNickname(), rolesOf(userId),
                sellerStatusOf(userId), jdbcTemplate.query("SELECT filename FROM user_avatars WHERE user_id=?",
                        (rs,n) -> AvatarService.url(rs.getString(1)), userId).stream().findFirst().orElse(null));
    }

    private void createAndSendCode(String email, Purpose purpose, String ip, String scene) {
        Instant now = Instant.now();
        emailVerificationRepository.findTopByEmailAndPurposeOrderByCreatedAtDesc(email, purpose)
                .ifPresent(latest -> {
                    if (latest.getLastSentAt().plus(RESEND_COOLDOWN).isAfter(now)) {
                        throw BizException.tooMany("发送过于频繁，请60秒后再试");
                    }
                });

        String code = String.format("%06d", secureRandom.nextInt(1_000_000));
        EmailVerification verification = new EmailVerification();
        verification.setEmail(email);
        verification.setPurpose(purpose);
        verification.setCodeHash(sha256Hex(code));
        verification.setExpiresAt(now.plus(CODE_TTL));
        verification.setLastSentAt(now);
        verification.setRequestIp(ip);
        emailVerificationRepository.save(verification);

        mailService.send(email, "麦麦二手 - " + scene + "验证码",
                "您正在麦麦二手" + scene + "，本次验证码为：" + code + "\n"
                        + "验证码 10 分钟内有效，仅可使用一次。若非本人操作，请忽略本邮件，切勿将验证码告知他人。");
        log.info("{}验证码已签发 email={} purpose={} ip={}", scene, email, purpose, ip);
    }

    /** 校验验证码：不存在/过期/已用/超限均报同一错误；连续 5 次错误即作废；正确则标记单次使用。 */
    private void verifyCode(String email, Purpose purpose, String code) {
        if (!codeVerifier.consume(email, purpose, code)) {
            throw new EmailCodeVerifier.InvalidCodeException();
        }
    }

    private void validatePasswordStrength(String password) {
        boolean hasLetter = password.chars().anyMatch(Character::isLetter);
        boolean hasDigit = password.chars().anyMatch(Character::isDigit);
        if (password.length() < 8 || password.getBytes(java.nio.charset.StandardCharsets.UTF_8).length > 72
                || !hasLetter || !hasDigit) {
            throw BizException.badRequest("WEAK_PASSWORD", "密码至少8位，且须同时包含字母和数字");
        }
    }

    private Set<String> rolesOf(Long userId) {
        Set<String> roles = new LinkedHashSet<>();
        for (UserRole userRole : userRoleRepository.findByUserId(userId)) {
            roles.add(userRole.getRole().name());
        }
        return roles;
    }

    private String sellerStatusOf(Long userId) {
        return sellerApplicationRepository.findTopByUserIdOrderByCreatedAtDesc(userId)
                .map(application -> application.getStatus().name())
                .orElse("NONE");
    }

    private static String sha256Hex(String raw) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(raw.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 不可用", e);
        }
    }
}
