-- Internal expected allocation only; no provider settlement or personal platform wallet.
ALTER TABLE orders ADD COLUMN channel_fee_cents BIGINT NULL;
ALTER TABLE orders ADD COLUMN channel_fee_confirmed TINYINT(1) NOT NULL DEFAULT 0;

UPDATE orders o SET o.channel_fee_cents=0, o.channel_fee_confirmed=1
WHERE EXISTS(SELECT 1 FROM payment_requests p WHERE p.order_id=o.id
             AND p.channel='MOCK_LOCAL' AND p.status='PAID' AND p.simulated=1);

CREATE TABLE finance_allocation_expectations (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT PRIMARY KEY,
  order_id BIGINT UNSIGNED NOT NULL,
  goods_remaining_cents BIGINT NOT NULL,
  freight_remaining_cents BIGINT NOT NULL,
  platform_expected_cents BIGINT NOT NULL,
  seller_expected_cents BIGINT NULL,
  channel_fee_cents BIGINT NULL,
  channel_fee_confirmed TINYINT(1) NOT NULL DEFAULT 0,
  simulated TINYINT(1) NOT NULL DEFAULT 0,
  status VARCHAR(32) NOT NULL DEFAULT 'WAITING_CHANNEL',
  updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  UNIQUE KEY uk_fae_order(order_id),
  CONSTRAINT fk_fae_order FOREIGN KEY(order_id) REFERENCES orders(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE trade_reminder_events (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT PRIMARY KEY,
  order_id BIGINT UNSIGNED NOT NULL,
  event_type VARCHAR(32) NOT NULL,
  status VARCHAR(16) NOT NULL DEFAULT 'OPEN',
  reason VARCHAR(300) NOT NULL,
  created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  resolved_at DATETIME(6) NULL,
  UNIQUE KEY uk_tre_order_type(order_id,event_type),
  KEY idx_tre_open(status,event_type,created_at),
  CONSTRAINT fk_tre_order FOREIGN KEY(order_id) REFERENCES orders(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
