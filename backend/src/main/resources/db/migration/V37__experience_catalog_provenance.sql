-- Provenance is explicit and defaults to ordinary user data. No sample content is seeded by Flyway.
ALTER TABLE products ADD COLUMN experience_source VARCHAR(80) NULL,
  ADD COLUMN supply_note VARCHAR(500) NULL;
ALTER TABLE orders ADD COLUMN experience_source VARCHAR(80) NULL;
