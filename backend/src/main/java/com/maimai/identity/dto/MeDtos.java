package com.maimai.identity.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.Instant;

/** 个人中心（资料/地址/卖家申请）端点的请求/响应 DTO。 */
public final class MeDtos {

    private MeDtos() {
    }

    public record UpdateProfileRequest(
            @NotBlank @Size(max = 50) String nickname) {
    }

    public record AddressRequest(
            @NotBlank @Size(max = 50) String receiver,
            @NotBlank @Size(max = 20) String phone,
            @NotBlank @Size(max = 100) String region,
            @NotBlank @Size(max = 200) String detail,
            boolean isDefault) {
    }

    public record AddressView(Long id, String receiver, String phone, String region, String detail,
                              boolean isDefault, Instant createdAt) {
    }

    public record SellerApplicationRequest(
            @Size(max = 500) String intro) {
    }

    public record SellerApplicationView(Long id, String status, String channelStatus, String intro,
                                        String reason, Instant createdAt) {
    }
}
