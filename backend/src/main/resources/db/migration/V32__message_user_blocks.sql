CREATE TABLE message_user_blocks (
    blocker_id BIGINT UNSIGNED NOT NULL,
    blocked_id BIGINT UNSIGNED NOT NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (blocker_id, blocked_id),
    CONSTRAINT fk_message_block_owner FOREIGN KEY (blocker_id) REFERENCES users(id),
    CONSTRAINT fk_message_block_target FOREIGN KEY (blocked_id) REFERENCES users(id),
    CONSTRAINT ck_message_block_distinct CHECK (blocker_id <> blocked_id)
);
