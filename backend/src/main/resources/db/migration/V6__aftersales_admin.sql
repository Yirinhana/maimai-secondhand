-- 售后、站内通知与后台审计
CREATE TABLE aftersales (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT PRIMARY KEY,
  aftersale_no VARCHAR(32) NOT NULL,
  order_id BIGINT UNSIGNED NOT NULL,
  buyer_id BIGINT UNSIGNED NOT NULL,
  type VARCHAR(20) NOT NULL,
  reason VARCHAR(500) NOT NULL,
  goods_amount_cents BIGINT NOT NULL DEFAULT 0,
  freight_amount_cents BIGINT NOT NULL DEFAULT 0,
  status VARCHAR(24) NOT NULL DEFAULT 'PENDING_SELLER',
  evidence TEXT NULL,
  seller_reply VARCHAR(500) NULL,
  seller_deadline DATETIME(6) NULL,
  return_deadline DATETIME(6) NULL,
  return_carrier VARCHAR(50) NULL,
  return_tracking_no VARCHAR(64) NULL,
  created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
  UNIQUE KEY uk_aftersale_no (aftersale_no),
  KEY idx_as_order (order_id),
  KEY idx_as_buyer (buyer_id),
  CONSTRAINT fk_as_order FOREIGN KEY (order_id) REFERENCES orders(id),
  CONSTRAINT fk_as_buyer FOREIGN KEY (buyer_id) REFERENCES users(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE aftersale_logs (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT PRIMARY KEY,
  aftersale_id BIGINT UNSIGNED NOT NULL,
  actor_id BIGINT UNSIGNED NULL,
  actor_role VARCHAR(20) NULL,
  action VARCHAR(40) NOT NULL,
  note VARCHAR(500) NULL,
  created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  KEY idx_asl_aftersale (aftersale_id),
  CONSTRAINT fk_asl_aftersale FOREIGN KEY (aftersale_id) REFERENCES aftersales(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE notifications (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT PRIMARY KEY,
  user_id BIGINT UNSIGNED NOT NULL,
  type VARCHAR(30) NOT NULL,
  title VARCHAR(120) NOT NULL,
  content VARCHAR(1000) NOT NULL,
  is_read TINYINT(1) NOT NULL DEFAULT 0,
  created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  KEY idx_ntf_user (user_id, is_read),
  CONSTRAINT fk_ntf_user FOREIGN KEY (user_id) REFERENCES users(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE admin_audit_logs (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT PRIMARY KEY,
  admin_id BIGINT UNSIGNED NOT NULL,
  action VARCHAR(50) NOT NULL,
  target_type VARCHAR(30) NOT NULL,
  target_id BIGINT UNSIGNED NULL,
  reason VARCHAR(500) NULL,
  before_state VARCHAR(500) NULL,
  after_state VARCHAR(500) NULL,
  created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  KEY idx_aal_admin (admin_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
