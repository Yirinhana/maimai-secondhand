package com.maimai.identity.controller;

import com.maimai.common.security.AuthenticatedUser;
import com.maimai.common.security.SecurityUtils;
import com.maimai.identity.domain.User;
import com.maimai.identity.dto.AuthDtos.UserSummary;
import com.maimai.identity.dto.MeDtos.AddressRequest;
import com.maimai.identity.dto.MeDtos.AddressView;
import com.maimai.identity.dto.MeDtos.SellerApplicationRequest;
import com.maimai.identity.dto.MeDtos.SellerApplicationView;
import com.maimai.identity.dto.MeDtos.UpdateProfileRequest;
import com.maimai.identity.service.AuthService;
import com.maimai.identity.service.MeService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** 个人中心端点：资料、收货地址、卖家申请。均需登录（anyRequest().authenticated()）。 */
@RestController
@RequestMapping("/api/v1/me")
public class MeController {

    private final MeService meService;
    private final AuthService authService;

    public MeController(MeService meService, AuthService authService) {
        this.meService = meService;
        this.authService = authService;
    }

    @PutMapping
    public UserSummary updateProfile(@Valid @RequestBody UpdateProfileRequest request) {
        AuthenticatedUser current = SecurityUtils.current();
        User user = meService.updateProfile(current.id(), request.nickname());
        // 刷新会话主体中的昵称，避免本次会话内读到旧值
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        AuthenticatedUser refreshed = new AuthenticatedUser(current.id(), current.email(),
                user.getNickname(), current.roles());
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                refreshed, null, authentication.getAuthorities()));
        return authService.summaryOf(current.id());
    }

    @GetMapping("/addresses")
    public List<AddressView> listAddresses() {
        return meService.listAddresses(SecurityUtils.currentUserId());
    }

    @PostMapping("/addresses")
    public AddressView addAddress(@Valid @RequestBody AddressRequest request) {
        return meService.addAddress(SecurityUtils.currentUserId(), request);
    }

    @PutMapping("/addresses/{id}")
    public AddressView updateAddress(@PathVariable Long id,
                                     @Valid @RequestBody AddressRequest request) {
        return meService.updateAddress(SecurityUtils.currentUserId(), id, request);
    }

    @DeleteMapping("/addresses/{id}")
    public ResponseEntity<Void> deleteAddress(@PathVariable Long id) {
        meService.deleteAddress(SecurityUtils.currentUserId(), id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/seller-application")
    public SellerApplicationView applySeller(@Valid @RequestBody SellerApplicationRequest request) {
        return meService.applySeller(SecurityUtils.currentUserId(), request);
    }

    @GetMapping("/seller-application")
    public SellerApplicationView latestApplication() {
        return meService.latestApplication(SecurityUtils.currentUserId());
    }
}
