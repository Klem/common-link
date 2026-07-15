ALTER TABLE users DROP CONSTRAINT users_provider_check;
ALTER TABLE users ADD CONSTRAINT users_provider_check
    CHECK (provider IN ('EMAIL', 'GOOGLE', 'MAGIC_LINK'));

ALTER TABLE users DROP COLUMN guest;
