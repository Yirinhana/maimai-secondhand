package com.maimai.trade.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "order_items")
public class OrderItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "order_id", nullable = false)
    private Long orderId;

    @Column(name = "product_id", nullable = false)
    private Long productId;

    @Column(nullable = false, length = 120)
    private String title;

    @Column(name = "item_condition", nullable = false, length = 20)
    private String itemCondition;

    @Column(length = 500)
    private String defects;

    @Column(name = "price_cents", nullable = false)
    private long priceCents;

    @Column(nullable = false)
    private int quantity;

    @Column(name = "freight_cents", nullable = false)
    private long freightCents = 0;

    @Column(name = "return_promise", length = 200)
    private String returnPromise;

    @Column(name = "image_path", length = 255)
    private String imagePath;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getOrderId() { return orderId; }
    public void setOrderId(Long orderId) { this.orderId = orderId; }
    public Long getProductId() { return productId; }
    public void setProductId(Long productId) { this.productId = productId; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getItemCondition() { return itemCondition; }
    public void setItemCondition(String itemCondition) { this.itemCondition = itemCondition; }
    public String getDefects() { return defects; }
    public void setDefects(String defects) { this.defects = defects; }
    public long getPriceCents() { return priceCents; }
    public void setPriceCents(long priceCents) { this.priceCents = priceCents; }
    public int getQuantity() { return quantity; }
    public void setQuantity(int quantity) { this.quantity = quantity; }
    public long getFreightCents() { return freightCents; }
    public void setFreightCents(long freightCents) { this.freightCents = freightCents; }
    public String getReturnPromise() { return returnPromise; }
    public void setReturnPromise(String returnPromise) { this.returnPromise = returnPromise; }
    public String getImagePath() { return imagePath; }
    public void setImagePath(String imagePath) { this.imagePath = imagePath; }
}
