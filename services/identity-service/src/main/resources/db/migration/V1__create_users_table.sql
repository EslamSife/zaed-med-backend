-- V1: Create users table
-- Auth Service database schema

-- Users table (core identity)
CREATE TABLE users (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    email VARCHAR(255) UNIQUE,
    phone VARCHAR(20) UNIQUE,
    name VARCHAR(255) NOT NULL,
    role VARCHAR(50) NOT NULL,
    partner_id UUID,
    is_active BOOLEAN NOT NULL DEFAULT true,
    last_login_at TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT chk_user_role CHECK (role IN (
        'DONOR', 'REQUESTER',
        'PARTNER_ADMIN', 'PARTNER_STAFF', 'PARTNER_VOLUNTEER',
        'ADMIN', 'SUPER_ADMIN'
    ))
);

-- Index for email lookups (login)
CREATE INDEX idx_users_email ON users(email) WHERE email IS NOT NULL;

-- Index for phone lookups (OTP)
CREATE INDEX idx_users_phone ON users(phone) WHERE phone IS NOT NULL;

-- Index for partner users
CREATE INDEX idx_users_partner ON users(partner_id) WHERE partner_id IS NOT NULL;

-- Trigger to update updated_at
CREATE OR REPLACE FUNCTION update_updated_at_column()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = CURRENT_TIMESTAMP;
    RETURN NEW;
END;
$$ language 'plpgsql';

CREATE TRIGGER update_users_updated_at
    BEFORE UPDATE ON users
    FOR EACH ROW
    EXECUTE FUNCTION update_updated_at_column();

COMMENT ON TABLE users IS 'Core user identity table for auth service';
COMMENT ON COLUMN users.role IS 'User role determining permissions';
COMMENT ON COLUMN users.partner_id IS 'Reference to partner org (for partner users)';
