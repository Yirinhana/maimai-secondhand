ALTER TABLE direct_messages DROP CHECK ck_message_content,
    ADD CONSTRAINT ck_message_content CHECK (
        (body IS NOT NULL AND CHAR_LENGTH(TRIM(body)) > 0)
        OR attachment_id IS NOT NULL OR product_id IS NOT NULL
    );
