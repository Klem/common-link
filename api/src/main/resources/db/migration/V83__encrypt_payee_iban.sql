-- Encrypt payee_ibans.iban at rest (security audit 2026-10-06, finding #2).
--
-- AES-256-GCM (ComplianceCryptoConverter) uses a fresh random IV per write, so two encryptions of
-- the same IBAN produce different ciphertexts -- the existing payee_ibans_unique constraint on the
-- raw `iban` column can no longer detect a duplicate once it's encrypted. `iban_fingerprint` is a
-- deterministic HMAC-SHA256 of the normalised IBAN (IbanFingerprint.kt), added alongside the
-- encrypted column and used by the (new) unique constraint instead.
--
-- No backfill needed: there are no existing payee_ibans rows in production as of this migration,
-- and staging has COMPLIANCE_ENCRYPTION_KEY unset (ComplianceCryptoConverter is then a no-op, so
-- existing plaintext rows there stay readable regardless). iban_fingerprint stays nullable rather
-- than NOT NULL even so -- any row written outside PayeeService.addIban (a seed script, a future
-- bulk import) leaves it NULL, and Postgres does not enforce uniqueness among NULLs, so that's not
-- a gap this constraint needs to close.
--
-- Rollback: U83__encrypt_payee_iban.sql

ALTER TABLE payee_ibans
    ALTER COLUMN iban TYPE TEXT;

ALTER TABLE payee_ibans
    ADD COLUMN iban_fingerprint VARCHAR(64) NULL;

ALTER TABLE payee_ibans
    DROP CONSTRAINT payee_ibans_unique;

ALTER TABLE payee_ibans
    ADD CONSTRAINT payee_ibans_unique UNIQUE (payee_id, iban_fingerprint);
