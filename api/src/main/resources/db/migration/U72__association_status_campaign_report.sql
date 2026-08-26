-- U72 : Rollback de V72__association_status_campaign_report.sql

ALTER TABLE compliance_alert DROP CONSTRAINT compliance_alert_origin_check;
ALTER TABLE compliance_alert
    ADD CONSTRAINT compliance_alert_origin_check
        CHECK (origin IN (
                          'FREEZE_HIT_ONBOARDING',
                          'FREEZE_HIT_DONATION',
                          'SYNC_FAILURE',
                          'SPLIT_DETECTION',
                          'ATYPICALITY_RULE'
            ));

ALTER TABLE association_profiles DROP COLUMN status;
