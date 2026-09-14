-- 支付账务：支付请求、渠道通知事件、退款、账目流水
CREATE TABLE payment_requests (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT PRIMARY KEY,
  pay_no VARCHAR(32) NOT NULL,
  order_id BIGINT UNSIGNED NOT NULL,
  amount_cents BIGINT NOT NULL,
  channel VARCHAR(20) NOT NULL,
  status VARCHAR(16) NOT NULL DEFAULT 'CREATED',
  channel_txn_id VARCHAR(64) NULL,
  simulated TINYINT(1) NOT NULL DEFAULT 0,
  created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  paid_at DATETIME(6) NULL,
  UNIQUE KEY uk_pay_no (pay_no),
  KEY idx_pr_order (order_id),
  CONSTRAINT fk_pr_order FOREIGN KEY (order_id) REFERENCES orders(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE payment_notifications (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT PRIMARY KEY,
  channel VARCHAR(20) NOT NULL,
  event_id VARCHAR(64) NOT NULL,
  payload TEXT NULL,
  processed TINYINT(1) NOT NULL DEFAULT 0,
  created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  UNIQUE KEY uk_pn_event (channel, event_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE refunds (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT PRIMARY KEY,
  refund_no VARCHAR(32) NOT NULL,
  order_id BIGINT UNSIGNED NOT NULL,
  aftersale_id BIGINT UNSIGNED NULL,
  goods_refund_cents BIGINT NOT NULL DEFAULT 0,
  freight_refund_cents BIGINT NOT NULL DEFAULT 0,
  platform_fee_refund_cents BIGINT NOT NULL DEFAULT 0,
  status VARCHAR(16) NOT NULL DEFAULT 'REQUESTED',
  channel VARCHAR(20) NOT NULL,
  channel_refund_id VARCHAR(64) NULL,
  simulated TINYINT(1) NOT NULL DEFAULT 0,
  created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
  UNIQUE KEY uk_refund_no (refund_no),
  KEY idx_refunds_order (order_id),
  CONSTRAINT fk_refunds_order FOREIGN KEY (order_id) REFERENCES orders(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE ledger_entries (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT PRIMARY KEY,
  order_id BIGINT UNSIGNED NOT NULL,
  entry_type VARCHAR(30) NOT NULL,
  amount_cents BIGINT NOT NULL,
  ref_type VARCHAR(30) NULL,
  ref_id BIGINT UNSIGNED NULL,
  created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  KEY idx_ledger_order (order_id),
  CONSTRAINT fk_ledger_order FOREIGN KEY (order_id) REFERENCES orders(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
