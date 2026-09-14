package com.maimai.identity.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.Set;

/** 认证与账号端点的请求/响应 DTO。 */
public final class AuthDtos {

    private AuthDtos() {
    }

    public record SendCodeRequest(
            @NotBlank @Email @Size(max = 190) String email) {
        public SendCodeRequest { email = normalizeEmail(email); }
    }

    public record RegisterRequest(
            @NotBlank @Email @Size(max = 190) String email,
            @NotBlank @Pattern(regexp = "\\d{6}", message = "验证码为6位数字") String code,
            @NotBlank @Size(min = 8, max = 72) String password,
            @NotBlank @Size(max = 50) String nickname,
            @jakarta.validation.constraints.NotNull @jakarta.validation.constraints.AssertTrue Boolean acceptedTerms,
            @NotBlank @Size(max = 64) String policyVersion) {
        public RegisterRequest { email = normalizeEmail(email); }
    }

    public record LoginRequest(
            @NotBlank @Email @Size(max = 190) String email,
            @NotBlank @Size(max = 72) String password) {
        public LoginRequest { email = normalizeEmail(email); }
    }

    public record ResetCodeRequest(
            @NotBlank @Email @Size(max = 190) String email) {
        public ResetCodeRequest { email = normalizeEmail(email); }
    }

    public record ResetPasswordRequest(
            @NotBlank @Email @Size(max = 190) String email,
            @NotBlank @Pattern(regexp = "\\d{6}", message = "验证码为6位数字") String code,
            @NotBlank @Size(min = 8, max = 72) String newPassword) {
        public ResetPasswordRequest { email = normalizeEmail(email); }
    }

    public record UserSummary(Long id, String email, String nickname, Set<String> roles,
                              String sellerStatus) {
    }

    public record CsrfResponse(String token) {
    }

    private static String normalizeEmail(String email) {
        return email == null ? null : email.trim().toLowerCase(java.util.Locale.ROOT);
    }
}
