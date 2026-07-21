-- V38 : Widget de don — token public + campagne de destination sur l'association
-- Rollback: U38__association_widget.sql

-- Token public opaque (ex. clk_…) pour identifier le widget d'une association.
-- UNIQUE mais nullable : une association sans widget actif = NULL.
ALTER TABLE association_profiles
    ADD COLUMN widget_token VARCHAR(255);

ALTER TABLE association_profiles
    ADD CONSTRAINT association_profiles_widget_token_unique UNIQUE (widget_token);

-- Campagne de destination choisie par l'association pour recevoir les dons widget.
-- ON DELETE SET NULL : la suppression d'une campagne désactive silencieusement le widget
-- sans bloquer la suppression ni orpheliner l'association.
ALTER TABLE association_profiles
    ADD COLUMN widget_destination_campaign_id UUID;

ALTER TABLE association_profiles
    ADD CONSTRAINT association_profiles_widget_destination_campaign_id_fkey
        FOREIGN KEY (widget_destination_campaign_id)
            REFERENCES campaigns (id)
            ON DELETE SET NULL;

-- Postgres n'indexe pas les colonnes FK automatiquement ; index nécessaire pour
-- les lookups ON DELETE SET NULL et les futures jointures widget.
CREATE INDEX idx_association_profiles_widget_destination_campaign_id
    ON association_profiles (widget_destination_campaign_id)
    WHERE widget_destination_campaign_id IS NOT NULL;
