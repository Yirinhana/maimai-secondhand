package com.maimai.admin.controller;

import com.maimai.admin.domain.AdminAuditLog;
import com.maimai.admin.dto.AdminDtos;
import com.maimai.admin.repo.AdminAuditLogRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** 后台审计日志查询。 */
@RestController
@RequestMapping("/api/v1/admin/audit-logs")
public class AdminAuditLogController {

    private final AdminAuditLogRepository adminAuditLogRepository;

    public AdminAuditLogController(AdminAuditLogRepository adminAuditLogRepository) {
        this.adminAuditLogRepository = adminAuditLogRepository;
    }

    @GetMapping
    public AdminDtos.PageResult<AdminDtos.AuditLogItem> list(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        int capped = Math.min(size, 100);
        List<AdminDtos.AuditLogItem> content = adminAuditLogRepository
                .findAllByOrderByCreatedAtDesc(PageRequest.of(page, capped)).stream()
                .map(l -> new AdminDtos.AuditLogItem(l.getId(), l.getAdminId(), l.getAction(),
                        l.getTargetType(), l.getTargetId(), l.getReason(),
                        l.getBeforeState(), l.getAfterState(), l.getCreatedAt()))
                .toList();
        return new AdminDtos.PageResult<>(content, adminAuditLogRepository.count(), page, capped);
    }
}
