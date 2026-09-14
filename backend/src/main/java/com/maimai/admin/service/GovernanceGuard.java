package com.maimai.admin.service;

import com.maimai.common.BizException;
import com.maimai.common.security.SecurityUtils;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import java.util.Set;

/** Database mutex shared by role changes, account closure and administrator status changes. */
@Service
public class GovernanceGuard {
    private final JdbcTemplate db;
    public GovernanceGuard(JdbcTemplate db) { this.db=db; }
    @Transactional(propagation=Propagation.MANDATORY)
    public void accounts() { db.queryForObject("SELECT lock_name FROM governance_locks WHERE lock_name='ACCOUNT_ROLES' FOR UPDATE",String.class); }
    @Transactional(propagation=Propagation.MANDATORY)
    public void categories() { db.queryForObject("SELECT lock_name FROM governance_locks WHERE lock_name='CATEGORIES' FOR UPDATE",String.class); }
    public void requireRole(String... allowed) {
        long actor=SecurityUtils.currentUserId();
        var roles=db.queryForList("SELECT r.role FROM users u JOIN user_roles r ON r.user_id=u.id WHERE u.id=? AND u.status='ACTIVE'",String.class,actor);
        if(roles.stream().noneMatch(Set.of(allowed)::contains)) throw BizException.forbidden("没有执行该操作的权限");
    }
    public void protectLastAdmin(long target) {
        Long isAdmin=db.queryForObject("SELECT COUNT(*) FROM users u JOIN user_roles r ON r.user_id=u.id WHERE u.id=? AND u.status='ACTIVE' AND r.role='SUPER_ADMIN'",Long.class,target);
        if(isAdmin!=null&&isAdmin>0&&db.queryForObject("SELECT COUNT(*) FROM users u JOIN user_roles r ON r.user_id=u.id WHERE u.status='ACTIVE' AND r.role='SUPER_ADMIN'",Long.class)<=1)
            throw BizException.conflict("LAST_SUPER_ADMIN","必须保留至少一名活跃超级管理员");
    }
}
