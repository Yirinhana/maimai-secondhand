package com.maimai.payment.domain;

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
@Table(name = "refunds")
public class Refund {

    public enum Status {REQUESTED, PROCESSING, SUCCESS, FAILED}

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "refund_no", nullable = false, unique = true, length = 32)
    private String refundNo;

    @Column(name = "order_id", nullable = false)
    private Long orderId;

    @Column(name = "aftersale_id")
    private Long aftersaleId;

    @Column(name = "goods_refund_cents", nullable = false)
    private long goodsRefundCents = 0;

    @Column(name = "freight_refund_cents", nullable = false)
    private long freightRefundCents = 0;

    @Column(name = "platform_fee_refund_cents", nullable = false)
    private long platformFeeRefundCents = 0;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private Status status = Status.REQUESTED;

    @Column(nullable = false, length = 20)
    private String channel;

    @Column(name = "channel_refund_id", length = 64)
    private String channelRefundId;

    @Column(nullable = false)
    private boolean simulated = false;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getRefundNo() { return refundNo; }
    public void setRefundNo(String refundNo) { this.refundNo = refundNo; }
    public Long getOrderId() { return orderId; }
    public void setOrderId(Long orderId) { this.orderId = orderId; }
    public Long getAftersaleId() { return aftersaleId; }
    public void setAftersaleId(Long aftersaleId) { this.aftersaleId = aftersaleId; }
    public long getGoodsRefundCents() { return goodsRefundCents; }
    public void setGoodsRefundCents(long goodsRefundCents) { this.goodsRefundCents = goodsRefundCents; }
    public long getFreightRefundCents() { return freightRefundCents; }
    public void setFreightRefundCents(long freightRefundCents) { this.freightRefundCents = freightRefundCents; }
    public long getPlatformFeeRefundCents() { return platformFeeRefundCents; }
    public void setPlatformFeeRefundCents(long platformFeeRefundCents) { this.platformFeeRefundCents = platformFeeRefundCents; }
    public Status getStatus() { return status; }
    public void setStatus(Status status) { this.status = status; }
    public String getChannel() { return channel; }
    public void setChannel(String channel) { this.channel = channel; }
    public String getChannelRefundId() { return channelRefundId; }
    public void setChannelRefundId(String channelRefundId) { this.channelRefundId = channelRefundId; }
    public boolean isSimulated() { return simulated; }
    public void setSimulated(boolean simulated) { this.simulated = simulated; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
