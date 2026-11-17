-- V97 : Structured CGU/CGV content, injected at startup from the landing site's texts.
--
-- content_format: how `content` must be read. 'TEXT' = plain text (the V73 placeholder rows, left untouched);
-- 'JSON_V1' = the structured JSON produced by LegalDocumentSeeder from landing-page/messages/fr.json
-- (title, intro, articles, paragraphs as text segments with optional links — no HTML, no CSS).
--
-- content_sha256: SHA-256 of `content`, set for every JSON_V1 row. Its first 12 hex characters are part of the
-- version identifier ("2026-09-09-a1b2c3d4e5f6"), so two texts published the same day get two versions; the full
-- hash is compared at startup to detect a prefix collision. Null on the V73 placeholder rows.
--
-- Schema only: existing rows keep their content and default to 'TEXT'.
--
-- Rollback : U97__legal_document_structured_content.sql

ALTER TABLE legal_document
    ADD COLUMN content_format VARCHAR(16) NOT NULL DEFAULT 'TEXT'
        CONSTRAINT legal_document_content_format_check CHECK (content_format IN ('TEXT', 'JSON_V1'));

ALTER TABLE legal_document
    ADD COLUMN content_sha256 VARCHAR(64);
