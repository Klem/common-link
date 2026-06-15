-- Idempotency constraint on donations.provider_ref.
-- provider_ref is the natural idempotency key for an incoming payment webhook
-- (format: "stripe:pi_..." or "monerium:<uuid>"). Without this constraint a retried
-- webhook can insert two Donation rows for the same payment, each enqueuing
-- RECORD_DONATION -> double on-chain recording and double tax receipts.
--
-- Pre-condition: verify no duplicate provider_ref values exist before running.
-- Run this query first; it must return zero rows or the migration will fail:
--   SELECT provider_ref, COUNT(*) FROM donations GROUP BY provider_ref HAVING COUNT(*) > 1;
ALTER TABLE donations ADD CONSTRAINT donations_provider_ref_unique UNIQUE (provider_ref);
