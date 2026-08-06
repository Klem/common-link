-- V51 : Journal d'audit LCB-FT append-only, chaîné par hash SHA-256.
-- Rollback: U51__rollback_compliance_audit_log.sql
--
-- Substrat commun réutilisé par les prompts suivants (contrôles de gel, revue d'alertes,
-- traçage des refus de publication) — voir le service ComplianceAuditLogService pour le
-- point d'écriture unique et le contrat anti-duplication.
--
-- event_type reste VARCHAR libre, SANS contrainte CHECK : ce journal est partagé par des
-- features qui n'existent pas encore (le CHECK serait à modifier à chaque nouvelle feature,
-- ce qui recréerait le couplage que ce socle est censé éliminer). subject_type, en revanche,
-- est un ensemble fini connu aujourd'hui (les types d'objets métier de la plateforme) et
-- porte un CHECK aligné sur ComplianceAuditSubjectType.

CREATE SEQUENCE compliance_audit_log_seq;

-- Single-row lock target for ComplianceAuditLogService's write serialization: a portable
-- `SELECT ... FOR UPDATE` instead of `pg_advisory_xact_lock`, which has no equivalent outside
-- Postgres. Advisory locks are a Postgres-specific optimization over exactly this pattern.
CREATE TABLE compliance_audit_log_lock
(
    id SMALLINT NOT NULL PRIMARY KEY
);
INSERT INTO compliance_audit_log_lock (id) VALUES (1);

CREATE TABLE compliance_audit_log
(
    id             UUID        NOT NULL DEFAULT gen_random_uuid() PRIMARY KEY,
    sequence_no    BIGINT      NOT NULL UNIQUE,
    event_type     VARCHAR(64) NOT NULL,
    subject_type   VARCHAR(32) NOT NULL
        CHECK (subject_type IN ('ASSOCIATION', 'DONATION', 'CAMPAIGN', 'ALERT')),
    subject_id     UUID,
    payload        TEXT        NOT NULL,
    actor_user_id  UUID,
    occurred_at    TIMESTAMPTZ NOT NULL,
    prev_hash      CHAR(64),
    row_hash       CHAR(64)    NOT NULL
);

CREATE INDEX idx_compliance_audit_log_subject ON compliance_audit_log (subject_type, subject_id);
CREATE INDEX idx_compliance_audit_log_event_type ON compliance_audit_log (event_type);

-- ── Immuabilité au niveau du SGBD ────────────────────────────────────────────
-- CURRENT_USER plutôt qu'un nom de rôle en dur : le rôle applicatif diffère par
-- environnement (nom fixe en local, provisionné par l'add-on en staging/prod) et Flyway
-- s'exécute déjà sous ce même rôle. Protège tant que personne ne re-GRANT ces droits ;
-- le trigger ci-dessous survit à ce cas (et à TRUNCATE, qu'un trigger BEFORE DELETE
-- classique ne peut pas intercepter — d'où le REVOKE explicite sur TRUNCATE aussi).
REVOKE UPDATE, DELETE, TRUNCATE ON compliance_audit_log FROM CURRENT_USER;

CREATE FUNCTION compliance_audit_log_immutable() RETURNS TRIGGER AS
$$
BEGIN
    RAISE EXCEPTION 'compliance_audit_log is append-only: % is not permitted', TG_OP;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_compliance_audit_log_immutable
    BEFORE UPDATE OR DELETE
    ON compliance_audit_log
    FOR EACH ROW
EXECUTE FUNCTION compliance_audit_log_immutable();
