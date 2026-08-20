-- V63 : Saisie manuelle des représentants légaux — colonne type sur beneficial_owner
-- et extension du CHECK compliance_audit_log.subject_type.
--
-- Art. R.561-3 CMF (décret n°2024-720 du 5 juillet 2024) : pour une association, tout
-- administrateur, membre de surveillance ou dirigeant est bénéficiaire effectif. La colonne
-- type distingue les représentants légaux (REPRESENTATIVE) des bénéficiaires effectifs stricts
-- (BENEFICIAL_OWNER) afin d'appliquer un gate d'approbation séparé et un contrôle de gel ciblé.
--
-- Rollback : U63__beneficial_owner_type_and_subject.sql

ALTER TABLE beneficial_owner
    ADD COLUMN type VARCHAR(20) NOT NULL DEFAULT 'BENEFICIAL_OWNER'
        CHECK (type IN ('BENEFICIAL_OWNER', 'REPRESENTATIVE'));

ALTER TABLE compliance_audit_log DROP CONSTRAINT compliance_audit_log_subject_type_check;
ALTER TABLE compliance_audit_log
    ADD CONSTRAINT compliance_audit_log_subject_type_check
        CHECK (subject_type IN (
            'ASSOCIATION', 'DONATION', 'CAMPAIGN', 'ALERT',
            'DECLARANT', 'SYSTEM', 'BENEFICIAL_OWNER', 'DONOR', 'REPRESENTATIVE'
        ));
