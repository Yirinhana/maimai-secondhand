package com.maimai.admin.controller;

import com.maimai.admin.dto.AdminDtos;
import com.maimai.admin.service.AdminStatsService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 后台平台总览统计。 */
@RestController
@RequestMapping("/api/v1/admin/stats")
public class AdminStatsController {

    private final AdminStatsService adminStatsService;

    public AdminStatsController(AdminStatsService adminStatsService) {
        this.adminStatsService = adminStatsService;
    }

    @GetMapping("/overview")
    public AdminDtos.StatsOverview overview() {
        return adminStatsService.overview();
    }
}
