-- Persist the source and verified navigation snapshots with each chat answer.
ALTER TABLE support_ai_turns
  ADD COLUMN answer_source VARCHAR(20) NOT NULL DEFAULT 'MODEL',
  ADD COLUMN resources_json TEXT NULL;
