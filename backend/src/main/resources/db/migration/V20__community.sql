-- 社区增强：收藏/关注/足迹、求购/回复、举报、评价、模块内审核日志
CREATE TABLE community_favorites (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT PRIMARY KEY,
  user_id BIGINT UNSIGNED NOT NULL,
  product_id BIGINT UNSIGNED NOT NULL,
  created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  UNIQUE KEY uk_cf_user_product (user_id, product_id),
  KEY idx_cf_user (user_id),
  CONSTRAINT fk_cf_user FOREIGN KEY (user_id) REFERENCES users(id),
  CONSTRAINT fk_cf_product FOREIGN KEY (product_id) REFERENCES products(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE community_seller_follows (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT PRIMARY KEY,
  follower_id BIGINT UNSIGNED NOT NULL,
  seller_id BIGINT UNSIGNED NOT NULL,
  created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  UNIQUE KEY uk_seller_follow (follower_id, seller_id),
  KEY idx_follows_follower (follower_id),
  KEY idx_follows_seller (seller_id),
  CONSTRAINT fk_cfollower_user FOREIGN KEY (follower_id) REFERENCES users(id),
  CONSTRAINT fk_seller_user FOREIGN KEY (seller_id) REFERENCES users(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE community_footprint_preferences (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT PRIMARY KEY,
  user_id BIGINT UNSIGNED NOT NULL,
  enabled TINYINT(1) NOT NULL DEFAULT 1,
  created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
  UNIQUE KEY uk_footprint_pref (user_id),
  CONSTRAINT fk_fpref_user FOREIGN KEY (user_id) REFERENCES users(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE community_product_footprints (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT PRIMARY KEY,
  user_id BIGINT UNSIGNED NOT NULL,
  product_id BIGINT UNSIGNED NOT NULL,
  viewed_at DATETIME(6) NOT NULL,
  UNIQUE KEY uk_footprint_user_product (user_id, product_id),
  KEY idx_footprint_user (user_id),
  KEY idx_footprint_product (product_id),
  CONSTRAINT fk_pf_user FOREIGN KEY (user_id) REFERENCES users(id),
  CONSTRAINT fk_pf_product FOREIGN KEY (product_id) REFERENCES products(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE community_demand_posts (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT PRIMARY KEY,
  author_id BIGINT UNSIGNED NOT NULL,
  title VARCHAR(120) NOT NULL,
  description TEXT NOT NULL,
  budget_min_cents BIGINT NOT NULL,
  budget_max_cents BIGINT NOT NULL,
  category_id BIGINT UNSIGNED NULL,
  region VARCHAR(100) NULL,
  status VARCHAR(24) NOT NULL DEFAULT 'PENDING',
  is_closed TINYINT(1) NOT NULL DEFAULT 0,
  is_deleted TINYINT(1) NOT NULL DEFAULT 0,
  reviewed_by BIGINT UNSIGNED NULL,
  reviewed_at DATETIME(6) NULL,
  review_reason VARCHAR(500) NULL,
  created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
  KEY idx_demand_author (author_id),
  KEY idx_demand_status (status),
  CONSTRAINT fk_demand_author FOREIGN KEY (author_id) REFERENCES users(id),
  CONSTRAINT fk_demand_category FOREIGN KEY (category_id) REFERENCES categories(id),
  CONSTRAINT fk_demand_reviewer FOREIGN KEY (reviewed_by) REFERENCES users(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE community_demand_replies (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT PRIMARY KEY,
  demand_id BIGINT UNSIGNED NOT NULL,
  author_id BIGINT UNSIGNED NOT NULL,
  content VARCHAR(800) NOT NULL,
  status VARCHAR(24) NOT NULL DEFAULT 'PENDING',
  reviewed_by BIGINT UNSIGNED NULL,
  reviewed_at DATETIME(6) NULL,
  review_reason VARCHAR(500) NULL,
  is_deleted TINYINT(1) NOT NULL DEFAULT 0,
  created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
  KEY idx_reply_demand (demand_id),
  KEY idx_reply_author (author_id),
  CONSTRAINT fk_reply_demand FOREIGN KEY (demand_id) REFERENCES community_demand_posts(id),
  CONSTRAINT fk_reply_author FOREIGN KEY (author_id) REFERENCES users(id),
  CONSTRAINT fk_reply_reviewer FOREIGN KEY (reviewed_by) REFERENCES users(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE community_reports (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT PRIMARY KEY,
  reporter_id BIGINT UNSIGNED NOT NULL,
  resource_type VARCHAR(24) NOT NULL,
  resource_id BIGINT UNSIGNED NOT NULL,
  reason VARCHAR(500) NOT NULL,
  status VARCHAR(24) NOT NULL DEFAULT 'PENDING',
  processed_by BIGINT UNSIGNED NULL,
  process_action VARCHAR(32) NULL,
  process_note VARCHAR(500) NULL,
  created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  resolved_at DATETIME(6) NULL,
  KEY idx_report_status (status),
  KEY idx_report_reporter (reporter_id),
  CONSTRAINT fk_report_reporter FOREIGN KEY (reporter_id) REFERENCES users(id),
  CONSTRAINT fk_report_processor FOREIGN KEY (processed_by) REFERENCES users(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE community_order_ratings (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT PRIMARY KEY,
  order_id BIGINT UNSIGNED NOT NULL,
  rater_id BIGINT UNSIGNED NOT NULL,
  rating TINYINT UNSIGNED NOT NULL,
  comment VARCHAR(500) NULL,
  created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  UNIQUE KEY uk_order_rater (order_id, rater_id),
  KEY idx_rating_rater (rater_id),
  KEY idx_rating_order (order_id),
  CONSTRAINT fk_rating_order FOREIGN KEY (order_id) REFERENCES orders(id),
  CONSTRAINT fk_rating_rater FOREIGN KEY (rater_id) REFERENCES users(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE community_action_logs (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT PRIMARY KEY,
  actor_id BIGINT UNSIGNED NOT NULL,
  actor_role VARCHAR(20) NOT NULL,
  action VARCHAR(40) NOT NULL,
  target_type VARCHAR(30) NOT NULL,
  target_id BIGINT UNSIGNED NOT NULL,
  before_state VARCHAR(500) NULL,
  after_state VARCHAR(500) NULL,
  note VARCHAR(500) NULL,
  created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  KEY idx_cal_actor (actor_id),
  KEY idx_cal_target (target_type, target_id),
  CONSTRAINT fk_action_actor FOREIGN KEY (actor_id) REFERENCES users(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
