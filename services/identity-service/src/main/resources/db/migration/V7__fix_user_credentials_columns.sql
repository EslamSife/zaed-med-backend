-- V7: Fix user_credentials column names to match entity

-- Drop the id column and make user_id the primary key
ALTER TABLE user_credentials DROP CONSTRAINT IF EXISTS user_credentials_pkey;
ALTER TABLE user_credentials DROP COLUMN IF EXISTS id;
ALTER TABLE user_credentials ADD PRIMARY KEY (user_id);

-- Rename columns to match entity
ALTER TABLE user_credentials RENAME COLUMN failed_attempts TO failed_login_attempts;
ALTER TABLE user_credentials RENAME COLUMN force_password_change TO must_change_password;

COMMENT ON COLUMN user_credentials.failed_login_attempts IS 'Number of consecutive failed login attempts';
COMMENT ON COLUMN user_credentials.must_change_password IS 'Require password change on next login';
