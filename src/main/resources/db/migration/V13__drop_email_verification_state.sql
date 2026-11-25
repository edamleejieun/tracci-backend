ALTER TABLE users
    DROP COLUMN IF EXISTS email_verified,
    DROP COLUMN IF EXISTS email_verified_at;
