-- U35 : Rollback of V35__association_settings.sql

DROP TABLE IF EXISTS fiscal_mandate;
DROP SEQUENCE IF EXISTS fiscal_mandate_ref_seq;
DROP TABLE IF EXISTS association_document;

-- Restore the boolean column before dropping the enum column
ALTER TABLE association_profiles ADD COLUMN verified BOOLEAN NOT NULL DEFAULT false;
UPDATE association_profiles SET verified = true WHERE verification_status = 'VERIFIED';

ALTER TABLE association_profiles
    DROP COLUMN verification_status,
    DROP COLUMN verification_rejection_reason,
    DROP COLUMN verification_submitted_at,
    DROP COLUMN verified_at,
    DROP COLUMN rna,
    DROP COLUMN creation_year,
    DROP COLUMN contact_email,
    DROP COLUMN phone;
