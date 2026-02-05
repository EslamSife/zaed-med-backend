-- V8: Fix user_2fa table to match entity

-- Drop and recreate with correct structure
ALTER TABLE user_2fa DROP CONSTRAINT IF EXISTS user_2fa_pkey;
ALTER TABLE user_2fa DROP COLUMN IF EXISTS id;
ALTER TABLE user_2fa ADD PRIMARY KEY (user_id);

-- Rename/add columns to match entity
ALTER TABLE user_2fa RENAME COLUMN secret TO totp_secret_encrypted;
ALTER TABLE user_2fa RENAME COLUMN enabled TO is_enabled;
ALTER TABLE user_2fa ADD COLUMN IF NOT EXISTS enabled_at TIMESTAMP WITH TIME ZONE;
ALTER TABLE user_2fa ADD COLUMN IF NOT EXISTS backup_codes_used INTEGER NOT NULL DEFAULT 0;

-- Drop the array column since entity uses @ElementCollection
ALTER TABLE user_2fa DROP COLUMN IF EXISTS recovery_codes;

-- Create the backup codes collection table for @ElementCollection
CREATE TABLE IF NOT EXISTS user_2fa_backup_codes (
    user_id UUID NOT NULL REFERENCES user_2fa(user_id) ON DELETE CASCADE,
    code_hash VARCHAR(255) NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_2fa_backup_codes_user ON user_2fa_backup_codes(user_id);

COMMENT ON COLUMN user_2fa.totp_secret_encrypted IS 'TOTP secret key (encrypted at rest)';
COMMENT ON COLUMN user_2fa.is_enabled IS 'Whether 2FA is enabled for this user';
COMMENT ON COLUMN user_2fa.enabled_at IS 'When 2FA was enabled';
COMMENT ON COLUMN user_2fa.backup_codes_used IS 'Count of backup codes that have been used';
COMMENT ON TABLE user_2fa_backup_codes IS 'Hashed backup recovery codes for 2FA';
