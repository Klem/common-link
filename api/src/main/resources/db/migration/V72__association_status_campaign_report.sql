-- V72 : Statut de conformité de l'association (IC-44 — canal de signalement de campagne).
-- Ajoute association_profiles.status (ACTIVE / ALERT / SUSPENDED) et élargit le CHECK
-- de compliance_alert.origin (V61, réduit par V64) au nouvel origin CAMPAIGN_REPORT.
--
-- ACTIVE → ALERT : signalement public reçu, en attente de traitement compliance (interne, ne
-- bloque rien publiquement). ALERT → SUSPENDED : signalement confirmé fondé par la compliance.
-- SUSPENDED → ACTIVE : réactivation par la compliance (voir ASSOCIATION_REACTIVATED, service
-- dédié — ne rouvre jamais l'alerte CLOSED d'origine, qui reste la preuve historique).
--
-- Rollback : U72__association_status_campaign_report.sql

ALTER TABLE association_profiles
    ADD COLUMN status VARCHAR(16) NOT NULL DEFAULT 'ACTIVE'
        CONSTRAINT association_profiles_status_check
            CHECK (status IN ('ACTIVE', 'ALERT', 'SUSPENDED'));

ALTER TABLE compliance_alert DROP CONSTRAINT compliance_alert_origin_check;
ALTER TABLE compliance_alert
    ADD CONSTRAINT compliance_alert_origin_check
        CHECK (origin IN (
                          'FREEZE_HIT_ONBOARDING',
                          'FREEZE_HIT_DONATION',
                          'SYNC_FAILURE',
                          'SPLIT_DETECTION',
                          'ATYPICALITY_RULE',
                          'CAMPAIGN_REPORT'
            ));
