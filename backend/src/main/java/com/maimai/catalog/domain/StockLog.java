package com.maimai.catalog.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "stock_logs")
public class StockLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "product_id", nullable = false)
    private Long productId;

    @Column(name = "delta_available", nullable = false)
    private int deltaAvailable = 0;

    @Column(name = "delta_reserved", nullable = false)
    private int deltaReserved = 0;

    @Column(name = "delta_sold", nullable = false)
    private int deltaSold = 0;

    @Column(nullable = false, length = 30)
    private String reason;

    @Column(name = "ref_type", length = 30)
    private String refType;

    @Column(name = "ref_id")
    private Long refId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getProductId() { return productId; }
    public void setProductId(Long productId) { this.productId = productId; }
    public int getDeltaAvailable() { return deltaAvailable; }
    public void setDeltaAvailable(int deltaAvailable) { this.deltaAvailable = deltaAvailable; }
    public int getDeltaReserved() { return deltaReserved; }
    public void setDeltaReserved(int deltaReserved) { this.deltaReserved = deltaReserved; }
    public int getDeltaSold() { return deltaSold; }
    public void setDeltaSold(int deltaSold) { this.deltaSold = deltaSold; }
    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }
    public String getRefType() { return refType; }
    public void setRefType(String refType) { this.refType = refType; }
    public Long getRefId() { return refId; }
    public void setRefId(Long refId) { this.refId = refId; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}
