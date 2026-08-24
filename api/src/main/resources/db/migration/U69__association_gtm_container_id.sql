-- U69 : Rollback of V69__association_gtm_container_id.sql

ALTER TABLE association_profiles
    DROP COLUMN IF EXISTS gtm_container_id;
