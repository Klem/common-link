ALTER TABLE association_profiles
    ADD COLUMN IF NOT EXISTS landing_show_project      BOOLEAN NOT NULL DEFAULT TRUE,
    ADD COLUMN IF NOT EXISTS landing_show_transparency BOOLEAN NOT NULL DEFAULT TRUE,
    ADD COLUMN IF NOT EXISTS landing_show_trust        BOOLEAN NOT NULL DEFAULT TRUE;
