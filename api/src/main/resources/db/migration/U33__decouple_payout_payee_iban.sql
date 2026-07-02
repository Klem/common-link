-- Rollback: restore the hard FK from payouts.payee_iban_id to payee_ibans(id)
ALTER TABLE payouts ADD CONSTRAINT payouts_payee_iban_id_fkey
    FOREIGN KEY (payee_iban_id) REFERENCES payee_ibans(id);

ALTER TABLE payouts DROP COLUMN IF EXISTS payee_iban_value;
