-- 交易履约：议价、购物车、结算批次、卖家子订单、订单项快照、快递、面交与交付码
CREATE TABLE bargain_offers (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT PRIMARY KEY,
  product_id BIGINT UNSIGNED NOT NULL,
  buyer_id BIGINT UNSIGNED NOT NULL,
  quantity INT NOT NULL,
  offer_price_cents BIGINT NOT NULL,
  counter_price_cents BIGINT NULL,
  status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
  expires_at DATETIME(6) NOT NULL,
  used_order_id BIGINT UNSIGNED NULL,
  created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
  KEY idx_bo_product_buyer (product_id, buyer_id),
  KEY idx_bo_buyer (buyer_id),
  CONSTRAINT fk_bo_product FOREIGN KEY (product_id) REFERENCES products(id),
  CONSTRAINT fk_bo_buyer FOREIGN KEY (buyer_id) REFERENCES users(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE cart_items (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT PRIMARY KEY,
  user_id BIGINT UNSIGNED NOT NULL,
  product_id BIGINT UNSIGNED NOT NULL,
  quantity INT NOT NULL,
  delivery_method VARCHAR(10) NOT NULL,
  created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
  UNIQUE KEY uk_cart (user_id, product_id, delivery_method),
  CONSTRAINT fk_cart_user FOREIGN KEY (user_id) REFERENCES users(id),
  CONSTRAINT fk_cart_product FOREIGN KEY (product_id) REFERENCES products(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE checkout_batches (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT PRIMARY KEY,
  batch_no VARCHAR(32) NOT NULL,
  user_id BIGINT UNSIGNED NOT NULL,
  status VARCHAR(20) NOT NULL DEFAULT 'CREATED',
  idempotency_key VARCHAR(64) NOT NULL,
  created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  UNIQUE KEY uk_batch_no (batch_no),
  UNIQUE KEY uk_batch_idem (user_id, idempotency_key)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE orders (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT PRIMARY KEY,
  order_no VARCHAR(32) NOT NULL,
  batch_id BIGINT UNSIGNED NOT NULL,
  buyer_id BIGINT UNSIGNED NOT NULL,
  seller_id BIGINT UNSIGNED NOT NULL,
  delivery_method VARCHAR(10) NOT NULL,
  receiver VARCHAR(50) NULL,
  phone VARCHAR(20) NULL,
  region VARCHAR(100) NULL,
  address_detail VARCHAR(200) NULL,
  goods_amount_cents BIGINT NOT NULL,
  freight_cents BIGINT NOT NULL DEFAULT 0,
  platform_fee_cents BIGINT NOT NULL DEFAULT 0,
  total_cents BIGINT NOT NULL,
  fulfillment_status VARCHAR(24) NOT NULL DEFAULT 'PENDING_PAYMENT',
  pay_status VARCHAR(16) NOT NULL DEFAULT 'UNPAID',
  refund_status VARCHAR(16) NOT NULL DEFAULT 'NONE',
  settle_status VARCHAR(16) NOT NULL DEFAULT 'NONE',
  bargain_id BIGINT UNSIGNED NULL,
  expires_at DATETIME(6) NOT NULL,
  paid_at DATETIME(6) NULL,
  ship_deadline DATETIME(6) NULL,
  shipped_at DATETIME(6) NULL,
  auto_confirm_at DATETIME(6) NULL,
  confirm_paused TINYINT(1) NOT NULL DEFAULT 0,
  completed_at DATETIME(6) NULL,
  closed_at DATETIME(6) NULL,
  close_reason VARCHAR(200) NULL,
  version BIGINT NOT NULL DEFAULT 0,
  created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
  UNIQUE KEY uk_order_no (order_no),
  KEY idx_orders_buyer (buyer_id, created_at),
  KEY idx_orders_seller (seller_id, created_at),
  KEY idx_orders_pending_exp (fulfillment_status, expires_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE order_items (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT PRIMARY KEY,
  order_id BIGINT UNSIGNED NOT NULL,
  product_id BIGINT UNSIGNED NOT NULL,
  title VARCHAR(120) NOT NULL,
  item_condition VARCHAR(20) NOT NULL,
  defects VARCHAR(500) NULL,
  price_cents BIGINT NOT NULL,
  quantity INT NOT NULL,
  freight_cents BIGINT NOT NULL DEFAULT 0,
  return_promise VARCHAR(200) NULL,
  image_path VARCHAR(255) NULL,
  KEY idx_oi_order (order_id),
  CONSTRAINT fk_oi_order FOREIGN KEY (order_id) REFERENCES orders(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE shipments (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT PRIMARY KEY,
  order_id BIGINT UNSIGNED NOT NULL,
  carrier VARCHAR(50) NOT NULL,
  tracking_no VARCHAR(64) NOT NULL,
  status VARCHAR(20) NOT NULL DEFAULT 'IN_TRANSIT',
  traces TEXT NULL,
  last_trace_at DATETIME(6) NULL,
  created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
  UNIQUE KEY uk_shipments_order (order_id),
  CONSTRAINT fk_shipments_order FOREIGN KEY (order_id) REFERENCES orders(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE meetup_appointments (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT PRIMARY KEY,
  order_id BIGINT UNSIGNED NOT NULL,
  location VARCHAR(200) NOT NULL,
  scheduled_at DATETIME(6) NOT NULL,
  status VARCHAR(20) NOT NULL DEFAULT 'ARRANGED',
  created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
  KEY idx_ma_order (order_id),
  CONSTRAINT fk_ma_order FOREIGN KEY (order_id) REFERENCES orders(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE delivery_codes (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT PRIMARY KEY,
  order_id BIGINT UNSIGNED NOT NULL,
  code_hash CHAR(64) NOT NULL,
  expires_at DATETIME(6) NOT NULL,
  attempts INT NOT NULL DEFAULT 0,
  verified_at DATETIME(6) NULL,
  invalidated TINYINT(1) NOT NULL DEFAULT 0,
  created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  KEY idx_dc_order (order_id),
  CONSTRAINT fk_dc_order FOREIGN KEY (order_id) REFERENCES orders(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
