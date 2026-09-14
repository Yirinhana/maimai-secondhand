CREATE TABLE aftersale_images (
    id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin PRIMARY KEY,
    aftersale_id BIGINT UNSIGNED NOT NULL,
    uploaded_by BIGINT UNSIGNED NOT NULL,
    storage_name VARCHAR(80) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    byte_size INT NOT NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    KEY idx_aftersale_images_case (aftersale_id, created_at),
    CONSTRAINT fk_aftersale_images_case FOREIGN KEY (aftersale_id) REFERENCES aftersales(id),
    CONSTRAINT fk_aftersale_images_user FOREIGN KEY (uploaded_by) REFERENCES users(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
