-- U62 : Rollback V62 — suppression des champs de traçabilité DG Trésor.
ALTER TABLE compliance_alert
    DROP COLUMN treasury_notified_at,
    DROP COLUMN treasury_notification_method,
    DROP COLUMN treasury_notification_ref;
