-- Rollback of V36__association_registry_check.sql

ALTER TABLE association_profiles
    DROP COLUMN decision_registry_check_id;

DROP TABLE association_registry_check;
