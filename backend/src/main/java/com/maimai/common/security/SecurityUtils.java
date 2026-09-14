package com.maimai.common.security;

import com.maimai.common.BizException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

/** 当前登录用户解析：所有服务端业务入口统一经此获取身份并做资源归属校验。 */
public final class SecurityUtils {

    private SecurityUtils() {
    }

    public static AuthenticatedUser current() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !(auth.getPrincipal() instanceof AuthenticatedUser user)) {
            throw BizException.unauthorized("请先登录");
        }
        return user;
    }

    public static Long currentUserId() {
        return current().id();
    }

    /** 校验当前用户即资源所有者，否则按不泄露资源存在性的策略返回拒绝。 */
    public static void requireOwner(Long ownerId) {
        if (ownerId == null || !ownerId.equals(currentUserId())) {
            throw BizException.forbidden("无权操作该资源");
        }
    }

    public static void requireAdmin() {
        if (!current().isAdmin()) {
            throw BizException.forbidden("需要后台权限");
        }
    }
}
