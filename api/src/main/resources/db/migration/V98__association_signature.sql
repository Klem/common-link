-- V98 : Signature image printed on the association's fiscal receipts (Cerfa 11580), and withdrawal of the
-- "reconnu d'utilité publique" fiscal mandate eligibility.
--
-- 1. association_signature (new table)
--
-- One row per association (shared primary key with association_profiles), written only through
-- PUT /api/association/me/signatory together with the signatory attestation, which is journaled
-- in compliance_audit_log (RECEIPT_SIGNATURE_ATTESTED).
--
-- Kept in its own table rather than as columns on association_profiles: the profile entity is
-- loaded almost everywhere and must never drag the image bytes along (same reasoning as
-- association_logos, V48).
--
-- mode: how the signature image was produced. 'UPLOAD' = image file imported, 'DRAWN' = drawn on
-- screen (PNG). Mirrors SignatureMode exactly. A signature is always an image: a name rendered in a
-- handwriting font is not a signature (fiscal receipt arbitration, 2026-11-23).
-- image / content_type: PNG or JPEG bytes and their sniffed MIME type.
-- attested_at: instant of the latest attestation (every save re-attests).
--
-- ON DELETE CASCADE: deleting a profile (remove_association.sql, dev only) removes its signature
-- with it.
--
-- 2. fiscal_mandate (V35): the 'PUBLIC_UTILITY_66' eligibility is withdrawn — the receipt does not
-- carry the recognition decree and Journal officiel dates such a receipt requires. The CHECK (named
-- by Postgres from the inline V35 definition) is recreated to mirror MandateEligibility exactly.
--
-- Schema only, no data recovery: if a fiscal_mandate row — active or revoked — has eligibility
-- 'PUBLIC_UTILITY_66', this migration fails on purpose; such rows must be handled by hand first.
--
-- Rollback : U98__association_signature.sql

CREATE TABLE association_signature (
    association_id UUID        PRIMARY KEY REFERENCES association_profiles (id) ON DELETE CASCADE,
    mode           VARCHAR(10) NOT NULL
        CONSTRAINT association_signature_mode_check CHECK (mode IN ('UPLOAD', 'DRAWN')),
    image          BYTEA       NOT NULL,
    content_type   VARCHAR(50) NOT NULL,
    attested_at    TIMESTAMPTZ NOT NULL,
    updated_at     TIMESTAMPTZ NOT NULL
);

ALTER TABLE fiscal_mandate DROP CONSTRAINT fiscal_mandate_eligibility_check;
ALTER TABLE fiscal_mandate
    ADD CONSTRAINT fiscal_mandate_eligibility_check CHECK (eligibility IN ('OIG_66', 'OIG_75_COLUCHE'));
