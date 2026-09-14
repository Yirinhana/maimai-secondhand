package com.maimai.trade.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "orders")
public class Order {

    public enum FulfillmentStatus {PENDING_PAYMENT, PAID_PENDING_SHIP, SHIPPED, AWAITING_MEETUP, COMPLETED, CLOSED}

    public enum PayStatus {UNPAID, PAYING, PAID, CLOSED}

    public enum RefundStatus {NONE, PARTIAL, FULL}

    public enum SettleStatus {NONE, PENDING, SETTLED}

    public enum DeliveryMethod {EXPRESS, MEETUP}

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "order_no", nullable = false, unique = true, length = 32)
    private String orderNo;

    @Column(name = "batch_id", nullable = false)
    private Long batchId;

    @Column(name = "buyer_id", nullable = false)
    private Long buyerId;

    @Column(name = "seller_id", nullable = false)
    private Long sellerId;

    @Enumerated(EnumType.STRING)
    @Column(name = "delivery_method", nullable = false, length = 10)
    private DeliveryMethod deliveryMethod;

    @Column(length = 50)
    private String receiver;

    @Column(length = 20)
    private String phone;

    @Column(length = 100)
    private String region;

    @Column(name = "address_detail", length = 200)
    private String addressDetail;

    @Column(name = "goods_amount_cents", nullable = false)
    private long goodsAmountCents;

    @Column(name = "freight_cents", nullable = false)
    private long freightCents = 0;

    @Column(name = "platform_fee_cents", nullable = false)
    private long platformFeeCents = 0;

    @Column(name = "total_cents", nullable = false)
    private long totalCents;

    @Enumerated(EnumType.STRING)
    @Column(name = "fulfillment_status", nullable = false, length = 24)
    private FulfillmentStatus fulfillmentStatus = FulfillmentStatus.PENDING_PAYMENT;

    @Enumerated(EnumType.STRING)
    @Column(name = "pay_status", nullable = false, length = 16)
    private PayStatus payStatus = PayStatus.UNPAID;

    @Enumerated(EnumType.STRING)
    @Column(name = "refund_status", nullable = false, length = 16)
    private RefundStatus refundStatus = RefundStatus.NONE;

    @Enumerated(EnumType.STRING)
    @Column(name = "settle_status", nullable = false, length = 16)
    private SettleStatus settleStatus = SettleStatus.NONE;

    @Column(name = "bargain_id")
    private Long bargainId;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "paid_at")
    private Instant paidAt;

    @Column(name = "ship_deadline")
    private Instant shipDeadline;

    @Column(name = "shipped_at")
    private Instant shippedAt;

    @Column(name = "auto_confirm_at")
    private Instant autoConfirmAt;

    @Column(name = "confirm_paused", nullable = false)
    private boolean confirmPaused = false;

    @Column(name = "completed_at")
    private Instant completedAt;

    @Column(name = "closed_at")
    private Instant closedAt;

    @Column(name = "close_reason", length = 200)
    private String closeReason;

    @Column(nullable = false)
    private long version = 0;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getOrderNo() { return orderNo; }
    public void setOrderNo(String orderNo) { this.orderNo = orderNo; }
    public Long getBatchId() { return batchId; }
    public void setBatchId(Long batchId) { this.batchId = batchId; }
    public Long getBuyerId() { return buyerId; }
    public void setBuyerId(Long buyerId) { this.buyerId = buyerId; }
    public Long getSellerId() { return sellerId; }
    public void setSellerId(Long sellerId) { this.sellerId = sellerId; }
    public DeliveryMethod getDeliveryMethod() { return deliveryMethod; }
    public void setDeliveryMethod(DeliveryMethod deliveryMethod) { this.deliveryMethod = deliveryMethod; }
    public String getReceiver() { return receiver; }
    public void setReceiver(String receiver) { this.receiver = receiver; }
    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; }
    public String getRegion() { return region; }
    public void setRegion(String region) { this.region = region; }
    public String getAddressDetail() { return addressDetail; }
    public void setAddressDetail(String addressDetail) { this.addressDetail = addressDetail; }
    public long getGoodsAmountCents() { return goodsAmountCents; }
    public void setGoodsAmountCents(long goodsAmountCents) { this.goodsAmountCents = goodsAmountCents; }
    public long getFreightCents() { return freightCents; }
    public void setFreightCents(long freightCents) { this.freightCents = freightCents; }
    public long getPlatformFeeCents() { return platformFeeCents; }
    public void setPlatformFeeCents(long platformFeeCents) { this.platformFeeCents = platformFeeCents; }
    public long getTotalCents() { return totalCents; }
    public void setTotalCents(long totalCents) { this.totalCents = totalCents; }
    public FulfillmentStatus getFulfillmentStatus() { return fulfillmentStatus; }
    public void setFulfillmentStatus(FulfillmentStatus fulfillmentStatus) { this.fulfillmentStatus = fulfillmentStatus; }
    public PayStatus getPayStatus() { return payStatus; }
    public void setPayStatus(PayStatus payStatus) { this.payStatus = payStatus; }
    public RefundStatus getRefundStatus() { return refundStatus; }
    public void setRefundStatus(RefundStatus refundStatus) { this.refundStatus = refundStatus; }
    public SettleStatus getSettleStatus() { return settleStatus; }
    public void setSettleStatus(SettleStatus settleStatus) { this.settleStatus = settleStatus; }
    public Long getBargainId() { return bargainId; }
    public void setBargainId(Long bargainId) { this.bargainId = bargainId; }
    public Instant getExpiresAt() { return expiresAt; }
    public void setExpiresAt(Instant expiresAt) { this.expiresAt = expiresAt; }
    public Instant getPaidAt() { return paidAt; }
    public void setPaidAt(Instant paidAt) { this.paidAt = paidAt; }
    public Instant getShipDeadline() { return shipDeadline; }
    public void setShipDeadline(Instant shipDeadline) { this.shipDeadline = shipDeadline; }
    public Instant getShippedAt() { return shippedAt; }
    public void setShippedAt(Instant shippedAt) { this.shippedAt = shippedAt; }
    public Instant getAutoConfirmAt() { return autoConfirmAt; }
    public void setAutoConfirmAt(Instant autoConfirmAt) { this.autoConfirmAt = autoConfirmAt; }
    public boolean isConfirmPaused() { return confirmPaused; }
    public void setConfirmPaused(boolean confirmPaused) { this.confirmPaused = confirmPaused; }
    public Instant getCompletedAt() { return completedAt; }
    public void setCompletedAt(Instant completedAt) { this.completedAt = completedAt; }
    public Instant getClosedAt() { return closedAt; }
    public void setClosedAt(Instant closedAt) { this.closedAt = closedAt; }
    public String getCloseReason() { return closeReason; }
    public void setCloseReason(String closeReason) { this.closeReason = closeReason; }
    public long getVersion() { return version; }
    public void setVersion(long version) { this.version = version; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
