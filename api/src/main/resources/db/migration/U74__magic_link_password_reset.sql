-- U74 : Rollback of V74__magic_link_password_reset.sql

ALTER TABLE magic_link_tokens
    DROP COLUMN IF EXISTS password_reset,
    DROP COLUMN IF EXISTS password_reset_grace_until,
    DROP COLUMN IF EXISTS password_reset_consumed_at;
