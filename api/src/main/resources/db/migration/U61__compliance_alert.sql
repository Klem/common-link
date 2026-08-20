-- U61 : rollback de V61 — suppression de la table compliance_alert.
DROP INDEX IF EXISTS compliance_alert_pending_dedup_uq;
DROP INDEX IF EXISTS idx_compliance_alert_subject;
DROP INDEX IF EXISTS idx_compliance_alert_status;
DROP TABLE IF EXISTS compliance_alert;
