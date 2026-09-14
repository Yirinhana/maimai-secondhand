package com.maimai.community.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.List;

/** 社区模块请求/响应模型。 */
public final class CommunityDtos {

    private CommunityDtos() {
    }

    public record PageResult<T>(List<T> items, long total, int page, int size, int totalPages) {
    }

    public record FavoriteItem(long id,
                              long productId,
                              String productTitle,
                              long productPriceCents,
                              String productStatus,
                              boolean isPublicVisible,
                              Instant collectedAt) {
    }

    public record FollowItem(long id,
                             long sellerId,
                             String sellerNickname,
                             Instant followedAt) {
    }

    public record FootprintItem(long id,
                               long productId,
                               String productTitle,
                               long productPriceCents,
                               String productStatus,
                               Instant viewedAt) {
    }

    public record FootprintSettingRequest(@NotNull Boolean enabled) {
    }

    public record ModerationDecisionRequest(@NotNull Boolean approve, @Size(max = 500) String reason) {
    }

    public record DemandCreateRequest(@NotBlank @Size(max = 120) String title,
                                     @NotBlank @Size(max = 800) String description,
                                     @NotNull @Min(0) Long budgetMinCents,
                                     @NotNull @Min(0) Long budgetMaxCents,
                                     Long categoryId,
                                     @Size(max = 100) String region) {
    }

    public record DemandPatchRequest(@Size(max = 120) String title,
                                    @Size(max = 800) String description,
                                    @Min(0) Long budgetMinCents,
                                    @Min(0) Long budgetMaxCents,
                                    Long categoryId,
                                    @Size(max = 100) String region) {
    }

    public record DemandItem(long id,
                            long authorId,
                            String authorNickname,
                            String title,
                            String description,
                            long budgetMinCents,
                            long budgetMaxCents,
                            Long categoryId,
                            String region,
                            String status,
                            boolean isClosed,
                            Instant createdAt,
                            Instant updatedAt,
                            Long reviewedBy,
                            Instant reviewedAt,
                            String reviewReason) {
    }

    public record DemandReplyRequest(@NotBlank @Size(max = 800) String content) {
    }

    public record DemandReplyItem(long id,
                                 long demandId,
                                 long authorId,
                                 String authorNickname,
                                 String content,
                                 String status,
                                 Instant createdAt,
                                 Instant updatedAt,
                                 Long reviewedBy,
                                 Instant reviewedAt,
                                 String reviewReason) {
    }

    public record ReportCreateRequest(@NotBlank @Size(max = 40) String resourceType,
                                     @NotNull Long resourceId,
                                     @NotBlank @Size(min = 6, max = 500) String reason) {
    }

    public record ReportProcessRequest(@NotBlank @Size(max = 40) String action,
                                      @Size(max = 500) String note) {
    }

    public record ReportItem(long id,
                            long reporterId,
                            String resourceType,
                            long resourceId,
                            String reason,
                            String status,
                            Long processorId,
                            String processAction,
                            String processNote,
                            Instant createdAt,
                            Instant resolvedAt) {
    }

    public record RatingRequest(@NotNull @Min(1) @Max(5) Integer rating,
                               @Size(max = 500) String comment) {
    }

    public record RatingItem(long id,
                            long orderId,
                            long reviewerId,
                            long rateeId,
                            String reviewerNickname,
                            int rating,
                            String comment,
                            String refundStatus,
                            Instant createdAt) {
    }

    public record PublicRatingItem(long id, long reviewerId, long rateeId, String reviewerNickname,
                                   int rating, String comment, String refundStatus, Instant createdAt) {
    }

    public enum ResourceType {
        PRODUCT,
        PRODUCT_COMMENT,
        DEMAND_POST,
        DEMAND_REPLY,
        ORDER_REVIEW
    }

    public enum ReportAction {
        KEEP,
        REJECT,
        HIDE,
        FORWARD_TO_PRODUCT
    }
}
