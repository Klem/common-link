-- V64 : Preuve de correspondance au registre de gel + alerte d'indisponibilité de criblage.
--
-- ## Pourquoi cette table
--
-- Le journal de conformité (compliance_audit_log) ne conserve, pour un FREEZE_SCREENING_HIT, que
-- des agrégats : matchCount, topScore, scoreThreshold, registryPublicationDate. Le responsable de
-- conformité doit prononcer une décision motivée (art. L.561-12 CMF) sans savoir QUELLE entité du
-- registre a été rapprochée, ni sur quelle valeur criblée. La décision n'est pas motivable.
--
-- ## Pourquoi une table distincte du journal
--
-- La fiche docs/legal/E4-journal-controles-de-gel.md §4.5 exclut structurellement toute identité
-- en clair du journal, au motif que « l'identité complète est déjà conservée dans les dossiers
-- d'identification auxquels la référence interne renvoie ». Ce motif ne vaut que pour le SUJET
-- criblé. Une entrée du registre de gel n'est dans aucun dossier CommonLink : c'est une
-- publication officielle publique (règlements UE, RCSNU, gel national).
--
-- Le journal reste donc inchangé — son jeu de champs identity-free est vérifié par assertion.
-- Les éléments porteurs d'identité (screened_normalized_name) vivent ici, dans une table à
-- finalité et durée de conservation propres : 5 ans (art. L.561-12 CMF).
--
-- ## Pourquoi des snapshots et non des jointures
--
-- SanctionSyncExecutor supprime les entrées radiées du registre (findByIdRegistreNotIn). Une
-- jointure vers sanctioned_entity ferait disparaître la preuve d'une décision passée le jour de
-- la radiation. Les colonnes matched_* sont donc figées à l'instant du criblage.
--
-- Rollback : U64__freeze_screening_match.sql

CREATE TABLE freeze_screening_match
(
    id                        UUID             NOT NULL DEFAULT gen_random_uuid() PRIMARY KEY,

    -- Ancrage sur l'événement immuable du journal qui a constaté la correspondance.
    -- Pas de FK : compliance_audit_log est protégée par REVOKE + trigger, une FK y ajouterait
    -- un verrou de référence sur une table volontairement inaltérable.
    audit_log_seq_ref         BIGINT           NOT NULL,

    -- Sujet réellement criblé (association, représentant, bénéficiaire effectif, donateur).
    subject_type              VARCHAR(32)      NOT NULL
        CONSTRAINT freeze_screening_match_subject_type_check
            CHECK (subject_type IN ('ASSOCIATION', 'DECLARANT', 'REPRESENTATIVE', 'BENEFICIAL_OWNER', 'DONOR')),
    subject_id                UUID             NOT NULL,

    -- Contexte de regroupement : permet de rattacher à une alerte portée par l'association
    -- les correspondances dont le sujet est un représentant ou un bénéficiaire effectif.
    -- NULL pour un criblage donateur, qui n'a pas de contexte association.
    association_id            UUID,

    -- Valeur effectivement comparée, après NameNormalizer.normalize().
    -- Figée : c'est elle qui a produit le score, et elle doit survivre à une correction
    -- ultérieure du nom au dossier. « TECHNO + » est stocké « TECHNO ».
    screened_normalized_name  VARCHAR(512)     NOT NULL,

    -- Correspondance : référence publique + snapshot des attributs du registre.
    sanctioned_id_registre    INTEGER          NOT NULL,
    matched_name              VARCHAR(512)     NOT NULL,
    matched_nature            VARCHAR(32)      NOT NULL
        CONSTRAINT freeze_screening_match_nature_check
            CHECK (matched_nature IN ('PHYSICAL_PERSON', 'LEGAL_ENTITY', 'VESSEL')),
    matched_legal_reference   VARCHAR(256),
    matched_date_of_birth     VARCHAR(32),

    -- Conditions du rapprochement, pour que l'officier puisse apprécier le score.
    score                     DOUBLE PRECISION NOT NULL,
    score_threshold           DOUBLE PRECISION NOT NULL,
    algorithm                 VARCHAR(64)      NOT NULL
        CONSTRAINT freeze_screening_match_algorithm_check
            CHECK (algorithm IN ('JARO_WINKLER')),
    registry_publication_date DATE             NOT NULL,

    created_at                TIMESTAMPTZ      NOT NULL
);

CREATE INDEX idx_freeze_screening_match_subject ON freeze_screening_match (subject_type, subject_id);
CREATE INDEX idx_freeze_screening_match_association ON freeze_screening_match (association_id);
CREATE INDEX idx_freeze_screening_match_seq ON freeze_screening_match (audit_log_seq_ref);

COMMENT ON TABLE freeze_screening_match IS
    'Preuve de correspondance au registre de gel. Conservation 5 ans (art. L.561-12 CMF). '
        'Snapshots figés : les colonnes matched_* survivent à la radiation de l''entrée du registre.';

-- Alerte d'indisponibilité de criblage.
--
-- FREEZE_SCREENING_UNAVAILABLE est journalisé mais ne remontait à aucune surface : le responsable
-- de conformité ne voyait jamais qu'un contrôle avait été empêché. La fiche
-- docs/legal/E4-journal-controles-de-gel.md §4.4 impose d'enregistrer les échecs précisément
-- parce qu'un journal silencieux en cas d'échec est trompeur — la surface d'alerte réintroduisait
-- ce silence.
ALTER TABLE compliance_alert DROP CONSTRAINT compliance_alert_origin_check;
ALTER TABLE compliance_alert
    ADD CONSTRAINT compliance_alert_origin_check
        CHECK (origin IN (
                          'FREEZE_HIT_ONBOARDING',
                          'FREEZE_HIT_DONATION',
                          'SCREENING_UNAVAILABLE',
                          'SYNC_FAILURE',
                          'SPLIT_DETECTION',
                          'ATYPICALITY_RULE'
            ));
