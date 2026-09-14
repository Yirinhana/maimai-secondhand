package com.maimai.trade.dto;

import com.maimai.trade.domain.Order;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.List;

/** 交易模块请求/响应 DTO。金额一律整数分。 */
public final class TradeDtos {

    private TradeDtos() {
    }

    // ---------- 议价 ----------

    public record CreateBargainRequest(
            @NotNull @Min(1) Integer quantity,
            @NotNull @Positive Long offerPriceCents) {
    }

    public record CounterRequest(@NotNull @Positive Long counterPriceCents) {
    }

    public record RejectRequest(@Size(max = 200) String reason) {
    }

    public record BargainDto(Long id, Long productId, String productTitle, String coverImage,
                             Long sellerId, String sellerNickname, Long buyerId, String buyerNickname,
                             int quantity, long offerPriceCents, Long counterPriceCents,
                             String status, Instant expiresAt, Long usedOrderId, Instant createdAt) {
    }

    // ---------- 购物车 ----------

    public record AddCartItemRequest(
            @NotNull Long productId,
            @NotNull @Min(1) Integer quantity,
            @NotBlank String deliveryMethod) {
    }

    public record UpdateCartItemRequest(@NotNull @Min(1) Integer quantity) {
    }

    public record CartItemDto(Long id, Long productId, String title, long priceCents, int quantity,
                              String deliveryMethod, Integer stockAvailable, String coverImage,
                              Long sellerId, String sellerNickname, String productStatus, boolean invalid) {
    }

    // ---------- 结算 ----------

    public record CheckoutItem(
            @NotNull Long productId,
            @NotNull @Min(1) Integer quantity,
            @NotBlank String deliveryMethod,
            Long bargainId) {
    }

    public record CheckoutRequest(
            @NotBlank @Size(max = 64) String idempotencyKey,
            @NotEmpty @Size(max = 100) List<@jakarta.validation.Valid CheckoutItem> items,
            Long addressId,
            @Size(max = 200) String meetupLocation,
            Instant meetupTime,
            List<Long> removeCartItemIds) {
    }

    public record CheckoutResponse(String batchNo, List<OrderDto> orders) {
    }

    // ---------- 订单 ----------

    public record OrderItemDto(Long productId, String title, String condition, String defects,
                               long priceCents, int quantity, String imagePath) {
    }

    public record OrderDto(Long id, String orderNo, Long buyerId, Long sellerId, String sellerNickname,
                           Order.DeliveryMethod deliveryMethod, List<OrderItemDto> items,
                           long goodsAmountCents, long freightCents, long platformFeeCents, long totalCents,
                           Order.FulfillmentStatus fulfillmentStatus, Order.PayStatus payStatus,
                           Order.RefundStatus refundStatus, Order.SettleStatus settleStatus,
                           String receiver, String phone, String region, String addressDetail,
                           String meetupLocation, Instant meetupTime,
                           Instant expiresAt, Instant paidAt, Instant shippedAt, Instant autoConfirmAt,
                           Instant createdAt, long retainedPlatformFeeCents, Long channelFeeCents,
                           boolean channelFeeConfirmed, Long expectedSellerNetCents, boolean simulated,
                           String allocationStatus) {
    }

    public record PageResponse<T>(List<T> content, long totalElements, int totalPages, int page, int size) {
    }

    // ---------- 履约 ----------

    public record ShipRequest(
            @NotBlank @Size(max = 50) String carrier,
            @NotBlank @Size(max = 64) String trackingNo) {
    }

    public record ShipmentDto(String carrier, String trackingNo, String status,
                              String traces, Instant lastTraceAt, String queryErrorCode, Instant lastQueryAttemptAt) {
    }

    public record MeetupRequest(
            @NotBlank @Size(max = 200) String location,
            @NotNull Instant scheduledAt) {
    }

    // ---------- 交付码 ----------

    public record DeliveryCodeResponse(String code, Instant expiresAt) {
    }

    public record VerifyCodeRequest(@NotBlank @Size(min = 6, max = 6) String code) {
    }
}
