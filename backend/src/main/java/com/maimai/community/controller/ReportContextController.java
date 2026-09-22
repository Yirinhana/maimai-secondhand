package com.maimai.community.controller;

import com.maimai.community.service.ReportContextService;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/community/admin/reports")
public class ReportContextController {
    private final ReportContextService service;
    public ReportContextController(ReportContextService service){this.service=service;}
    @GetMapping("/{id}/context") public ReportContextService.Context detail(@PathVariable long id){return service.detail(id);}
    @PostMapping("/{id}/ai-assessment") public ReportContextService.Context assess(@PathVariable long id){return service.assess(id);}
}
