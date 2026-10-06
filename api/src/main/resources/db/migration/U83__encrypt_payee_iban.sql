-- Rollback of V83. Does NOT restore `iban` to VARCHAR(34): once any row has been encrypted the
-- stored value ("v1:<iv>:<ct>") no longer fits, and that re-encryption is not reversible here
-- (same convention as U82 -- the pre-migration plaintext is not recoverable from this script
-- alone). Restores the original constraint shape; the column stays TEXT.
ALTER TABLE payee_ibans DROP CONSTRAINT IF EXISTS payee_ibans_unique;
ALTER TABLE payee_ibans ADD CONSTRAINT payee_ibans_unique UNIQUE (payee_id, iban);
ALTER TABLE payee_ibans DROP COLUMN IF EXISTS iban_fingerprint;
