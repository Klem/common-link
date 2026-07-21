-- V40 : Widget de don — champs snapshot identité + site source sur les donations
-- Rollback: U40__donation_widget_fields.sql

-- Site tiers auto-déclaré (best-effort, non fiable — nettoyé côté back).
ALTER TABLE donations ADD COLUMN source_site VARCHAR(255);

-- Snapshot d'identité fiscale au moment du don (reçu Cerfa 2041-RD).
-- Nullable en base (dons hors widget), mais requis par la validation du flux widget.
ALTER TABLE donations ADD COLUMN donor_full_name    VARCHAR(255);
ALTER TABLE donations ADD COLUMN donor_address_line1 VARCHAR(255);
ALTER TABLE donations ADD COLUMN donor_address_line2 VARCHAR(255);
ALTER TABLE donations ADD COLUMN donor_postal_code   VARCHAR(16);
ALTER TABLE donations ADD COLUMN donor_city          VARCHAR(128);
ALTER TABLE donations ADD COLUMN donor_country       VARCHAR(2);
