package com.maimai.admin.service;

import com.maimai.admin.dto.AdminDtos.UserItem;
import com.maimai.admin.dto.GovernanceDtos.RoleChange;
import com.maimai.common.BizException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.Set;

@Service
@Transactional
public class AdminRoleService {
    private final JdbcTemplate db;
    private final GovernanceGuard guard;
    private final AdminAuditService audit;
    public AdminRoleService(JdbcTemplate db,GovernanceGuard guard,AdminAuditService audit) {this.db=db;this.guard=guard;this.audit=audit;}
    public UserItem change(long id,RoleChange request) {
        guard.accounts();guard.requireRole("SUPER_ADMIN");
        if(request==null||request.role()==null||!Set.of("OPERATOR","SUPPORT","SUPER_ADMIN").contains(request.role())||request.grant()==null)
            throw BizException.badRequest("ROLE_INVALID","仅能管理运营、客服及超级管理员角色");
        if(request.reason()==null||request.reason().isBlank()||request.reason().length()>500) throw BizException.badRequest("REASON_REQUIRED","请填写500字以内的操作原因");
        var statuses=db.queryForList("SELECT status FROM users WHERE id=? FOR UPDATE",String.class,id);
        if(statuses.isEmpty()) throw BizException.notFound("用户不存在");
        if(request.grant()&&!"ACTIVE".equals(statuses.getFirst())) throw BizException.conflict("USER_INACTIVE","停用账号不能获得后台角色");
        var before=db.queryForList("SELECT role FROM user_roles WHERE user_id=? ORDER BY role",String.class,id);
        if(request.grant()!=before.contains(request.role())) {
            if(!request.grant()&&"SUPER_ADMIN".equals(request.role())) guard.protectLastAdmin(id);
            if(request.grant()) db.update("INSERT INTO user_roles(user_id,role) VALUES (?,?)",id,request.role());
            else db.update("DELETE FROM user_roles WHERE user_id=? AND role=?",id,request.role());
            audit.record(request.grant()?"ROLE_GRANT":"ROLE_REVOKE","USER",id,request.reason().trim(),String.join(",",before),String.join(",",roles(id)));
        }
        return db.queryForObject("SELECT id,email,nickname,status,created_at FROM users WHERE id=?",(rs,n)->new UserItem(id,rs.getString("email"),rs.getString("nickname"),rs.getString("status"),roles(id),rs.getTimestamp("created_at").toInstant()),id);
    }
    private java.util.List<String> roles(long id) {return db.queryForList("SELECT role FROM user_roles WHERE user_id=? ORDER BY role",String.class,id);}
}
