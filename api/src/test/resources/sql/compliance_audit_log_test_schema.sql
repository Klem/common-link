-- Provisions what Hibernate create-drop can't: the raw-SQL-only artifacts from
-- V51__compliance_audit_log.sql that no @Entity maps (see ComplianceAuditLogServiceLogicTest KDoc).
-- Plain, portable ANSI SQL — works identically on H2 and Postgres.
CREATE SEQUENCE IF NOT EXISTS compliance_audit_log_seq;
CREATE TABLE IF NOT EXISTS compliance_audit_log_lock (id SMALLINT NOT NULL PRIMARY KEY);
INSERT INTO compliance_audit_log_lock (id) SELECT 1 WHERE NOT EXISTS
    (SELECT 1 FROM compliance_audit_log_lock WHERE id = 1);
