-- Rollback of V38__association_widget.sql

DROP INDEX IF EXISTS idx_association_profiles_widget_destination_campaign_id;

ALTER TABLE association_profiles
    DROP CONSTRAINT IF EXISTS association_profiles_widget_destination_campaign_id_fkey;

ALTER TABLE association_profiles
    DROP COLUMN IF EXISTS widget_destination_campaign_id;

ALTER TABLE association_profiles
    DROP CONSTRAINT IF EXISTS association_profiles_widget_token_unique;

ALTER TABLE association_profiles
    DROP COLUMN IF EXISTS widget_token;
