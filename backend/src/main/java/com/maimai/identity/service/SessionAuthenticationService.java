package com.maimai.identity.service;

import com.maimai.common.security.AuthenticatedUser;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.csrf.CsrfTokenRepository;
import org.springframework.session.FindByIndexNameSessionRepository;
import org.springframework.stereotype.Service;

/** Explicit persistence is required for custom JSON login in Spring Security. */
@Service
public class SessionAuthenticationService {
    private final SecurityContextRepository contexts;
    private final CsrfTokenRepository csrf;

    public SessionAuthenticationService(SecurityContextRepository contexts, CsrfTokenRepository csrf) {
        this.contexts = contexts;
        this.csrf = csrf;
    }

    public void establish(HttpServletRequest request, HttpServletResponse response, AuthenticatedUser principal) {
        request.getSession(true);
        request.changeSessionId();
        var context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(new UsernamePasswordAuthenticationToken(principal, null,
                principal.roles().stream().map(role -> new SimpleGrantedAuthority("ROLE_" + role)).toList()));
        SecurityContextHolder.setContext(context);
        request.getSession().setAttribute(FindByIndexNameSessionRepository.PRINCIPAL_NAME_INDEX_NAME,
                principal.email());
        contexts.saveContext(context, request, response);
        csrf.saveToken(csrf.generateToken(request), request, response);
    }

    public void logout(HttpServletRequest request, HttpServletResponse response) {
        var session = request.getSession(false);
        if (session != null) session.invalidate();
        var empty = SecurityContextHolder.createEmptyContext();
        SecurityContextHolder.setContext(empty);
        contexts.saveContext(empty, request, response);
        csrf.saveToken(null, request, response);
    }
}
