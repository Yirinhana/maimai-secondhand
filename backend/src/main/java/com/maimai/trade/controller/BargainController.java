package com.maimai.trade.controller;

import com.maimai.common.security.SecurityUtils;
import com.maimai.trade.dto.TradeDtos.BargainDto;
import com.maimai.trade.dto.TradeDtos.CounterRequest;
import com.maimai.trade.dto.TradeDtos.CreateBargainRequest;
import com.maimai.trade.dto.TradeDtos.RejectRequest;
import com.maimai.trade.service.BargainService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** 议价端点。 */
@RestController
@RequestMapping("/api/v1")
public class BargainController {

    private final BargainService bargainService;

    public BargainController(BargainService bargainService) {
        this.bargainService = bargainService;
    }

    @PostMapping("/products/{id}/bargains")
    public BargainDto create(@PathVariable Long id, @RequestBody @Valid CreateBargainRequest request) {
        return bargainService.create(SecurityUtils.currentUserId(), id, request.quantity(), request.offerPriceCents());
    }

    @GetMapping("/me/bargains")
    public List<BargainDto> listBuyer() {
        return bargainService.listBuyer(SecurityUtils.currentUserId());
    }

    @GetMapping("/seller/bargains")
    public List<BargainDto> listSeller() {
        return bargainService.listSeller(SecurityUtils.currentUserId());
    }

    @PostMapping("/seller/bargains/{id}/accept")
    public BargainDto accept(@PathVariable Long id) {
        return bargainService.accept(SecurityUtils.currentUserId(), id);
    }

    @PostMapping("/seller/bargains/{id}/counter")
    public BargainDto counter(@PathVariable Long id, @RequestBody @Valid CounterRequest request) {
        return bargainService.counter(SecurityUtils.currentUserId(), id, request.counterPriceCents());
    }

    @PostMapping("/seller/bargains/{id}/reject")
    public BargainDto reject(@PathVariable Long id, @RequestBody(required = false) RejectRequest request) {
        return bargainService.reject(SecurityUtils.currentUserId(), id, request != null ? request.reason() : null);
    }

    @PostMapping("/me/bargains/{id}/confirm")
    public BargainDto confirm(@PathVariable Long id) {
        return bargainService.confirm(SecurityUtils.currentUserId(), id);
    }
}
