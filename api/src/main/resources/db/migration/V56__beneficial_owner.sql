-- V56 : Table des bénéficiaires effectifs — identification LCB-FT (prompt 9)
-- Rollback : U56__beneficial_owner.sql
--
-- name et date_of_birth sont stockés chiffrés (AES-256-GCM via ComplianceCryptoConverter).
-- origin trace l'origine de l'information : DECLARED (déclaré par l'association),
-- REGISTRY (confirmé depuis le scan des registres), STATUTS (relevé sur les statuts).
-- discarded = true signifie que le curateur a écarté ce bénéficiaire ; la ligne est conservée
-- pour l'audit trail — jamais supprimée.

CREATE TABLE beneficial_owner
(
    id             UUID        NOT NULL DEFAULT gen_random_uuid() PRIMARY KEY,
    association_id UUID        NOT NULL REFERENCES association_profiles (id),
    name           TEXT        NOT NULL,
    role           VARCHAR(200),
    date_of_birth  TEXT,
    origin         VARCHAR(20) NOT NULL CHECK (origin IN ('DECLARED', 'REGISTRY', 'STATUTS')),
    collected_at   TIMESTAMPTZ NOT NULL,
    confirmed_by   UUID        REFERENCES users (id),
    discarded      BOOLEAN     NOT NULL DEFAULT FALSE,
    discarded_by   UUID        REFERENCES users (id),
    discarded_at   TIMESTAMPTZ
);

CREATE INDEX idx_beneficial_owner_association ON beneficial_owner (association_id);
