-- V4: Create refresh tokens table
-- Stores refresh tokens for session management and revocation

CREATE TABLE refresh_tokens (
    id VARCHAR(36) PRIMARY KEY,  -- The jti claim from the JWT
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    token_hash VARCHAR(255) NOT NULL,  -- SHA-256 hash for secure lookup
    device_id VARCHAR(255),
    device_info VARCHAR(500),
    ip_address VARCHAR(45),
    last_used_at TIMESTAMP WITH TIME ZONE,
    expires_at TIMESTAMP WITH TIME ZONE NOT NULL,
    revoked_at TIMESTAMP WITH TIME ZONE,
    revoke_reason VARCHAR(50),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Index for token hash lookup
CREATE INDEX idx_refresh_token_hash ON refresh_tokens(token_hash);

-- Index for user's tokens (for logout all)
CREATE INDEX idx_refresh_user ON refresh_tokens(user_id);

-- Index for active tokens
CREATE INDEX idx_refresh_active ON refresh_tokens(user_id, expires_at)
    WHERE revoked_at IS NULL;

-- Index for cleanup job
CREATE INDEX idx_refresh_cleanup ON refresh_tokens(expires_at, revoked_at);

COMMENT ON TABLE refresh_tokens IS 'JWT refresh tokens for session management';
COMMENT ON COLUMN refresh_tokens.id IS 'The jti claim from the JWT';
COMMENT ON COLUMN refresh_tokens.token_hash IS 'SHA-256 hash of the token for secure lookup';
COMMENT ON COLUMN refresh_tokens.device_id IS 'Client device identifier';
COMMENT ON COLUMN refresh_tokens.revoke_reason IS 'Reason: LOGOUT, LOGOUT_ALL, ROTATION, SUSPICIOUS, ADMIN';
