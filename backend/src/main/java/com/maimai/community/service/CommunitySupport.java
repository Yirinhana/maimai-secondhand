package com.maimai.community.service;

import com.maimai.common.BizException;
import com.maimai.common.security.SecurityUtils;
import com.maimai.community.dto.CommunityDtos.PageResult;
import com.maimai.community.repository.CommunityRepository;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Objects;

/** Community authorization, input bounds and audit helpers; no business state changes here. */
@Component
class CommunitySupport {
    final CommunityRepository repo;

    CommunitySupport(CommunityRepository repo) { this.repo = repo; }

    void self(Long userId) {
        if (!Objects.equals(SecurityUtils.currentUserId(), userId)) {
            throw BizException.forbidden("只能操作自己的社区数据");
        }
    }

    void moderator(Long userId) {
        self(userId);
        var actor = SecurityUtils.current();
        if (!actor.hasRole("OPERATOR") && !actor.hasRole("SUPER_ADMIN")) {
            throw BizException.forbidden("仅运营或超级管理员可审核社区内容");
        }
    }

    void lockUser(Long userId) {
        self(userId);
        Long id = repo.queryOne("SELECT id FROM users WHERE id = ? AND status = 'ACTIVE' FOR UPDATE",
                (rs, n) -> rs.getLong(1), userId);
        if (id == null) throw BizException.forbidden("账号不可用");
    }

    void publicProduct(Long productId) {
        if (productId == null || repo.count("SELECT COUNT(*) FROM products WHERE id = ? AND status = 'ON_SALE'", productId) == 0) {
            throw BizException.notFound("商品不存在或暂不可见");
        }
    }

    void category(Long categoryId) {
        if (categoryId != null && repo.count("SELECT COUNT(*) FROM categories WHERE id = ? AND status = 'ACTIVE'", categoryId) == 0) {
            throw BizException.notFound("分类不存在或已停用");
        }
    }

    static String text(String value, String field, int min, int max) {
        if (value == null) {
            if (min == 0) return null;
            throw BizException.badRequest("INVALID_CONTENT", field + "不能为空");
        }
        String normalized = value.strip();
        if (normalized.length() < min || normalized.length() > max || normalized.indexOf('\0') >= 0) {
            throw BizException.badRequest("INVALID_CONTENT", field + "长度须为" + min + "至" + max + "字");
        }
        // Stored and returned as plain text; renderers must use text interpolation, never v-html.
        return normalized;
    }

    static Paging paging(Integer page, Integer size) {
        int p = page == null ? 0 : page;
        int s = size == null ? 20 : Math.min(size, 50);
        if (p < 0 || p > 10_000 || s < 1) throw BizException.badRequest("PAGE_INVALID", "页号须为0至10000，每页至少1条");
        return new Paging(p, s);
    }

    record Paging(int page, int size) {
        long offset() { return (long) page * size; }
        <T> PageResult<T> result(List<T> items, long total) {
            return new PageResult<>(items, total, page, size, (int) Math.min(Integer.MAX_VALUE, (total + size - 1) / size));
        }
    }

    void audit(Long actor, String action, String type, Long target, String before, String after, String note) {
        String role = SecurityUtils.current().hasRole("SUPER_ADMIN") ? "SUPER_ADMIN" : "OPERATOR";
        repo.update("""
                INSERT INTO community_action_logs(actor_id, actor_role, action, target_type, target_id, before_state, after_state, note)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?)""", actor, role, action, type, target, before, after, note);
    }
}
