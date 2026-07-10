-- V36 : Persist automated legal-existence registry pre-checks (LCB-FT audit trail)
-- Rollback: U36__association_registry_check.sql
--
-- Append-only: one row per scan. Rows are never updated or deleted, so a FK
-- reference to a row is a faithful, frozen snapshot of what was checked.
-- Prefixed `association_` — a future `donor_registry_check` may follow.

CREATE TABLE association_registry_check (
    id                      UUID        NOT NULL DEFAULT gen_random_uuid() PRIMARY KEY,
    association_id          UUID        NOT NULL REFERENCES association_profiles(id),

    -- Recherche d'entreprises
    association_exists      BOOLEAN,
    siren                   VARCHAR(9),
    rna                     VARCHAR(20),

    -- INSEE Sirene: 'A' = active, 'C' = ceased
    etat_administratif      VARCHAR(1)  CHECK (etat_administratif IN ('A', 'C')),

    -- JOAFE
    joafe_declaration_found BOOLEAN,
    dissolution_detected    BOOLEAN,

    -- BODACC
    bodacc_procedure_found  BOOLEAN,

    -- Non-fatal per-source failures (raw evidence) — JSON array string via StringListJsonConverter
    warnings                TEXT        NOT NULL DEFAULT '[]',

    -- Accountability: curator who triggered the scan
    checked_by              UUID        REFERENCES users(id),
    checked_at              TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- Latest scan per association = ORDER BY checked_at DESC LIMIT 1
CREATE INDEX idx_association_registry_check_assoc_checked_at
    ON association_registry_check(association_id, checked_at DESC);

-- Freeze the scan that informed the KYC approve/reject decision (nullable:
-- the check is informational, a curator may decide without a prior scan).
ALTER TABLE association_profiles
    ADD COLUMN decision_registry_check_id UUID REFERENCES association_registry_check(id);
