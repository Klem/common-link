-- Undo: rename payee_ibans FK constraint back
ALTER TABLE payee_ibans RENAME CONSTRAINT payee_ibans_payee_id_fkey TO beneficiary_ibans_beneficiary_id_fkey;

-- Undo: rename indexes back
ALTER INDEX payees_association_idx RENAME TO beneficiaries_association_idx;
ALTER INDEX payee_ibans_payee_idx RENAME TO beneficiary_ibans_beneficiary_idx;

-- Undo: rename constraints back
ALTER TABLE payee_ibans RENAME CONSTRAINT payee_ibans_unique TO beneficiary_ibans_unique;
ALTER TABLE payees RENAME CONSTRAINT payees_asso_identifier1_unique TO beneficiaries_asso_identifier1_unique;

-- Undo: rename foreign key column back
ALTER TABLE payee_ibans RENAME COLUMN payee_id TO beneficiary_id;

-- Undo: rename tables back
ALTER TABLE payee_ibans RENAME TO beneficiary_ibans;
ALTER TABLE payees RENAME TO beneficiaries;
