-- V2: Create user credentials table
-- Stores password hashes for partner/admin users

CREATE TABLE user_credentials (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL UNIQUE REFERENCES users(id) ON DELETE CASCADE,
    password_hash VARCHAR(255) NOT NULL,
    password_changed_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    force_password_change BOOLEAN NOT NULL DEFAULT false,
    failed_attempts INTEGER NOT NULL DEFAULT 0,
    locked_until TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Index for user lookup
CREATE INDEX idx_credentials_user ON user_credentials(user_id);

-- Trigger to update updated_at
CREATE TRIGGER update_credentials_updated_at
    BEFORE UPDATE ON user_credentials
    FOR EACH ROW
    EXECUTE FUNCTION update_updated_at_column();

COMMENT ON TABLE user_credentials IS 'Password credentials for partner/admin users';
COMMENT ON COLUMN user_credentials.force_password_change IS 'Require password change on next login';
COMMENT ON COLUMN user_credentials.locked_until IS 'Account locked until this time after failed attempts';
