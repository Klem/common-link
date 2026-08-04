-- U48 : Rollback of V48__association_landing_config.sql

DROP TABLE IF EXISTS association_logos;

ALTER TABLE association_profiles
    DROP CONSTRAINT IF EXISTS chk_association_landing_theme;

ALTER TABLE association_profiles
    DROP COLUMN IF EXISTS landing_theme,
    DROP COLUMN IF EXISTS landing_logo,
    DROP COLUMN IF EXISTS landing_show_project,
    DROP COLUMN IF EXISTS landing_show_transparency,
    DROP COLUMN IF EXISTS landing_show_trust;
