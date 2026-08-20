-- U60 : Rollback de V60 — retire DONOR du CHECK de compliance_audit_log.subject_type.
ALTER TABLE compliance_audit_log DROP CONSTRAINT compliance_audit_log_subject_type_check;
ALTER TABLE compliance_audit_log
    ADD CONSTRAINT compliance_audit_log_subject_type_check
        CHECK (subject_type IN ('ASSOCIATION', 'DONATION', 'CAMPAIGN', 'ALERT', 'DECLARANT', 'SYSTEM', 'BENEFICIAL_OWNER'));
