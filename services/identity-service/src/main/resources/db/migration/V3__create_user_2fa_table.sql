-- V3: Create user 2FA table
-- Stores TOTP secrets and recovery codes for 2FA

CREATE TABLE user_2fa (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL UNIQUE REFERENCES users(id) ON DELETE CASCADE,
    secret VARCHAR(255),
    enabled BOOLEAN NOT NULL DEFAULT false,
    recovery_codes TEXT[], -- Array of hashed recovery codes
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Index for user lookup
CREATE INDEX idx_2fa_user ON user_2fa(user_id);

-- Trigger to update updated_at
CREATE TRIGGER update_2fa_updated_at
    BEFORE UPDATE ON user_2fa
    FOR EACH ROW
    EXECUTE FUNCTION update_updated_at_column();

COMMENT ON TABLE user_2fa IS 'TOTP-based two-factor authentication settings';
COMMENT ON COLUMN user_2fa.secret IS 'TOTP secret key (encrypted at rest)';
COMMENT ON COLUMN user_2fa.recovery_codes IS 'Hashed backup recovery codes';
