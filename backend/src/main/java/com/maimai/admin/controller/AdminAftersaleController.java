package com.maimai.admin.controller;

import com.maimai.admin.dto.AdminDtos;
import com.maimai.admin.service.AdminAftersaleService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 后台人工售后处理（REFUND/REJECT 须 SUPER_ADMIN 或 SUPPORT）。 */
@RestController
@RequestMapping("/api/v1/admin/aftersales")
@org.springframework.security.access.prepost.PreAuthorize("hasAnyRole('SUPER_ADMIN','SUPPORT')")
public class AdminAftersaleController {

    private final AdminAftersaleService adminAftersaleService;

    public AdminAftersaleController(AdminAftersaleService adminAftersaleService) {
        this.adminAftersaleService = adminAftersaleService;
    }

    @GetMapping
    public AdminDtos.PageResult<AdminDtos.AdminAftersaleItem> list(
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return adminAftersaleService.list(status, page, Math.min(size, 100));
    }

    @PostMapping("/{id}/resolve")
    public AdminDtos.AdminAftersaleItem resolve(@PathVariable Long id,
                                                @RequestBody @Valid AdminDtos.ResolveAftersaleRequest request) {
        return adminAftersaleService.resolve(id, request);
    }
}
