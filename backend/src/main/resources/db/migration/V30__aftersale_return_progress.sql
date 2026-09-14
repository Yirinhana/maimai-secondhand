ALTER TABLE aftersales
    ADD COLUMN return_recipient VARCHAR(50) NULL,
    ADD COLUMN return_phone VARCHAR(30) NULL,
    ADD COLUMN return_address VARCHAR(500) NULL,
    ADD COLUMN return_shipped_at DATETIME(6) NULL,
    ADD COLUMN return_received_at DATETIME(6) NULL,
    ADD COLUMN return_inspection_deadline DATETIME(6) NULL;
CREATE INDEX idx_aftersale_return_inspection ON aftersales(status, return_inspection_deadline);
