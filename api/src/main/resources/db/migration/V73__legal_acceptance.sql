-- V73 : Preuve d'acceptation CGU/CGV (notice ACPR ; art. 1740 A CGI).
--
-- legal_document : une ligne = une version immuable d'un texte CGU/CGV. Jamais mise à jour ni
-- supprimée après publication — une nouvelle version est une nouvelle ligne.
--
-- legal_acceptance : preuve qu'un donateur ou une association a accepté une version donnée.
-- Donateur : une ligne par document à CHAQUE don (acte transactionnel, pas d'unicité). Association :
-- au plus une ligne par (association, document_type, document_version) — index unique partiel,
-- réutilisée tant que la version CGU courante ne change pas.
--
-- Rollback : U73__legal_acceptance.sql

CREATE TABLE legal_document
(
    id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    document_type VARCHAR(10)  NOT NULL
        CONSTRAINT legal_document_type_check CHECK (document_type IN ('CGU', 'CGV')),
    version       VARCHAR(32)  NOT NULL,
    content       TEXT         NOT NULL,
    published_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT legal_document_type_version_uq UNIQUE (document_type, version)
);

CREATE INDEX legal_document_type_published_idx ON legal_document (document_type, published_at DESC);

CREATE TABLE legal_acceptance
(
    id                UUID        NOT NULL DEFAULT gen_random_uuid() PRIMARY KEY,
    subject_type      VARCHAR(16) NOT NULL
        CONSTRAINT legal_acceptance_subject_type_check CHECK (subject_type IN ('DONOR', 'ASSOCIATION')),
    subject_id        UUID        NOT NULL,
    document_type     VARCHAR(10) NOT NULL
        CONSTRAINT legal_acceptance_document_type_check CHECK (document_type IN ('CGU', 'CGV')),
    document_version  VARCHAR(32) NOT NULL,
    accepted_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
    signer_name       VARCHAR(255),
    signer_email      VARCHAR(255),
    donation_id       UUID,
    campaign_id       UUID
);

CREATE UNIQUE INDEX legal_acceptance_association_version_uq
    ON legal_acceptance (subject_id, document_type, document_version)
    WHERE subject_type = 'ASSOCIATION';

CREATE INDEX legal_acceptance_subject_idx ON legal_acceptance (subject_type, subject_id, accepted_at DESC);

-- Placeholder — texte juridique NON validé par le service juridique. À remplacer avant toute mise
-- en production réelle (voir .tasks/todo.md). Sans cette ligne, le contrôle de publication de
-- campagne n'aurait aucune version courante à faire accepter et bloquerait toute publication.
INSERT INTO legal_document (document_type, version, content)
VALUES ('CGU', '2026-08-26',
        '⚠️ PLACEHOLDER — ce texte n''a pas été rédigé ni validé par le service juridique et ne doit jamais être présenté à un utilisateur réel. Conditions Générales d''Utilisation de CommonLink — à rédiger.'),
       ('CGV', '2026-08-26',
        '⚠️ PLACEHOLDER — ce texte n''a pas été rédigé ni validé par le service juridique et ne doit jamais être présenté à un utilisateur réel. Conditions Générales de Vente de CommonLink — à rédiger.');
