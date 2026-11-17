-- U95 : Rollback of V95__compliance_alert_origin_screening_unavailable.sql
-- Fails if a SCREENING_UNAVAILABLE alert was recorded since V95: such rows must be handled by hand first.

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
