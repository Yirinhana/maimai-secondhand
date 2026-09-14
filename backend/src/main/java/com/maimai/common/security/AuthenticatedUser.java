package com.maimai.common.security;

import java.io.Serial;
import java.io.Serializable;
import java.util.Set;

/** 登录主体：写入会话的轻量身份，不含密码等敏感信息。须可序列化以支持 JDBC 持久化会话。 */
public record AuthenticatedUser(Long id, String email, String nickname, Set<String> roles)
        implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    public boolean hasRole(String role) {
        return roles != null && roles.contains(role);
    }

    public boolean isAdmin() {
        return hasRole("SUPER_ADMIN") || hasRole("OPERATOR") || hasRole("SUPPORT");
    }
}
