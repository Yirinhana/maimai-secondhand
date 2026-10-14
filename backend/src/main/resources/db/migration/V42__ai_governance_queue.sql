-- Transactional intake. No historical rows are silently sent to a model by this migration.
CREATE TABLE ai_governance_signals (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT PRIMARY KEY,
  source_type VARCHAR(24) NOT NULL,
  source_id BIGINT UNSIGNED NOT NULL,
  revision BIGINT NOT NULL DEFAULT 1,
  captured_revision BIGINT NOT NULL DEFAULT 0,
  updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  UNIQUE KEY uk_governance_signal (source_type, source_id),
  KEY idx_governance_signal_capture (captured_revision, revision, id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE ai_governance_jobs (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT PRIMARY KEY,
  signal_id BIGINT UNSIGNED NOT NULL,
  source_type VARCHAR(24) NOT NULL,
  source_id BIGINT UNSIGNED NOT NULL,
  source_hash CHAR(64) NOT NULL,
  lane VARCHAR(12) NOT NULL,
  group_key VARCHAR(100) NOT NULL,
  source_url VARCHAR(250) NOT NULL,
  source_title VARCHAR(200) NOT NULL,
  actor_id BIGINT UNSIGNED NULL,
  subject_id BIGINT UNSIGNED NULL,
  source_text TEXT NOT NULL,
  facts TEXT NOT NULL,
  status VARCHAR(20) NOT NULL DEFAULT 'QUEUED',
  attempts INT NOT NULL DEFAULT 0,
  failure_code VARCHAR(64) NULL,
  model VARCHAR(100) NULL,
  result_json TEXT NULL,
  category VARCHAR(24) NULL,
  priority VARCHAR(12) NULL,
  created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  started_at DATETIME(6) NULL,
  finished_at DATETIME(6) NULL,
  next_attempt_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  CONSTRAINT fk_gov_job_signal FOREIGN KEY (signal_id) REFERENCES ai_governance_signals(id),
  UNIQUE KEY uk_gov_job_content (source_type, source_id, source_hash),
  KEY idx_gov_job_queue (status, next_attempt_at, id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE ai_governance_cases (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT PRIMARY KEY,
  case_key VARCHAR(150) NOT NULL,
  lane VARCHAR(12) NOT NULL,
  category VARCHAR(24) NOT NULL,
  priority VARCHAR(12) NOT NULL,
  status VARCHAR(20) NOT NULL DEFAULT 'OPEN',
  assigned_to BIGINT UNSIGNED NULL,
  latest_job_id BIGINT UNSIGNED NOT NULL,
  created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  UNIQUE KEY uk_gov_case (case_key),
  CONSTRAINT fk_gov_case_job FOREIGN KEY (latest_job_id) REFERENCES ai_governance_jobs(id),
  CONSTRAINT fk_gov_case_assignee FOREIGN KEY (assigned_to) REFERENCES users(id),
  KEY idx_gov_case_queue (lane, status, priority, updated_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE ai_governance_case_jobs (
  case_id BIGINT UNSIGNED NOT NULL,
  job_id BIGINT UNSIGNED NOT NULL,
  PRIMARY KEY (case_id, job_id),
  CONSTRAINT fk_gov_evidence_case FOREIGN KEY (case_id) REFERENCES ai_governance_cases(id),
  CONSTRAINT fk_gov_evidence_job FOREIGN KEY (job_id) REFERENCES ai_governance_jobs(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE ai_governance_actions (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT PRIMARY KEY,
  case_id BIGINT UNSIGNED NULL,
  actor_id BIGINT UNSIGNED NOT NULL,
  action VARCHAR(32) NOT NULL,
  note VARCHAR(1000) NOT NULL,
  created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  CONSTRAINT fk_gov_action_case FOREIGN KEY (case_id) REFERENCES ai_governance_cases(id),
  CONSTRAINT fk_gov_action_actor FOREIGN KEY (actor_id) REFERENCES users(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE ai_governance_control (
  id INT NOT NULL PRIMARY KEY,
  paused BOOLEAN NOT NULL DEFAULT FALSE,
  next_call_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
INSERT INTO ai_governance_control(id) VALUES (1);

CREATE TABLE ai_governance_daily_usage (
  usage_date DATE NOT NULL PRIMARY KEY,
  calls INT NOT NULL DEFAULT 0
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
