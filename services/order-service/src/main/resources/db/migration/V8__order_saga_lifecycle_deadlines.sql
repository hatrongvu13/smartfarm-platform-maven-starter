ALTER TABLE ord_saga ADD COLUMN IF NOT EXISTS processing_deadline_at TIMESTAMP WITH TIME ZONE;
ALTER TABLE ord_saga ADD COLUMN IF NOT EXISTS manual_review_until TIMESTAMP WITH TIME ZONE;
UPDATE ord_saga SET processing_deadline_at = created_at + INTERVAL '30 minutes' WHERE processing_deadline_at IS NULL;
ALTER TABLE ord_saga ALTER COLUMN processing_deadline_at SET NOT NULL;
CREATE INDEX IF NOT EXISTS ix_ord_saga_processing_deadline ON ord_saga (status, processing_deadline_at);
CREATE INDEX IF NOT EXISTS ix_ord_saga_manual_review_until ON ord_saga (status, manual_review_until);
