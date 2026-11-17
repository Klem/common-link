-- V96 : Refuses TRUNCATE on compliance_audit_log with a statement-level trigger (audit 2026-11-12, H6).
--
-- V51 relies on REVOKE TRUNCATE alone, its comment stating that a trigger cannot intercept TRUNCATE. That is
-- wrong: PostgreSQL fires BEFORE TRUNCATE triggers declared FOR EACH STATEMENT. Since the application role owns
-- the table, it can grant itself TRUNCATE back; this trigger is a second barrier, as the row-level trigger of V51
-- is for UPDATE and DELETE. V51 is already applied everywhere and is not edited.
--
-- What stays open (documented in docs/legal/E4-journal-controles-de-gel.md): the table owner can still drop
-- either trigger, as long as Flyway and the application share one database role.
--
-- Rollback : U96__compliance_audit_log_no_truncate.sql

CREATE TRIGGER trg_compliance_audit_log_no_truncate
    BEFORE TRUNCATE
    ON compliance_audit_log
    FOR EACH STATEMENT
EXECUTE FUNCTION compliance_audit_log_immutable();
