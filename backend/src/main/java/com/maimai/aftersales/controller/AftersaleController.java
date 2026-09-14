package com.maimai.aftersales.controller;

import com.maimai.aftersales.dto.AftersaleDtos;
import com.maimai.aftersales.service.AftersaleService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 买家侧售后端点与售后详情。 */
@RestController
@RequestMapping("/api/v1")
public class AftersaleController {

    private final AftersaleService aftersaleService;

    public AftersaleController(AftersaleService aftersaleService) {
        this.aftersaleService = aftersaleService;
    }

    @PostMapping("/orders/{orderNo}/aftersales")
    public AftersaleDtos.AftersaleDetail create(@PathVariable String orderNo,
                                                @RequestBody @Valid AftersaleDtos.CreateAftersaleRequest request) {
        return aftersaleService.create(orderNo, request);
    }

    @GetMapping("/me/aftersales")
    public AftersaleDtos.PageResult<AftersaleDtos.AftersaleSummary> listMine(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return aftersaleService.listMine(page, Math.min(size, 100));
    }

    @PostMapping("/me/aftersales/{id}/return-ship")
    public AftersaleDtos.AftersaleDetail returnShip(@PathVariable Long id,
                                                    @RequestBody @Valid AftersaleDtos.ReturnShipRequest request) {
        return aftersaleService.returnShip(id, request);
    }

    @GetMapping("/aftersales/{id}")
    public AftersaleDtos.AftersaleDetail detail(@PathVariable Long id) {
        return aftersaleService.detail(id);
    }

    @PostMapping("/me/aftersales/{id}/escalate")
    public AftersaleDtos.AftersaleDetail escalate(@PathVariable Long id) {
        return aftersaleService.escalate(id);
    }
}
