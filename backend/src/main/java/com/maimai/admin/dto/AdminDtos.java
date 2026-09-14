package com.maimai.admin.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.List;

/** 后台模块请求/响应 DTO。金额一律整数分。 */
public final class AdminDtos {

    private AdminDtos() {
    }

    /** 通用分页结果。 */
    public record PageResult<T>(List<T> content, long total, int page, int size) {
    }

    /** 卖家申请审核：action ∈ APPROVE|REJECT|SUPPLEMENT|SUSPEND；channelQualified 仅 APPROVE 有意义。 */
    public record ReviewApplicationRequest(@NotBlank String action,
                                           @NotBlank @Size(max = 500) String reason,
                                           Boolean channelQualified) {
    }

    /** 卖家申请列表项。 */
    public record SellerApplicationItem(Long id, Long userId, String email, String nickname,
                                        String status, String channelStatus, String intro, String reason,
                                        Long reviewedBy, Instant reviewedAt, Instant createdAt) {
    }

    /** 用户状态变更：status ∈ ACTIVE|DISABLED（仅 SUPER_ADMIN）。 */
    public record UserStatusRequest(@NotBlank String status) {
    }

    /** 用户列表项。 */
    public record UserItem(Long id, String email, String nickname, String status,
                           List<String> roles, Instant createdAt) {
    }

    /** 人工售后处理：action ∈ REFUND|REJECT；金额缺省用售后申请值。 */
    public record ResolveAftersaleRequest(@NotBlank String action,
                                          @NotBlank @Size(max = 500) String note,
                                          @Min(0) Long goodsAmountCents,
                                          @Min(0) Long freightAmountCents) {
    }

    /** 后台售后列表项。 */
    public record AdminAftersaleItem(Long id, String aftersaleNo, Long orderId, String orderNo,
                                     Long buyerId, String type, String reason,
                                     long goodsAmountCents, long freightAmountCents,
                                     String status, Instant createdAt) {
    }

    /** 平台总览统计。 */
    public record StatsOverview(long userCount, long productOnSaleCount, long orderCount,
                                long paidOrderCount, long refundSuccessCount, long platformFeeSumCents) {
    }

    /** 审计日志项。 */
    public record AuditLogItem(Long id, Long adminId, String action, String targetType, Long targetId,
                               String reason, String beforeState, String afterState, Instant createdAt) {
    }
}
