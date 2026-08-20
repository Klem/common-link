-- U63 : Rollback de V63__beneficial_owner_type_and_subject.sql

ALTER TABLE compliance_audit_log DROP CONSTRAINT compliance_audit_log_subject_type_check;
ALTER TABLE compliance_audit_log
    ADD CONSTRAINT compliance_audit_log_subject_type_check
        CHECK (subject_type IN (
            'ASSOCIATION', 'DONATION', 'CAMPAIGN', 'ALERT',
            'DECLARANT', 'SYSTEM', 'BENEFICIAL_OWNER', 'DONOR'
        ));

ALTER TABLE beneficial_owner DROP COLUMN type;
