-- V9: Change metadata column from JSONB to TEXT for simple string storage
-- The entity uses String type, so TEXT is more appropriate

ALTER TABLE auth_audit_logs ALTER COLUMN metadata TYPE TEXT;
