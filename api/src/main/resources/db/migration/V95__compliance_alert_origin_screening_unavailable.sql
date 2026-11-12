-- V95 : Restores SCREENING_UNAVAILABLE in compliance_alert_origin_check (audit 2026-11-12, H3).
--
-- V64 added the origin; V72 recreated the constraint to add CAMPAIGN_REPORT and dropped it. Since V72,
-- every alert raised when the asset-freeze screening could not run (FreezeHitAlertPort) violates the
-- CHECK, and the caller only logs the failure: the compliance officer never sees it.
--
-- The list below is the full ComplianceAlertOrigin enum; keep both identical (ComplianceAlertOriginCheckTest).
--
-- Rollback : U95__compliance_alert_origin_screening_unavailable.sql

ALTER TABLE compliance_alert DROP CONSTRAINT compliance_alert_origin_check;
ALTER TABLE compliance_alert
    ADD CONSTRAINT compliance_alert_origin_check
        CHECK (origin IN (
                          'FREEZE_HIT_ONBOARDING',
                          'FREEZE_HIT_DONATION',
                          'SCREENING_UNAVAILABLE',
                          'SYNC_FAILURE',
                          'SPLIT_DETECTION',
                          'ATYPICALITY_RULE',
                          'CAMPAIGN_REPORT'
            ));
