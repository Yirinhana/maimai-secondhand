package com.maimai.admin.controller;

import com.maimai.admin.dto.AdminDtos;
import com.maimai.admin.service.AdminSellerApplicationService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 后台卖家申请审核（SecurityConfig 已限定 OPERATOR/SUPPORT/SUPER_ADMIN）。 */
@RestController
@RequestMapping("/api/v1/admin/seller-applications")
@org.springframework.security.access.prepost.PreAuthorize("hasAnyRole('SUPER_ADMIN','OPERATOR')")
public class AdminSellerApplicationController {

    private final AdminSellerApplicationService adminSellerApplicationService;

    public AdminSellerApplicationController(AdminSellerApplicationService adminSellerApplicationService) {
        this.adminSellerApplicationService = adminSellerApplicationService;
    }

    @GetMapping
    public AdminDtos.PageResult<AdminDtos.SellerApplicationItem> list(
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return adminSellerApplicationService.list(status, page, Math.min(size, 100));
    }

    @PostMapping("/{id}/review")
    public AdminDtos.SellerApplicationItem review(@PathVariable Long id,
                                                  @RequestBody @Valid AdminDtos.ReviewApplicationRequest request) {
        return adminSellerApplicationService.review(id, request);
    }
}
