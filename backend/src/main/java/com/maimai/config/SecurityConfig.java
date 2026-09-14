package com.maimai.config;

import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfTokenRepository;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextHolderFilter;
import org.springframework.session.web.http.DefaultCookieSerializer;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;
import java.util.UUID;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    private final boolean localProfile;

    public SecurityConfig(Environment environment) {
        // test profile 用于真实数据库集成测试，与 local 一样开放隔离的开发入口；其余环境一律拒绝
        this.localProfile = environment.acceptsProfiles(Profiles.of("local", "test"));
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    /** JSON login uses AuthService; do not create an unused generated-password account. */
    @Bean
    public org.springframework.security.core.userdetails.UserDetailsService userDetailsService() {
        return username -> { throw new org.springframework.security.core.userdetails.UsernameNotFoundException("Use the email login endpoint"); };
    }

    @Bean
    public SecurityContextRepository securityContextRepository() {
        return new HttpSessionSecurityContextRepository();
    }

    @Bean
    public CsrfTokenRepository csrfTokenRepository() {
        var repository = CookieCsrfTokenRepository.withHttpOnlyFalse();
        repository.setCookiePath("/");
        repository.setCookieCustomizer(cookie -> cookie.sameSite("Lax").secure(!localProfile));
        return repository;
    }

    @Bean
    public DefaultCookieSerializer cookieSerializer() {
        var cookie = new DefaultCookieSerializer();
        cookie.setCookiePath("/");
        cookie.setUseHttpOnlyCookie(true);
        cookie.setUseSecureCookie(!localProfile);
        cookie.setSameSite("Lax");
        return cookie;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http,
                                                   CorsConfigurationSource corsConfigurationSource,
                                                   CsrfTokenRepository csrfTokenRepository,
                                                   SecurityContextRepository securityContextRepository,
                                                   JdbcTemplate jdbc) throws Exception {

        http.securityContext(context -> context.securityContextRepository(securityContextRepository)
                .requireExplicitSave(true))
            .csrf(csrf -> csrf.csrfTokenRepository(csrfTokenRepository)
                .csrfTokenRequestHandler(new SpaCsrfTokenRequestHandler()))
            .cors(cors -> cors.configurationSource(corsConfigurationSource))
            .authorizeHttpRequests(auth -> {
                auth.requestMatchers(request -> "GET".equals(request.getMethod()) &&
                        request.getServletPath().matches("/api/v1/community/(demands(?:/[0-9]+(?:/replies)?)?|users/[0-9]+/ratings)"))
                        .permitAll();
                auth.requestMatchers(HttpMethod.GET,
                        "/api/v1/categories", "/api/v1/products", "/api/v1/products/*",
                        "/api/v1/sellers/*", "/api/v1/sellers/*/products",
                        "/api/v1/policies/current", "/api/v1/shipping-provinces", "/api/v1/support/faq",
                        "/uploads/**", "/error").permitAll()
                    .requestMatchers("/api/v1/auth/register/code", "/api/v1/auth/register",
                        "/api/v1/auth/login", "/api/v1/auth/password/code",
                        "/api/v1/auth/password/reset", "/api/v1/auth/csrf").permitAll()
                    .requestMatchers("/api/v1/admin/**").hasAnyRole("OPERATOR", "SUPPORT", "SUPER_ADMIN");
                if (localProfile) {
                    // 开发入口（模拟支付、邮件捕获）：仅 local profile 注册并放行，其余环境一律拒绝
                    auth.requestMatchers("/api/v1/dev/**").permitAll();
                } else {
                    auth.requestMatchers("/api/v1/dev/**").denyAll();
                }
                auth.anyRequest().authenticated();
            })
            .exceptionHandling(handling -> handling
                .authenticationEntryPoint((req, res, ex) -> writeJson(res, 401, "UNAUTHORIZED", "请先登录"))
                .accessDeniedHandler((req, res, ex) -> writeJson(res, 403, "FORBIDDEN", "没有执行该操作的权限")))
            .addFilterAfter(new ActiveSessionFilter(jdbc), SecurityContextHolderFilter.class)
            .formLogin(form -> form.disable())
            .httpBasic(basic -> basic.disable())
            .logout(logout -> logout.disable());

        return http.build();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource(
            @Value("${maimai.frontend-origin:http://localhost:5173}") String frontendOrigin) {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(List.of(frontendOrigin));
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("Content-Type", "X-XSRF-TOKEN"));
        config.setAllowCredentials(true);
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/**", config);
        return source;
    }

    private void writeJson(HttpServletResponse res, int status, String code, String message)
            throws java.io.IOException {
        res.setStatus(status);
        res.setContentType(MediaType.APPLICATION_JSON_VALUE);
        res.setCharacterEncoding("UTF-8");
        String traceId = UUID.randomUUID().toString().substring(0, 8);
        res.getWriter().write("{\"code\":\"" + code + "\",\"message\":\"" + message
                + "\",\"traceId\":\"" + traceId + "\",\"timestamp\":\""
                + java.time.Instant.now() + "\"}");
    }
}
