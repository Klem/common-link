-- Rollback V41 : suppression des colonnes de redirection widget

ALTER TABLE association_profiles
    DROP COLUMN IF EXISTS widget_redirect_url;

ALTER TABLE association_profiles
    DROP COLUMN IF EXISTS widget_cancel_url;
