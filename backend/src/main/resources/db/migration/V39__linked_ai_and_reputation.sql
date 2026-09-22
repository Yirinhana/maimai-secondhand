-- Preserve source records. AI advice never changes an order or a moderation decision.
ALTER TABLE community_reports
  ADD COLUMN target_owner_id BIGINT UNSIGNED NULL,
  ADD COLUMN content_snapshot TEXT NULL,
  ADD COLUMN target_url VARCHAR(250) NULL,
  ADD CONSTRAINT fk_report_target_owner FOREIGN KEY (target_owner_id) REFERENCES users(id),
  ADD KEY idx_report_target (resource_type, resource_id, status);

-- Recover existing account links only. Do not invent a historical content snapshot.
UPDATE community_reports r JOIN products p ON r.resource_type='PRODUCT' AND p.id=r.resource_id SET r.target_owner_id=p.seller_id;
UPDATE community_reports r JOIN product_comments c ON r.resource_type='PRODUCT_COMMENT' AND c.id=r.resource_id SET r.target_owner_id=c.author_id;
UPDATE community_reports r JOIN community_demand_posts d ON r.resource_type='DEMAND_POST' AND d.id=r.resource_id SET r.target_owner_id=d.author_id;
UPDATE community_reports r JOIN community_demand_replies d ON r.resource_type='DEMAND_REPLY' AND d.id=r.resource_id SET r.target_owner_id=d.author_id;
UPDATE community_reports r JOIN community_order_ratings v ON r.resource_type='ORDER_REVIEW' AND v.id=r.resource_id SET r.target_owner_id=v.rater_id;

CREATE TABLE ai_workflow_runs (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT PRIMARY KEY,
  actor_id BIGINT UNSIGNED NOT NULL,
  stage VARCHAR(24) NOT NULL,
  resource_id BIGINT UNSIGNED NULL,
  request_key VARCHAR(64) NOT NULL,
  request_hash CHAR(64) NOT NULL,
  status VARCHAR(20) NOT NULL,
  context_summary TEXT NOT NULL,
  answer TEXT NULL,
  failure_code VARCHAR(64) NULL,
  model VARCHAR(100) NOT NULL,
  created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  finished_at DATETIME(6) NULL,
  CONSTRAINT fk_ai_workflow_actor FOREIGN KEY (actor_id) REFERENCES users(id),
  UNIQUE KEY uk_ai_workflow_request (actor_id, request_key),
  KEY idx_ai_workflow_resource (stage, resource_id, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE report_ai_assessments (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT PRIMARY KEY,
  report_id BIGINT UNSIGNED NOT NULL,
  requested_by BIGINT UNSIGNED NOT NULL,
  status VARCHAR(20) NOT NULL,
  assessment TEXT NULL,
  failure_code VARCHAR(64) NULL,
  model VARCHAR(100) NOT NULL,
  created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  finished_at DATETIME(6) NULL,
  CONSTRAINT fk_report_ai_report FOREIGN KEY (report_id) REFERENCES community_reports(id),
  CONSTRAINT fk_report_ai_actor FOREIGN KEY (requested_by) REFERENCES users(id),
  KEY idx_report_ai_history (report_id, id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE reputation_reminders (
  order_id BIGINT UNSIGNED NOT NULL,
  user_id BIGINT UNSIGNED NOT NULL,
  created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  PRIMARY KEY (order_id, user_id),
  CONSTRAINT fk_reputation_reminder_order FOREIGN KEY (order_id) REFERENCES orders(id),
  CONSTRAINT fk_reputation_reminder_user FOREIGN KEY (user_id) REFERENCES users(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
