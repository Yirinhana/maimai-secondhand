-- An explicit local-draft acceptance is recorded only at successful new registration.
-- Existing seed/users are not backfilled as if they had consented.
CREATE TABLE user_policy_acceptances (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT PRIMARY KEY,
  user_id BIGINT UNSIGNED NOT NULL,
  policy_version VARCHAR(64) NOT NULL,
  accepted_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  UNIQUE KEY uk_upa_user_version(user_id,policy_version),
  CONSTRAINT fk_upa_user FOREIGN KEY(user_id) REFERENCES users(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
