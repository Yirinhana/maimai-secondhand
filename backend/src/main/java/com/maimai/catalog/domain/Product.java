package com.maimai.catalog.domain;

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
@Table(name = "products")
public class Product {

    public enum Condition {NEW, LIKE_NEW, GOOD, FAIR, POOR}

    public enum Status {DRAFT, PENDING_REVIEW, ON_SALE, CHANGES_REVIEW, REJECTED, OFF_SHELF}

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "seller_id", nullable = false)
    private Long sellerId;

    @Column(name = "category_id", nullable = false)
    private Long categoryId;

    @Column(nullable = false, length = 120)
    private String title;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(name = "item_condition", nullable = false, length = 20)
    private Condition itemCondition;

    @Column(length = 500)
    private String defects;

    @Column(name = "price_cents", nullable = false)
    private long priceCents;

    @Column(name = "stock_available", nullable = false)
    private int stockAvailable = 0;

    @Column(name = "stock_reserved", nullable = false)
    private int stockReserved = 0;

    @Column(name = "stock_sold", nullable = false)
    private int stockSold = 0;

    @Column(nullable = false, length = 100)
    private String region;

    @Column(name="shipping_provinces",nullable=false,length=500)
    private String shippingProvinces="";
    @Column(precision=9,scale=6)
    private java.math.BigDecimal latitude;
    @Column(precision=10,scale=6)
    private java.math.BigDecimal longitude;

    @Column(name = "delivery_methods", nullable = false, length = 50)
    private String deliveryMethods;

    @Column(name = "freight_cents", nullable = false)
    private long freightCents = 0;

    @Column(name = "return_promise", length = 200)
    private String returnPromise;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Status status = Status.DRAFT;

    @Column(name = "review_reason", length = 500)
    private String reviewReason;

    @Column(nullable = false)
    private long version = 0;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getSellerId() { return sellerId; }
    public void setSellerId(Long sellerId) { this.sellerId = sellerId; }
    public Long getCategoryId() { return categoryId; }
    public void setCategoryId(Long categoryId) { this.categoryId = categoryId; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public Condition getItemCondition() { return itemCondition; }
    public void setItemCondition(Condition itemCondition) { this.itemCondition = itemCondition; }
    public String getDefects() { return defects; }
    public void setDefects(String defects) { this.defects = defects; }
    public long getPriceCents() { return priceCents; }
    public void setPriceCents(long priceCents) { this.priceCents = priceCents; }
    public int getStockAvailable() { return stockAvailable; }
    public void setStockAvailable(int stockAvailable) { this.stockAvailable = stockAvailable; }
    public int getStockReserved() { return stockReserved; }
    public void setStockReserved(int stockReserved) { this.stockReserved = stockReserved; }
    public int getStockSold() { return stockSold; }
    public void setStockSold(int stockSold) { this.stockSold = stockSold; }
    public String getRegion() { return region; }
    public String getShippingProvinces(){return shippingProvinces;}
    public void setShippingProvinces(String value){this.shippingProvinces=value;}
    public java.math.BigDecimal getLatitude(){return latitude;}
    public void setLatitude(java.math.BigDecimal value){this.latitude=value;}
    public java.math.BigDecimal getLongitude(){return longitude;}
    public void setLongitude(java.math.BigDecimal value){this.longitude=value;}
    public void setRegion(String region) { this.region = region; }
    public String getDeliveryMethods() { return deliveryMethods; }
    public void setDeliveryMethods(String deliveryMethods) { this.deliveryMethods = deliveryMethods; }
    public long getFreightCents() { return freightCents; }
    public void setFreightCents(long freightCents) { this.freightCents = freightCents; }
    public String getReturnPromise() { return returnPromise; }
    public void setReturnPromise(String returnPromise) { this.returnPromise = returnPromise; }
    public Status getStatus() { return status; }
    public void setStatus(Status status) { this.status = status; }
    public String getReviewReason() { return reviewReason; }
    public void setReviewReason(String reviewReason) { this.reviewReason = reviewReason; }
    public long getVersion() { return version; }
    public void setVersion(long version) { this.version = version; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
