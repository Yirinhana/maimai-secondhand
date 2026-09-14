package com.maimai.admin.service;

import com.maimai.admin.dto.AdminDtos;
import com.maimai.common.BizException;
import com.maimai.common.security.SecurityUtils;
import com.maimai.identity.domain.User;
import com.maimai.identity.domain.UserRole;
import com.maimai.identity.repo.UserRepository;
import com.maimai.identity.repo.UserRoleRepository;
import com.maimai.notification.NotificationService;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/** 后台用户管理：模糊检索与状态变更（状态变更仅 SUPER_ADMIN）。 */
@Service
@Transactional
public class AdminUserService {

    private final UserRepository userRepository;
    private final UserRoleRepository userRoleRepository;
    private final NotificationService notificationService;
    private final AdminAuditService adminAuditService;
    private final JdbcTemplate jdbcTemplate;
    private final GovernanceGuard governance;

    public AdminUserService(UserRepository userRepository,
                            UserRoleRepository userRoleRepository,
                            NotificationService notificationService,
                            AdminAuditService adminAuditService,
                            JdbcTemplate jdbcTemplate, GovernanceGuard governance) {
        this.userRepository = userRepository;
        this.userRoleRepository = userRoleRepository;
        this.notificationService = notificationService;
        this.adminAuditService = adminAuditService;
        this.jdbcTemplate = jdbcTemplate;
        this.governance = governance;
    }

    @Transactional(readOnly = true)
    public AdminDtos.PageResult<AdminDtos.UserItem> list(String keyword, int page, int size) {
        String trimmed = keyword == null ? "" : keyword.trim();
        String where = trimmed.isEmpty() ? "" : " WHERE email LIKE ? OR nickname LIKE ?";
        List<Object> params = new ArrayList<>();
        if (!trimmed.isEmpty()) {
            String like = "%" + trimmed + "%";
            params.add(like);
            params.add(like);
        }
        Long total = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM users" + where, Long.class, params.toArray());
        List<Object> pageParams = new ArrayList<>(params);
        pageParams.add(size);
        pageParams.add((long) page * size);
        List<AdminDtos.UserItem> content = jdbcTemplate.query(
                "SELECT id, email, nickname, status, created_at FROM users" + where
                        + " ORDER BY id LIMIT ? OFFSET ?",
                (rs, rowNum) -> new AdminDtos.UserItem(rs.getLong("id"), rs.getString("email"),
                        rs.getString("nickname"), rs.getString("status"),
                        rolesOf(rs.getLong("id")),
                        rs.getTimestamp("created_at").toInstant()),
                pageParams.toArray());
        return new AdminDtos.PageResult<>(content, total == null ? 0 : total, page, size);
    }

    /** 变更用户状态（仅 SUPER_ADMIN）；不能停用超级管理员账号。 */
    public AdminDtos.UserItem updateStatus(Long id, AdminDtos.UserStatusRequest request) {
        governance.accounts();
        governance.requireRole("SUPER_ADMIN");
        User.Status target;
        try {
            target = User.Status.valueOf(request.status().trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            throw BizException.badRequest("USER_STATUS_INVALID", "非法的用户状态");
        }
        if (target != User.Status.ACTIVE && target != User.Status.DISABLED) {
            throw BizException.badRequest("USER_STATUS_INVALID", "仅支持 ACTIVE 或 DISABLED");
        }
        jdbcTemplate.queryForList("SELECT id FROM users WHERE id=? FOR UPDATE",Long.class,id);
        User user = userRepository.findById(id)
                .orElseThrow(() -> BizException.notFound("用户不存在"));
        if (target == User.Status.ACTIVE && jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM account_closure_requests WHERE user_id=?",Long.class,id)>0) {
            throw BizException.conflict("ACCOUNT_CLOSED", "已申请注销的账号不能通过普通状态管理恢复");
        }
        if (target == User.Status.DISABLED) governance.protectLastAdmin(id);
        if (target == User.Status.DISABLED
                && userRoleRepository.existsByUserIdAndRole(id, UserRole.Role.SUPER_ADMIN)) {
            throw BizException.forbidden("不能停用超级管理员账号");
        }
        if (user.getStatus() == target) {
            return new AdminDtos.UserItem(user.getId(), user.getEmail(), user.getNickname(),
                    user.getStatus().name(), rolesOf(user.getId()), user.getCreatedAt());
        }
        String before = user.getStatus().name();
        user.setStatus(target);
        user.setUpdatedAt(Instant.now());
        userRepository.save(user);
        adminAuditService.record("USER_STATUS_" + target.name(), "USER", user.getId(),
                "后台变更用户状态", before, target.name());
        notificationService.notify(user.getId(), "USER_STATUS",
                target == User.Status.DISABLED ? "账号已被停用" : "账号已恢复",
                target == User.Status.DISABLED
                        ? "您的账号已被平台停用，如有疑问请联系客服"
                        : "您的账号已恢复正常使用");
        return new AdminDtos.UserItem(user.getId(), user.getEmail(), user.getNickname(),
                user.getStatus().name(), rolesOf(user.getId()), user.getCreatedAt());
    }

    private List<String> rolesOf(Long userId) {
        return userRoleRepository.findByUserId(userId).stream()
                .map(r -> r.getRole().name())
                .toList();
    }
}
