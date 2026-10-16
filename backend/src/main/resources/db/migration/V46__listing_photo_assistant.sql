-- Private photo-assistance results. No image bytes or provider credentials are stored here.
CREATE TABLE listing_ai_runs (
  id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
  user_id BIGINT UNSIGNED NOT NULL,
  request_key VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  image_hash CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  image_ids_json TEXT NOT NULL,
  model VARCHAR(80) NOT NULL,
  status VARCHAR(20) NOT NULL,
  result_json TEXT NULL,
  failure_code VARCHAR(40) NULL,
  created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  UNIQUE KEY uk_listing_ai_request(user_id, request_key),
  KEY idx_listing_ai_cache(user_id,image_hash,status,created_at),
  KEY idx_listing_ai_processing(status,created_at),
  CONSTRAINT fk_listing_ai_user FOREIGN KEY(user_id) REFERENCES users(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- scope_id=0 is the shared site budget; positive values are account budgets.
CREATE TABLE listing_ai_daily_usage (
  usage_date DATE NOT NULL,
  scope_id BIGINT UNSIGNED NOT NULL,
  calls INT NOT NULL DEFAULT 0,
  PRIMARY KEY(usage_date,scope_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
