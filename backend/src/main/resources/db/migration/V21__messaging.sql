CREATE TABLE message_conversations (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT PRIMARY KEY,
  user_low BIGINT UNSIGNED NOT NULL,
  user_high BIGINT UNSIGNED NOT NULL,
  product_id BIGINT UNSIGNED NULL,
  created_by BIGINT UNSIGNED NOT NULL,
  created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  UNIQUE KEY uk_message_pair (user_low, user_high),
  KEY idx_conversation_low (user_low, updated_at),
  KEY idx_conversation_high (user_high, updated_at),
  CONSTRAINT fk_conversation_low FOREIGN KEY (user_low) REFERENCES users(id),
  CONSTRAINT fk_conversation_high FOREIGN KEY (user_high) REFERENCES users(id),
  CONSTRAINT fk_conversation_product FOREIGN KEY (product_id) REFERENCES products(id),
  CONSTRAINT ck_conversation_pair CHECK (user_low < user_high)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE message_attachments (
  id CHAR(36) NOT NULL PRIMARY KEY,
  conversation_id BIGINT UNSIGNED NOT NULL,
  uploaded_by BIGINT UNSIGNED NOT NULL,
  storage_name VARCHAR(80) NOT NULL,
  media_type VARCHAR(40) NOT NULL,
  byte_size BIGINT NOT NULL,
  created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  CONSTRAINT fk_attachment_conversation FOREIGN KEY (conversation_id) REFERENCES message_conversations(id),
  CONSTRAINT fk_attachment_user FOREIGN KEY (uploaded_by) REFERENCES users(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE direct_messages (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT PRIMARY KEY,
  conversation_id BIGINT UNSIGNED NOT NULL,
  sender_id BIGINT UNSIGNED NOT NULL,
  client_id CHAR(36) NOT NULL,
  body VARCHAR(2000) NULL,
  attachment_id CHAR(36) NULL,
  created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  UNIQUE KEY uk_message_retry (sender_id, client_id),
  UNIQUE KEY uk_message_attachment (attachment_id),
  KEY idx_message_history (conversation_id, id),
  KEY idx_message_sender_time (sender_id, created_at),
  CONSTRAINT fk_message_conversation FOREIGN KEY (conversation_id) REFERENCES message_conversations(id),
  CONSTRAINT fk_message_sender FOREIGN KEY (sender_id) REFERENCES users(id),
  CONSTRAINT fk_message_attachment FOREIGN KEY (attachment_id) REFERENCES message_attachments(id),
  CONSTRAINT ck_message_content CHECK ((body IS NOT NULL AND CHAR_LENGTH(body) > 0) OR attachment_id IS NOT NULL)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE message_read_positions (
  conversation_id BIGINT UNSIGNED NOT NULL,
  user_id BIGINT UNSIGNED NOT NULL,
  last_read_id BIGINT UNSIGNED NOT NULL DEFAULT 0,
  updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  PRIMARY KEY (conversation_id, user_id),
  CONSTRAINT fk_read_conversation FOREIGN KEY (conversation_id) REFERENCES message_conversations(id),
  CONSTRAINT fk_read_user FOREIGN KEY (user_id) REFERENCES users(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
