-- Rollback of V90.
CREATE INDEX IF NOT EXISTS idx_compliance_audit_log_event_type
    ON compliance_audit_log (event_type);

DROP INDEX IF EXISTS idx_compliance_audit_log_event_type_seq;
