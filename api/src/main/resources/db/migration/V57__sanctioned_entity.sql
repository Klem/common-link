-- V57 : Table du registre national des mesures de gel des avoirs (DG Trésor, épique E4)
-- Source unique : Direction générale du Trésor — registre consolidant les mesures nationales,
-- européennes et onusiennes applicables en France (décision D2).
-- Rollback : U57__sanctioned_entity.sql

CREATE TABLE sanctioned_entity
(
    id               UUID         NOT NULL DEFAULT gen_random_uuid() PRIMARY KEY,
    id_registre      INTEGER      NOT NULL,
    nature           VARCHAR(32)  NOT NULL,
    nom              VARCHAR(512) NOT NULL,
    -- JSON array of normalized name variants (main name, reversed, aliases).
    -- Computed exclusively via NameNormalizer at ingestion — no SQL normalization.
    normalized_names TEXT         NOT NULL,
    -- Partial date of birth: "DD/MM/YYYY", "MM/YYYY", or "YYYY". Null for legal entities.
    date_of_birth    VARCHAR(32),
    legal_reference  VARCHAR(256),
    publication_date DATE         NOT NULL,
    ingested_at      TIMESTAMPTZ  NOT NULL,

    CONSTRAINT uq_sanctioned_entity_id_registre UNIQUE (id_registre)
);

CREATE INDEX idx_sanctioned_entity_nature ON sanctioned_entity (nature);
