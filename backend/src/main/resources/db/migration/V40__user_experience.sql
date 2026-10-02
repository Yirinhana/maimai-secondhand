ALTER TABLE notifications ADD COLUMN target_path VARCHAR(240) NULL;
ALTER TABLE products ADD COLUMN specifications TEXT NULL;
ALTER TABLE product_images ADD COLUMN source_media_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NULL,
  ADD UNIQUE KEY uk_product_source_media(product_id,source_media_id);

CREATE TABLE listing_drafts (
  user_id BIGINT UNSIGNED NOT NULL,
  draft_key VARCHAR(32) NOT NULL,
  payload MEDIUMTEXT NOT NULL,
  version BIGINT NOT NULL DEFAULT 1,
  updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  PRIMARY KEY(user_id,draft_key),
  CONSTRAINT fk_listing_draft_user FOREIGN KEY(user_id) REFERENCES users(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE personal_media (
  id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin PRIMARY KEY,
  owner_id BIGINT UNSIGNED NOT NULL,
  purpose VARCHAR(16) NOT NULL,
  filename VARCHAR(40) NOT NULL,
  created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  KEY idx_media_owner(owner_id,purpose),
  CONSTRAINT fk_personal_media_owner FOREIGN KEY(owner_id) REFERENCES users(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

ALTER TABLE community_order_ratings
  ADD COLUMN description_rating TINYINT NULL,
  ADD COLUMN communication_rating TINYINT NULL,
  ADD COLUMN fulfillment_rating TINYINT NULL,
  ADD COLUMN followup VARCHAR(500) NULL,
  ADD COLUMN followed_up_at DATETIME(6) NULL;
CREATE TABLE rating_images (
  rating_id BIGINT UNSIGNED NOT NULL,
  media_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  sort INT NOT NULL,
  PRIMARY KEY(rating_id,media_id),
  UNIQUE KEY uk_rating_media(media_id),
  CONSTRAINT fk_rating_image_review FOREIGN KEY(rating_id) REFERENCES community_order_ratings(id) ON DELETE CASCADE,
  CONSTRAINT fk_rating_image_media FOREIGN KEY(media_id) REFERENCES personal_media(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE search_subscriptions (
  id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
  user_id BIGINT UNSIGNED NOT NULL,
  name VARCHAR(80) NOT NULL,
  keyword VARCHAR(120) NOT NULL DEFAULT '',
  category_id BIGINT UNSIGNED NULL,
  min_price_cents BIGINT NULL,
  max_price_cents BIGINT NULL,
  region VARCHAR(100) NOT NULL DEFAULT '',
  item_condition VARCHAR(20) NOT NULL DEFAULT '',
  delivery_method VARCHAR(10) NOT NULL DEFAULT '',
  checked_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  enabled BOOLEAN NOT NULL DEFAULT TRUE,
  created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  KEY idx_subscription_user(user_id),
  CONSTRAINT fk_search_subscription_user FOREIGN KEY(user_id) REFERENCES users(id) ON DELETE CASCADE,
  CONSTRAINT fk_search_subscription_category FOREIGN KEY(category_id) REFERENCES categories(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
CREATE TABLE search_subscription_matches (
  subscription_id BIGINT UNSIGNED NOT NULL,
  product_id BIGINT UNSIGNED NOT NULL,
  PRIMARY KEY(subscription_id,product_id),
  CONSTRAINT fk_match_subscription FOREIGN KEY(subscription_id) REFERENCES search_subscriptions(id) ON DELETE CASCADE,
  CONSTRAINT fk_match_product FOREIGN KEY(product_id) REFERENCES products(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
