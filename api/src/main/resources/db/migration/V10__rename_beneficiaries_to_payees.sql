-- Rename beneficiaries table to payees
ALTER TABLE beneficiaries RENAME TO payees;

-- Rename beneficiary_ibans table to payee_ibans
ALTER TABLE beneficiary_ibans RENAME TO payee_ibans;

-- Rename foreign key column in payee_ibans
ALTER TABLE payee_ibans RENAME COLUMN beneficiary_id TO payee_id;

-- Rename constraints
ALTER TABLE payees RENAME CONSTRAINT beneficiaries_asso_identifier1_unique TO payees_asso_identifier1_unique;
ALTER TABLE payee_ibans RENAME CONSTRAINT beneficiary_ibans_unique TO payee_ibans_unique;

-- Rename indexes
ALTER INDEX beneficiaries_association_idx RENAME TO payees_association_idx;
ALTER INDEX beneficiary_ibans_beneficiary_idx RENAME TO payee_ibans_payee_idx;

-- Rename the FK constraint on payee_ibans (references payees)
ALTER TABLE payee_ibans RENAME CONSTRAINT beneficiary_ibans_beneficiary_id_fkey TO payee_ibans_payee_id_fkey;
