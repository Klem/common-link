-- Rollback de V54__registry_check_officers.sql

ALTER TABLE association_registry_check
    DROP COLUMN IF EXISTS officers,
    DROP COLUMN IF EXISTS rna_active;
