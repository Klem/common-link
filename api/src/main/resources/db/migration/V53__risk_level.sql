-- V53 : LCB-FT — colonnes niveau de risque
-- association_profiles : niveau de risque, date d'évaluation, version du document de classification.
-- donations           : niveau de risque au moment de l'enregistrement.
--
-- DEFAULT 'STANDARD' : toutes les lignes existantes ressortent au niveau standard.
-- risk_classification_version est nullable : null jusqu'à la première évaluation formelle.

ALTER TABLE association_profiles
    ADD COLUMN risk_level                  VARCHAR(20)  NOT NULL DEFAULT 'STANDARD'
        CONSTRAINT chk_association_profiles_risk_level
            CHECK (risk_level IN ('LOW', 'STANDARD', 'HIGH')),
    ADD COLUMN risk_level_assessed_at      TIMESTAMPTZ,
    ADD COLUMN risk_classification_version VARCHAR(32);

ALTER TABLE donations
    ADD COLUMN risk_level VARCHAR(20) NOT NULL DEFAULT 'STANDARD'
        CONSTRAINT chk_donations_risk_level
            CHECK (risk_level IN ('LOW', 'STANDARD', 'HIGH'));
