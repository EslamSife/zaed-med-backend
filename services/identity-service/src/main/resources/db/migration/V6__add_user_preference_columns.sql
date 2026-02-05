-- V6: Add missing user columns for preferences and verification

ALTER TABLE users ADD COLUMN IF NOT EXISTS is_verified BOOLEAN NOT NULL DEFAULT false;
ALTER TABLE users ADD COLUMN IF NOT EXISTS preferred_city VARCHAR(100);
ALTER TABLE users ADD COLUMN IF NOT EXISTS preferred_area VARCHAR(100);

-- Update role constraint to include PARTNER_PHARMACY
ALTER TABLE users DROP CONSTRAINT IF EXISTS chk_user_role;
ALTER TABLE users ADD CONSTRAINT chk_user_role CHECK (role IN (
    'DONOR', 'REQUESTER',
    'PARTNER_ADMIN', 'PARTNER_STAFF', 'PARTNER_VOLUNTEER', 'PARTNER_PHARMACY',
    'ADMIN', 'SUPER_ADMIN'
));

COMMENT ON COLUMN users.is_verified IS 'Whether user email/phone has been verified';
COMMENT ON COLUMN users.preferred_city IS 'User preferred city for matching';
COMMENT ON COLUMN users.preferred_area IS 'User preferred area within city';
