CREATE TABLE product_comments (
    id BIGINT UNSIGNED PRIMARY KEY AUTO_INCREMENT,
    product_id BIGINT UNSIGNED NOT NULL,
    author_id BIGINT UNSIGNED NOT NULL,
    reply_to_id BIGINT UNSIGNED NULL,
    content VARCHAR(1000) NOT NULL,
    status VARCHAR(16) NOT NULL DEFAULT 'PUBLISHED',
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    CONSTRAINT fk_product_comments_product FOREIGN KEY(product_id) REFERENCES products(id),
    CONSTRAINT fk_product_comments_author FOREIGN KEY(author_id) REFERENCES users(id),
    CONSTRAINT fk_product_comments_reply FOREIGN KEY(reply_to_id) REFERENCES product_comments(id),
    CONSTRAINT chk_product_comments_status CHECK(status IN ('PUBLISHED','DELETED','HIDDEN')),
    INDEX idx_product_comments_page(product_id,status,created_at,id),
    INDEX idx_product_comments_rate(author_id,created_at)
);

ALTER TABLE direct_messages ADD COLUMN product_id BIGINT UNSIGNED NULL,
    ADD COLUMN product_title VARCHAR(120) NULL,
    ADD COLUMN product_cover VARCHAR(500) NULL,
    ADD COLUMN product_price_cents BIGINT NULL,
    ADD CONSTRAINT fk_direct_messages_product FOREIGN KEY(product_id) REFERENCES products(id);
