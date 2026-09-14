ALTER TABLE products
  ADD COLUMN shipping_provinces VARCHAR(500) NOT NULL DEFAULT '',
  ADD COLUMN latitude DECIMAL(9,6) NULL,
  ADD COLUMN longitude DECIMAL(10,6) NULL,
  ADD CONSTRAINT ck_products_coordinates CHECK (
    (latitude IS NULL AND longitude IS NULL) OR
    (latitude IS NOT NULL AND longitude IS NOT NULL AND latitude BETWEEN -90 AND 90 AND longitude BETWEEN -180 AND 180)
  );

CREATE TABLE product_revisions (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT PRIMARY KEY,
  product_id BIGINT UNSIGNED NOT NULL,
  version INT NOT NULL,
  action VARCHAR(40) NOT NULL,
  actor_id BIGINT UNSIGNED NULL,
  content JSON NOT NULL,
  created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  UNIQUE KEY uk_product_revision(product_id,version),
  CONSTRAINT fk_revision_product FOREIGN KEY(product_id) REFERENCES products(id),
  CONSTRAINT fk_revision_actor FOREIGN KEY(actor_id) REFERENCES users(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
