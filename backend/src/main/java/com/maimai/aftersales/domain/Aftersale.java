package com.maimai.aftersales.domain;

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
@Table(name = "aftersales")
public class Aftersale {

    public enum Type {REFUND_ONLY, RETURN_REFUND}

    public enum Status {PENDING_SELLER, SELLER_REJECTED, PENDING_RETURN, RETURN_SHIPPED, PENDING_MANUAL, RESOLVED, CLOSED}

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "aftersale_no", nullable = false, unique = true, length = 32)
    private String aftersaleNo;

    @Column(name = "order_id", nullable = false)
    private Long orderId;

    @Column(name = "buyer_id", nullable = false)
    private Long buyerId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Type type;

    @Column(nullable = false, length = 500)
    private String reason;

    @Column(name = "goods_amount_cents", nullable = false)
    private long goodsAmountCents = 0;

    @Column(name = "freight_amount_cents", nullable = false)
    private long freightAmountCents = 0;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 24)
    private Status status = Status.PENDING_SELLER;

    @Column(columnDefinition = "TEXT")
    private String evidence;

    @Column(name = "seller_reply", length = 500)
    private String sellerReply;

    @Column(name = "seller_deadline")
    private Instant sellerDeadline;

    @Column(name = "return_deadline")
    private Instant returnDeadline;

    @Column(name = "return_carrier", length = 50)
    private String returnCarrier;

    @Column(name = "return_tracking_no", length = 64)
    private String returnTrackingNo;

    @Column(name = "return_recipient", length = 50)
    private String returnRecipient;
    @Column(name = "return_phone", length = 30)
    private String returnPhone;
    @Column(name = "return_address", length = 500)
    private String returnAddress;
    @Column(name = "return_shipped_at")
    private Instant returnShippedAt;
    @Column(name = "return_received_at")
    private Instant returnReceivedAt;
    @Column(name = "return_inspection_deadline")
    private Instant returnInspectionDeadline;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getAftersaleNo() { return aftersaleNo; }
    public void setAftersaleNo(String aftersaleNo) { this.aftersaleNo = aftersaleNo; }
    public Long getOrderId() { return orderId; }
    public void setOrderId(Long orderId) { this.orderId = orderId; }
    public Long getBuyerId() { return buyerId; }
    public void setBuyerId(Long buyerId) { this.buyerId = buyerId; }
    public Type getType() { return type; }
    public void setType(Type type) { this.type = type; }
    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }
    public long getGoodsAmountCents() { return goodsAmountCents; }
    public void setGoodsAmountCents(long goodsAmountCents) { this.goodsAmountCents = goodsAmountCents; }
    public long getFreightAmountCents() { return freightAmountCents; }
    public void setFreightAmountCents(long freightAmountCents) { this.freightAmountCents = freightAmountCents; }
    public Status getStatus() { return status; }
    public void setStatus(Status status) { this.status = status; }
    public String getEvidence() { return evidence; }
    public void setEvidence(String evidence) { this.evidence = evidence; }
    public String getSellerReply() { return sellerReply; }
    public void setSellerReply(String sellerReply) { this.sellerReply = sellerReply; }
    public Instant getSellerDeadline() { return sellerDeadline; }
    public void setSellerDeadline(Instant sellerDeadline) { this.sellerDeadline = sellerDeadline; }
    public Instant getReturnDeadline() { return returnDeadline; }
    public void setReturnDeadline(Instant returnDeadline) { this.returnDeadline = returnDeadline; }
    public String getReturnCarrier() { return returnCarrier; }
    public void setReturnCarrier(String returnCarrier) { this.returnCarrier = returnCarrier; }
    public String getReturnTrackingNo() { return returnTrackingNo; }
    public void setReturnTrackingNo(String returnTrackingNo) { this.returnTrackingNo = returnTrackingNo; }
    public String getReturnRecipient() { return returnRecipient; }
    public void setReturnRecipient(String value) { returnRecipient = value; }
    public String getReturnPhone() { return returnPhone; }
    public void setReturnPhone(String value) { returnPhone = value; }
    public String getReturnAddress() { return returnAddress; }
    public void setReturnAddress(String value) { returnAddress = value; }
    public Instant getReturnShippedAt() { return returnShippedAt; }
    public void setReturnShippedAt(Instant value) { returnShippedAt = value; }
    public Instant getReturnReceivedAt() { return returnReceivedAt; }
    public void setReturnReceivedAt(Instant value) { returnReceivedAt = value; }
    public Instant getReturnInspectionDeadline() { return returnInspectionDeadline; }
    public void setReturnInspectionDeadline(Instant value) { returnInspectionDeadline = value; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
