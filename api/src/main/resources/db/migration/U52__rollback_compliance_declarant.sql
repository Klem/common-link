-- U52 : Rollback de V52.
-- Sécurité : n'exécuter que si compliance_declarant est vide et qu'aucune ligne
-- de type DECLARANT n'existe dans compliance_audit_log.

DROP TABLE IF EXISTS compliance_declarant;

ALTER TABLE compliance_audit_log DROP CONSTRAINT compliance_audit_log_subject_type_check;
ALTER TABLE compliance_audit_log
    ADD CONSTRAINT compliance_audit_log_subject_type_check
        CHECK (subject_type IN ('ASSOCIATION', 'DONATION', 'CAMPAIGN', 'ALERT'));
