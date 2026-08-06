-- U51 : Rollback of V51__compliance_audit_log.sql

DROP TABLE IF EXISTS compliance_audit_log;
DROP FUNCTION IF EXISTS compliance_audit_log_immutable();
DROP SEQUENCE IF EXISTS compliance_audit_log_seq;
DROP TABLE IF EXISTS compliance_audit_log_lock;
