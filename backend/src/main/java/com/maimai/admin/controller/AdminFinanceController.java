package com.maimai.admin.controller;

import com.maimai.payment.finance.*;
import com.maimai.admin.service.AdminAuditService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.time.LocalDate;

@RestController
@RequestMapping("/api/v1/admin/finance")
@PreAuthorize("hasRole('SUPER_ADMIN')")
public class AdminFinanceController {
    private final FinanceService finance;
    private final AdminAuditService audits;
    public AdminFinanceController(FinanceService finance,AdminAuditService audits){this.finance=finance;this.audits=audits;}
    @GetMapping("/orders")
    public FinanceDtos.Page list(@RequestParam(required=false) @DateTimeFormat(iso=DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required=false) @DateTimeFormat(iso=DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="20") int size) {
        return finance.list(from,to,page,size);
    }
    @GetMapping("/orders/{orderId}")
    public FinanceDtos.Detail detail(@PathVariable long orderId){return finance.detail(orderId);}
    @GetMapping(value="/export.csv",produces="text/csv;charset=UTF-8")
    public ResponseEntity<String> export(@RequestParam(required=false) @DateTimeFormat(iso=DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required=false) @DateTimeFormat(iso=DateTimeFormat.ISO.DATE) LocalDate to) {
        String csv=finance.csv(from,to);
        audits.record("FINANCE_EXPORT","FINANCE",null,"导出内部流水（非渠道对账），日期 "+from+" 至 "+to,null,null);
        return ResponseEntity.ok().header(HttpHeaders.CONTENT_DISPOSITION,"attachment; filename=maimai-finance.csv")
                .header(HttpHeaders.CACHE_CONTROL,"no-store").body(csv);
    }
}
