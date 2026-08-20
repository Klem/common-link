-- V61 : Modèle d'alertes de conformité LCB-FT (épique E4, prompt 16).
-- Table compliance_alert : une ligne par alerte ouverte ou clôturée.
-- Le détail circonstancié vit dans compliance_audit_log (V51) — pas ici.
--
-- Cycle de vie : PENDING → IN_REVIEW → CLOSED (irréversible, pas de retour en arrière).
-- Déduplication : l'index partiel compliance_alert_pending_dedup_uq interdit
-- d'ouvrir une seconde alerte pour le même couple (origine, sujet) tant que la
-- première n'est pas CLOSED. COALESCE(subject_id::text, '') couvre les alertes
-- de niveau SYSTEM dont subject_id est NULL (ex. SYNC_FAILURE).
--
-- Rollback : U61__compliance_alert.sql

CREATE TABLE compliance_alert
(
    id                 UUID        NOT NULL DEFAULT gen_random_uuid() PRIMARY KEY,
    origin             VARCHAR(32) NOT NULL
        CONSTRAINT compliance_alert_origin_check
            CHECK (origin IN (
                'FREEZE_HIT_ONBOARDING',
                'FREEZE_HIT_DONATION',
                'SYNC_FAILURE',
                'SPLIT_DETECTION',
                'ATYPICALITY_RULE'
            )),
    subject_type       VARCHAR(32) NOT NULL
        CONSTRAINT compliance_alert_subject_type_check
            CHECK (subject_type IN ('ASSOCIATION', 'BENEFICIAL_OWNER', 'DONOR', 'SYSTEM')),
    subject_id         UUID,
    severity           VARCHAR(16) NOT NULL
        CONSTRAINT compliance_alert_severity_check
            CHECK (severity IN ('HIGH', 'MEDIUM', 'LOW')),
    status             VARCHAR(16) NOT NULL DEFAULT 'PENDING'
        CONSTRAINT compliance_alert_status_check
            CHECK (status IN ('PENDING', 'IN_REVIEW', 'CLOSED')),
    created_at         TIMESTAMPTZ NOT NULL,
    taken_in_charge_at TIMESTAMPTZ,
    taken_in_charge_by UUID        REFERENCES users (id),
    decision           VARCHAR(16)
        CONSTRAINT compliance_alert_decision_check
            CHECK (decision IS NULL OR decision IN ('LEGITIMATE', 'SUSPICIOUS', 'FALSE_POSITIVE')),
    decision_rationale TEXT,
    audit_log_seq_ref  BIGINT
);

-- Au plus une alerte ouverte (PENDING ou IN_REVIEW) par (origine, sujet).
-- COALESCE(subject_id::text, '') assure l'unicité même quand subject_id est NULL.
CREATE UNIQUE INDEX compliance_alert_pending_dedup_uq
    ON compliance_alert (origin, COALESCE(subject_id::text, ''))
    WHERE status IN ('PENDING', 'IN_REVIEW');

CREATE INDEX idx_compliance_alert_subject ON compliance_alert (subject_type, subject_id);
CREATE INDEX idx_compliance_alert_status  ON compliance_alert (status, created_at DESC);
