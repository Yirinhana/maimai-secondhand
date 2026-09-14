package com.maimai.catalog.admin;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.List;

/** 后台商品审核 DTO。 */
public final class AdminProductDtos {

    private AdminProductDtos() {
    }

    public record ReviewRequest(@NotNull Boolean approve,
                                @Size(max = 500) String reason) {
    }

    public record AdminProductItem(long id,
                                   String title,
                                   long sellerId,
                                   String sellerNickname,
                                   long categoryId,
                                   long priceCents,
                                   String condition,
                                   String region,
                                   String status,
                                   String reviewReason,
                                   String coverImage,
                                   List<String> deliveryMethods,
                                   Instant createdAt,
                                   Instant updatedAt) {
    }
}
