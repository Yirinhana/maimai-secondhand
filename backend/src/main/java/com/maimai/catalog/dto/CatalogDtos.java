package com.maimai.catalog.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.data.domain.Page;

import java.time.Instant;
import java.util.List;

/** catalog 模块请求/响应 DTO。金额一律整数分。 */
public final class CatalogDtos {

    private CatalogDtos() {
    }

    /** 统一分页响应：与 api-v1 契约 {content,totalElements,totalPages,page,size} 一致。 */
    public record PageResult<T>(List<T> content, long totalElements, int totalPages, int page, int size) {

        public static <T> PageResult<T> from(Page<T> p) {
            return new PageResult<>(p.getContent(), p.getTotalElements(), p.getTotalPages(), p.getNumber(), p.getSize());
        }
    }

    public record CategoryNode(long id, String name, List<CategoryNode> children) {
    }

    public record ProductSummary(long id,
                                 String title,
                                 long priceCents,
                                 String condition,
                                 String region,
                                 String coverImage,
                                 int stockAvailable,
                                 long sellerId,
                                 String sellerNickname,
                                 long freightCents,
                                 List<String> deliveryMethods, Double distanceMeters) {
    }

    public record ImageItem(long id, String path, int sort) {
    }

    public record SellerBrief(long id, String nickname, String avatarUrl) {
    }

    public record ProductDetail(long id,
                                String title,
                                String description,
                                String condition,
                                String defects,
                                long priceCents,
                                int stockAvailable,
                                String region,
                                List<String> deliveryMethods,
                                long freightCents,
                                String returnPromise,
                                String status,
                                List<ImageItem> images,
                                SellerBrief seller,
                                Instant createdAt, List<String> shippingProvinces,
                                java.math.BigDecimal latitude, java.math.BigDecimal longitude,
                                String experienceSource, String supplyNote) {
    }

    public record SellerProfile(long id, String nickname, Instant joinedAt, long onSaleCount, String avatarUrl) {
    }

    public record SellerProductDetail(long id, long categoryId, String title, String description,
            String condition, String defects, long priceCents, int stockAvailable, int stockReserved,
            int stockSold, String region, List<String> deliveryMethods, long freightCents,
            String returnPromise, String status, String reviewReason, List<ImageItem> images,
            List<String> shippingProvinces,java.math.BigDecimal latitude,java.math.BigDecimal longitude) {}

    /** 卖家“我的商品”列表项：含全部状态与审核原因。 */
    public record SellerProductItem(long id,
                                    String title,
                                    long priceCents,
                                    String condition,
                                    String region,
                                    String coverImage,
                                    int stockAvailable,
                                    int stockReserved,
                                    int stockSold,
                                    String status,
                                    String reviewReason,
                                    long freightCents,
                                    List<String> deliveryMethods,
                                    Instant createdAt,
                                    Instant updatedAt) {
    }

    public record ProductCreateRequest(@NotBlank @Size(max = 120) String title,
                                       @NotNull Long categoryId,
                                       String description,
                                       @NotNull String condition,
                                       @Size(max = 500) String defects,
                                       @NotNull @Min(1) Long priceCents,
                                       @NotNull @Min(0) Integer stock,
                                       @NotBlank @Size(max = 100) String region,
                                       @NotEmpty List<String> deliveryMethods,
                                       @NotNull @Min(0) Long freightCents,
                                       @Size(max = 200) String returnPromise,
                                       Boolean submit, List<String> shippingProvinces,
                                       java.math.BigDecimal latitude,java.math.BigDecimal longitude) {
    }

    /** 修改商品：不含 stock/submit；库存调整必须走 /stock 端点。 */
    public record ProductUpdateRequest(@NotBlank @Size(max = 120) String title,
                                       @NotNull Long categoryId,
                                       String description,
                                       @NotNull String condition,
                                       @Size(max = 500) String defects,
                                       @NotNull @Min(1) Long priceCents,
                                       Integer stock,
                                       @NotBlank @Size(max = 100) String region,
                                       @NotEmpty List<String> deliveryMethods,
                                       @NotNull @Min(0) Long freightCents,
                                       @Size(max = 200) String returnPromise, List<String> shippingProvinces,
                                       java.math.BigDecimal latitude,java.math.BigDecimal longitude) {
    }

    public record StockAdjustRequest(@NotNull Integer delta) {
    }
}
