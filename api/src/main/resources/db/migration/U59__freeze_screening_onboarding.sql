-- Rollback V59 : retire BENEFICIAL_OWNER du CHECK de compliance_audit_log.subject_type.
-- Applicable uniquement si aucune ligne BENEFICIAL_OWNER n'existe dans compliance_audit_log.
ALTER TABLE compliance_audit_log DROP CONSTRAINT compliance_audit_log_subject_type_check;
ALTER TABLE compliance_audit_log
    ADD CONSTRAINT compliance_audit_log_subject_type_check
        CHECK (subject_type IN ('ASSOCIATION', 'DONATION', 'CAMPAIGN', 'ALERT', 'DECLARANT', 'SYSTEM'));
