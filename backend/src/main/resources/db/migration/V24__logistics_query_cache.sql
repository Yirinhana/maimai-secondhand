CREATE TABLE logistics_query_cache (
    carrier VARCHAR(50) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    tracking_no VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    phone_fingerprint CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NULL,
    last_attempt_at TIMESTAMP(6) NULL,
    fetched_at TIMESTAMP(6) NULL,
    trace_status VARCHAR(20) NULL,
    traces MEDIUMTEXT NULL,
    error_code VARCHAR(64) NULL,
    PRIMARY KEY (carrier, tracking_no)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
