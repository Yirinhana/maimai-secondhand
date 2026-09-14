package com.maimai.admin.service;

import com.maimai.admin.domain.AdminAuditLog;
import com.maimai.admin.repo.AdminAuditLogRepository;
import com.maimai.common.security.SecurityUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** 后台审计：所有写操作记录操作者、动作、对象、理由与前后状态。 */
@Service
public class AdminAuditService {

    private final AdminAuditLogRepository adminAuditLogRepository;

    public AdminAuditService(AdminAuditLogRepository adminAuditLogRepository) {
        this.adminAuditLogRepository = adminAuditLogRepository;
    }

    @Transactional(propagation = Propagation.REQUIRED)
    public void record(String action, String targetType, Long targetId,
                       String reason, String beforeState, String afterState) {
        AdminAuditLog log = new AdminAuditLog();
        log.setAdminId(SecurityUtils.currentUserId());
        log.setAction(action);
        log.setTargetType(targetType);
        log.setTargetId(targetId);
        log.setReason(reason);
        log.setBeforeState(beforeState);
        log.setAfterState(afterState);
        adminAuditLogRepository.save(log);
    }
}
