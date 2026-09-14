-- 社区修正：评价下架标记（举报隐藏评价）、足迹按浏览时间过滤/清理索引
ALTER TABLE community_order_ratings
  ADD COLUMN is_hidden TINYINT(1) NOT NULL DEFAULT 0 AFTER comment,
  ADD COLUMN ratee_id BIGINT UNSIGNED NULL AFTER rater_id,
  ADD KEY idx_rating_hidden (is_hidden);

UPDATE community_order_ratings r JOIN orders o ON o.id = r.order_id
SET r.ratee_id = CASE WHEN r.rater_id = o.buyer_id THEN o.seller_id ELSE o.buyer_id END;

ALTER TABLE community_order_ratings
  MODIFY ratee_id BIGINT UNSIGNED NOT NULL,
  ADD KEY idx_rating_ratee_visible (ratee_id, is_hidden, created_at),
  ADD CONSTRAINT fk_rating_ratee FOREIGN KEY (ratee_id) REFERENCES users(id);

ALTER TABLE community_product_footprints
  ADD KEY idx_footprint_user_viewed (user_id, viewed_at),
  ADD KEY idx_footprint_viewed (viewed_at);

ALTER TABLE community_demand_replies ADD KEY idx_reply_status (status, created_at);
