-- Order-scoped QR sessions. No bank, wallet, or real payment credentials.
CREATE TABLE experience_payment_sessions (
  token CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL PRIMARY KEY,
  payment_id BIGINT UNSIGNED NOT NULL UNIQUE,
  created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  CONSTRAINT fk_experience_payment FOREIGN KEY (payment_id) REFERENCES payment_requests(id) ON DELETE CASCADE
);
