-- U96 : Rollback of V96__compliance_audit_log_no_truncate.sql

DROP TRIGGER IF EXISTS trg_compliance_audit_log_no_truncate ON compliance_audit_log;
