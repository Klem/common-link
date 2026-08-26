-- The association can no longer hide these sections on the public landing page (ACPR compliance:
-- objet/transparence/confiance are always rendered). The preference columns had no effect anymore.
ALTER TABLE association_profiles
    DROP COLUMN IF EXISTS landing_show_project,
    DROP COLUMN IF EXISTS landing_show_transparency,
    DROP COLUMN IF EXISTS landing_show_trust;
