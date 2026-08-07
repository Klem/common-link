-- V52 : Référentiel des déclarants TRACFIN (volet dev de E1-2 / tâche Asana 1216210976716058).
-- Rollback: U52__rollback_compliance_declarant.sql
--
-- Étend le CHECK de subject_type pour accepter DECLARANT, puis crée compliance_declarant :
-- registre chiffré des personnes habilitées à déposer une déclaration de soupçon sur ERMES.
-- Les colonnes teledeclarant_number et full_name sont chiffrées en base applicative par
-- ComplianceCryptoConverter (AES-256-GCM, IV aléatoire par écriture) ; aucun index n'est
-- posé dessus — un IV aléatoire rend un index d'égalité sémantiquement inutile.
-- La désignation n'est jamais supprimée physiquement : revoked_at NULL = actif, non-NULL = révoqué.

-- 1. Étendre le CHECK de compliance_audit_log.subject_type
--    Nom dérivé de la convention Postgres pour un CHECK inline sans CONSTRAINT :
--    <table>_<column>_check. Pas de IF EXISTS — une erreur de nom doit interrompre le
--    déploiement plutôt que créer silencieusement une seconde contrainte.
ALTER TABLE compliance_audit_log DROP CONSTRAINT compliance_audit_log_subject_type_check;
ALTER TABLE compliance_audit_log
    ADD CONSTRAINT compliance_audit_log_subject_type_check
        CHECK (subject_type IN ('ASSOCIATION', 'DONATION', 'CAMPAIGN', 'ALERT', 'DECLARANT'));

-- 2. Registre des déclarants TRACFIN
CREATE TABLE compliance_declarant
(
    id                   UUID        NOT NULL DEFAULT gen_random_uuid() PRIMARY KEY,
    user_id              UUID        NOT NULL UNIQUE REFERENCES users (id),
    teledeclarant_number TEXT        NOT NULL,
    full_name            TEXT        NOT NULL,
    designated_at        DATE        NOT NULL,
    revoked_at           DATE,
    created_at           TIMESTAMPTZ NOT NULL DEFAULT now()
);
