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

/** 卖家侧售后端点。 */
@RestController
@RequestMapping("/api/v1/seller/aftersales")
public class SellerAftersaleController {

    private final AftersaleService aftersaleService;

    public SellerAftersaleController(AftersaleService aftersaleService) {
        this.aftersaleService = aftersaleService;
    }

    @GetMapping
    public AftersaleDtos.PageResult<AftersaleDtos.AftersaleSummary> listSeller(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return aftersaleService.listSeller(page, Math.min(size, 100));
    }

    @PostMapping("/{id}/respond")
    public AftersaleDtos.AftersaleDetail respond(@PathVariable Long id,
                                                 @RequestBody @Valid AftersaleDtos.RespondRequest request) {
        return aftersaleService.respond(id, request);
    }

    @PostMapping("/{id}/confirm-return")
    public AftersaleDtos.AftersaleDetail confirmReturn(@PathVariable Long id) {
        return aftersaleService.confirmReturn(id);
    }

    @PostMapping("/{id}/receive-return")
    public AftersaleDtos.AftersaleDetail receiveReturn(@PathVariable Long id) {
        return aftersaleService.receiveReturn(id);
    }
}
