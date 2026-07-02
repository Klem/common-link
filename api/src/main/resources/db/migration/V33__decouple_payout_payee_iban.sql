-- Snapshot the IBAN value at payout creation time, then drop the hard FK to payee_ibans
-- so that later edits/deletion of a beneficiary's IBAN never affect an already-issued payout.
ALTER TABLE payouts ADD COLUMN payee_iban_value VARCHAR(34);

UPDATE payouts p
SET payee_iban_value = pi.iban
FROM payee_ibans pi
WHERE pi.id = p.payee_iban_id;

ALTER TABLE payouts ALTER COLUMN payee_iban_value SET NOT NULL;

ALTER TABLE payouts DROP CONSTRAINT IF EXISTS payouts_payee_iban_id_fkey;
