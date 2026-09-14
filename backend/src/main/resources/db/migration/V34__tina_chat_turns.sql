CREATE TABLE support_ai_turns (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT PRIMARY KEY,
  owner_id BIGINT UNSIGNED NOT NULL,
  request_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  attempt_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  question VARCHAR(1000) NOT NULL,
  answer VARCHAR(1800) NULL,
  status VARCHAR(16) NOT NULL,
  error_code VARCHAR(50) NULL,
  created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  UNIQUE KEY uk_ai_owner_request (owner_id, request_id),
  KEY idx_ai_owner_id (owner_id, id),
  CONSTRAINT chk_ai_turn_status CHECK (status IN ('PENDING','COMPLETE','FAILED')),
  CONSTRAINT fk_ai_turn_owner FOREIGN KEY (owner_id) REFERENCES users(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
