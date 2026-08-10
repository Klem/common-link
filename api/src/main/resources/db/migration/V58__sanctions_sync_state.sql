-- V58 : État de synchronisation du registre de gel (épique E4, tâche Asana 1216210853624507).
-- Rollback : U58__sanctions_sync_state.sql
--
-- 1. Étend le CHECK de compliance_audit_log.subject_type pour accepter SYSTEM.
--    Les synchronisations planifiées ne ciblent pas un objet métier unique — SYSTEM
--    est le type correct pour les événements d'infrastructure (succès / échec de sync).
--    Convention de nommage de la contrainte : identique à V52 (compliance_audit_log_subject_type_check).
ALTER TABLE compliance_audit_log DROP CONSTRAINT compliance_audit_log_subject_type_check;
ALTER TABLE compliance_audit_log
    ADD CONSTRAINT compliance_audit_log_subject_type_check
        CHECK (subject_type IN ('ASSOCIATION', 'DONATION', 'CAMPAIGN', 'ALERT', 'DECLARANT', 'SYSTEM'));

-- 2. Table d'état de synchronisation du registre.
--    Ligne unique (id = 1). Sert également de cible de verrou FOR UPDATE afin de sérialiser
--    les tentatives de synchronisation entre instances applicatives (même patron que
--    compliance_audit_log_lock, V51). last_attempt_at est mis à jour à chaque tentative,
--    réussie ou non. last_success_at n'est mis à jour qu'en cas de succès — il ne doit jamais
--    régresser. last_publication_date est la date de publication DG Trésor de la dernière
--    ingestion réussie ; c'est la "version de liste" référencée dans le journal des contrôles.
CREATE TABLE sanctions_sync_state
(
    id                    SMALLINT    NOT NULL PRIMARY KEY,
    last_attempt_at       TIMESTAMPTZ,
    last_success_at       TIMESTAMPTZ,
    last_publication_date DATE
);

INSERT INTO sanctions_sync_state (id) VALUES (1);
