-- V59 : Contrôle de gel à l'entrée en relation (épique E4, tâche Asana 1216210853624505).
-- Étend le CHECK de compliance_audit_log.subject_type pour accepter BENEFICIAL_OWNER.
--
-- Les bénéficiaires effectifs sont des entités distinctes des associations et des déclarants :
-- ils possèdent leur propre UUID (beneficial_owner.id) et sont soumis individuellement au
-- contrôle de gel (art. L.561-5 CMF). Un type sujet dédié rend chaque événement d'audit
-- directement traçable vers la ligne du registre concernée, sans ambiguïté avec ASSOCIATION
-- ou DECLARANT.
--
-- Rollback : U59__freeze_screening_onboarding.sql
ALTER TABLE compliance_audit_log DROP CONSTRAINT compliance_audit_log_subject_type_check;
ALTER TABLE compliance_audit_log
    ADD CONSTRAINT compliance_audit_log_subject_type_check
        CHECK (subject_type IN ('ASSOCIATION', 'DONATION', 'CAMPAIGN', 'ALERT', 'DECLARANT', 'SYSTEM', 'BENEFICIAL_OWNER'));
