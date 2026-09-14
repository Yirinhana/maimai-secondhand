package com.maimai.admin.controller;

import com.maimai.admin.dto.AdminDtos;
import com.maimai.admin.service.AdminUserService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 后台用户管理（状态变更仅 SUPER_ADMIN）。 */
@RestController
@RequestMapping("/api/v1/admin/users")
public class AdminUserController {

    private final AdminUserService adminUserService;

    public AdminUserController(AdminUserService adminUserService) {
        this.adminUserService = adminUserService;
    }

    @GetMapping
    public AdminDtos.PageResult<AdminDtos.UserItem> list(
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return adminUserService.list(keyword, page, Math.min(size, 100));
    }

    @PostMapping("/{id}/status")
    public AdminDtos.UserItem updateStatus(@PathVariable Long id,
                                           @RequestBody @Valid AdminDtos.UserStatusRequest request) {
        return adminUserService.updateStatus(id, request);
    }
}
