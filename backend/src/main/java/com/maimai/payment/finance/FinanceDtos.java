package com.maimai.payment.finance;

import java.time.Instant;
import java.util.List;

public final class FinanceDtos {
    private FinanceDtos() {}
    public record Money(long retainedPlatformFeeCents, Long channelFeeCents, boolean channelFeeConfirmed,
                        Long expectedSellerNetCents, boolean simulated, String allocationStatus) {}
    public record Summary(long orderId, String orderNo, long buyerId, long sellerId,
            long goodsAmountCents, long freightCents, long goodsRefundedCents, long freightRefundedCents,
            long originalPlatformFeeCents, long retainedPlatformFeeCents, long platformFeeRefundedCents,
            long platformFeeRefundDueCents, Long channelFeeCents, boolean channelFeeConfirmed,
            Long expectedSellerNetCents, boolean simulated, String channel, String payStatus,
            String refundStatus, String fulfillmentStatus, String allocationStatus, Instant createdAt, Instant paidAt) {}
    public record Ledger(long id, String entryType, long amountCents, String referenceType, Long referenceId, Instant createdAt) {}
    public record Detail(Summary order, List<Ledger> ledger, String reconciliationStatus, String notice) {}
    public record Page(List<Summary> content, long total, int page, int size) {}
}
