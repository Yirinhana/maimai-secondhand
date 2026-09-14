package com.maimai.config;

import com.maimai.common.security.AuthenticatedUser;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.LinkedHashSet;

/** Recheck account status and grants so an old session cannot retain revoked privileges. */
public final class ActiveSessionFilter extends OncePerRequestFilter {
    private final JdbcTemplate jdbc;

    public ActiveSessionFilter(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof AuthenticatedUser principal) {
            var users = jdbc.query("SELECT status, nickname FROM users WHERE id=?",
                    (rs, row) -> new String[]{rs.getString(1), rs.getString(2)}, principal.id());
            if (users.isEmpty() || !"ACTIVE".equals(users.getFirst()[0])) {
                SecurityContextHolder.clearContext();
                var session = request.getSession(false);
                if (session != null) session.invalidate();
            } else {
                var roles = new LinkedHashSet<>(jdbc.queryForList(
                        "SELECT role FROM user_roles WHERE user_id=?", String.class, principal.id()));
                var fresh = new AuthenticatedUser(principal.id(), principal.email(), users.getFirst()[1], roles);
                var context = SecurityContextHolder.createEmptyContext();
                context.setAuthentication(new UsernamePasswordAuthenticationToken(fresh, null,
                        roles.stream().map(role -> new SimpleGrantedAuthority("ROLE_" + role)).toList()));
                SecurityContextHolder.setContext(context);
            }
        }
        chain.doFilter(request, response);
    }
}
