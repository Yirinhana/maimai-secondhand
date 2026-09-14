package com.maimai.aftersales.dto;

import com.maimai.aftersales.domain.Aftersale;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.List;

/** 售后模块请求/响应 DTO。金额一律整数分。 */
public final class AftersaleDtos {

    private AftersaleDtos() {
    }

    /** 买家发起售后申请。 */
    public record CreateAftersaleRequest(@NotNull Aftersale.Type type,
                                         @NotBlank @Size(max = 500) String reason,
                                         @NotNull @Min(0) Long goodsAmountCents,
                                         @NotNull @Min(0) Long freightAmountCents,
                                         @Size(max = 2000) String evidence) {
    }

    /** 卖家响应售后：agree=false 时 reply 必填。 */
    public record RespondRequest(@NotNull Boolean agree, @Size(max = 500) String reply,
                                 @Size(max = 50) String returnRecipient,
                                 @Size(max = 30) String returnPhone,
                                 @Size(max = 500) String returnAddress) {
        public RespondRequest(Boolean agree, String reply) { this(agree, reply, null, null, null); }
    }

    /** 买家填写退货物流。 */
    public record ReturnShipRequest(@NotBlank @Size(max = 50) String carrier,
                                    @NotBlank @Size(max = 64) String trackingNo) {
    }

    /** 售后单概要（列表项）。 */
    public record AftersaleSummary(Long id, String aftersaleNo, Long orderId, String orderNo,
                                   String type, long goodsAmountCents, long freightAmountCents,
                                   String status, Instant sellerDeadline, Instant returnDeadline,
                                   Instant createdAt) {
    }

    /** 售后单详情（含处理日志）。 */
    public record AftersaleDetail(Long id, String aftersaleNo, Long orderId, String orderNo,
                                  Long buyerId, Long sellerId, String type, String reason,
                                  long goodsAmountCents, long freightAmountCents, String status,
                                  String evidence, String sellerReply,
                                  Instant sellerDeadline, Instant returnDeadline,
                                  String returnCarrier, String returnTrackingNo,
                                  Instant createdAt, Instant updatedAt,
                                  List<AftersaleLogItem> logs,
                                  String returnRecipient, String returnPhone, String returnAddress,
                                  Instant returnShippedAt, Instant returnReceivedAt,
                                  Instant returnInspectionDeadline) {
    }

    /** 售后处理日志项。 */
    public record AftersaleLogItem(Long id, Long actorId, String actorRole, String action,
                                   String note, Instant createdAt) {
    }

    /** 通用分页结果。 */
    public record PageResult<T>(List<T> content, long total, int page, int size) {
    }
}
