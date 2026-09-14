package com.maimai.identity.controller;

import com.maimai.common.security.AuthenticatedUser;
import com.maimai.common.security.SecurityUtils;
import com.maimai.identity.dto.AuthDtos.CsrfResponse;
import com.maimai.identity.dto.AuthDtos.LoginRequest;
import com.maimai.identity.dto.AuthDtos.RegisterRequest;
import com.maimai.identity.dto.AuthDtos.ResetCodeRequest;
import com.maimai.identity.dto.AuthDtos.ResetPasswordRequest;
import com.maimai.identity.dto.AuthDtos.SendCodeRequest;
import com.maimai.identity.dto.AuthDtos.UserSummary;
import com.maimai.identity.service.AuthService;
import com.maimai.identity.service.SessionAuthenticationService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.session.FindByIndexNameSessionRepository;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** 认证端点：注册/登录/退出/找回密码/CSRF。公开路径已在 SecurityConfig 显式放行。 */
@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final AuthService authService;
    private final SessionAuthenticationService sessions;

    public AuthController(AuthService authService, SessionAuthenticationService sessions) {
        this.authService = authService;
        this.sessions = sessions;
    }

    @PostMapping("/register/code")
    public ResponseEntity<Void> registerCode(@Valid @RequestBody SendCodeRequest request,
                                             HttpServletRequest servletRequest) {
        authService.sendRegisterCode(request.email(), servletRequest.getRemoteAddr());
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/register")
    public UserSummary register(@Valid @RequestBody RegisterRequest request,
                                HttpServletRequest servletRequest, HttpServletResponse servletResponse) {
        AuthenticatedUser principal = authService.register(request);
        sessions.establish(servletRequest, servletResponse, principal);
        return authService.summaryOf(principal.id());
    }

    @PostMapping("/login")
    public UserSummary login(@Valid @RequestBody LoginRequest request,
                             HttpServletRequest servletRequest, HttpServletResponse servletResponse) {
        AuthenticatedUser principal = authService.login(request, servletRequest.getRemoteAddr());
        sessions.establish(servletRequest, servletResponse, principal);
        return authService.summaryOf(principal.id());
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(HttpServletRequest request, HttpServletResponse response) {
        sessions.logout(request, response);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/me")
    public UserSummary me() {
        return authService.summaryOf(SecurityUtils.currentUserId());
    }

    @GetMapping("/csrf")
    public CsrfResponse csrf(HttpServletRequest request) {
        CsrfToken token = (CsrfToken) request.getAttribute(CsrfToken.class.getName());
        return new CsrfResponse(token != null ? token.getToken() : null);
    }

    @PostMapping("/password/code")
    public ResponseEntity<Void> passwordCode(@Valid @RequestBody ResetCodeRequest request,
                                             HttpServletRequest servletRequest) {
        // 无论邮箱是否存在都返回相同成功响应（防枚举），服务内部仅对已注册邮箱实际发码
        authService.sendResetCode(request.email(), servletRequest.getRemoteAddr());
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/password/reset")
    public ResponseEntity<Void> passwordReset(@Valid @RequestBody ResetPasswordRequest request) {
        authService.resetPassword(request);
        return ResponseEntity.noContent().build();
    }

}
