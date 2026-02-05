-- V5: Create auth audit log table
-- Tracks authentication events for security monitoring

CREATE TABLE auth_audit_logs (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID REFERENCES users(id) ON DELETE SET NULL,
    email VARCHAR(255),
    phone VARCHAR(20),
    ip_address VARCHAR(45),
    user_agent TEXT,
    event_type VARCHAR(50) NOT NULL,
    success BOOLEAN NOT NULL,
    failure_reason VARCHAR(100),
    details TEXT,
    risk_score INTEGER,
    metadata JSONB,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT chk_event_type CHECK (event_type IN (
        'LOGIN_SUCCESS', 'LOGIN_FAILED', 'LOGOUT',
        'OTP_SENT', 'OTP_VERIFIED', 'OTP_FAILED',
        'TWO_FACTOR_CHALLENGE', 'TWO_FACTOR_SUCCESS', 'TWO_FACTOR_FAILED',
        'TOKEN_REFRESH', 'TOKEN_REVOKED',
        'PASSWORD_CHANGED', 'PASSWORD_RESET'
    ))
);

-- Index for user's audit trail
CREATE INDEX idx_audit_user ON auth_audit_logs(user_id, created_at DESC)
    WHERE user_id IS NOT NULL;

-- Index for IP-based queries (rate limiting, security)
CREATE INDEX idx_audit_ip ON auth_audit_logs(ip_address, created_at DESC);

-- Index for email-based queries (login attempts)
CREATE INDEX idx_audit_email ON auth_audit_logs(email, created_at DESC)
    WHERE email IS NOT NULL;

-- Index for phone-based queries (OTP attempts)
CREATE INDEX idx_audit_phone ON auth_audit_logs(phone, created_at DESC)
    WHERE phone IS NOT NULL;

-- Index for event type queries
CREATE INDEX idx_audit_event ON auth_audit_logs(event_type, created_at DESC);

-- Partition by month for easier cleanup (optional - for high volume)
-- In Phase 2, consider partitioning: CREATE TABLE auth_audit_logs ... PARTITION BY RANGE (created_at);

COMMENT ON TABLE auth_audit_logs IS 'Security audit trail for all auth events';
COMMENT ON COLUMN auth_audit_logs.event_type IS 'Type of authentication event';
COMMENT ON COLUMN auth_audit_logs.details IS 'Additional context (failure reason, etc)';
