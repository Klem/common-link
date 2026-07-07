-- V35 : Association settings — KYC verification status, new profile fields, documents, fiscal mandate
-- Rollback: U35__rollback_association_settings.sql

-- ── 1. Extend association_profiles ───────────────────────────────────────────

ALTER TABLE association_profiles
    ADD COLUMN rna              VARCHAR(20),
    ADD COLUMN creation_year    SMALLINT,
    ADD COLUMN contact_email    VARCHAR(255),
    ADD COLUMN phone            VARCHAR(30),
    ADD COLUMN verification_status           VARCHAR(20) NOT NULL DEFAULT 'UNVERIFIED',
    ADD COLUMN verification_rejection_reason TEXT,
    ADD COLUMN verification_submitted_at     TIMESTAMPTZ,
    ADD COLUMN verified_at                   TIMESTAMPTZ;

-- Backfill: preserve existing KYC approvals
UPDATE association_profiles SET verification_status = 'VERIFIED' WHERE verified = true;

ALTER TABLE association_profiles DROP COLUMN verified;

-- ── 2. Documents (KYC + mandat) ───────────────────────────────────────────────

CREATE TABLE association_document (
    id           UUID        NOT NULL DEFAULT gen_random_uuid() PRIMARY KEY,
    association_id UUID      NOT NULL REFERENCES association_profiles(id),
    doc_type     VARCHAR(30) NOT NULL,
    category     VARCHAR(20),
    file_name    VARCHAR(255) NOT NULL,
    content_type VARCHAR(100) NOT NULL,
    size_bytes   BIGINT      NOT NULL,
    content      BYTEA       NOT NULL,
    uploaded_at  TIMESTAMPTZ NOT NULL
);

-- One document per (association, type) except OPTIONAL docs (which can be multiple)
CREATE UNIQUE INDEX uidx_association_document_unique_type
    ON association_document(association_id, doc_type)
    WHERE doc_type <> 'OPTIONAL';

CREATE INDEX idx_association_document_association_id
    ON association_document(association_id);

-- ── 3. Fiscal mandate ─────────────────────────────────────────────────────────

CREATE SEQUENCE fiscal_mandate_ref_seq START 1;

CREATE TABLE fiscal_mandate (
    id             UUID        NOT NULL DEFAULT gen_random_uuid() PRIMARY KEY,
    association_id UUID        NOT NULL REFERENCES association_profiles(id),
    eligibility    VARCHAR(30) NOT NULL,
    reference      VARCHAR(20) NOT NULL UNIQUE,
    signed_at      TIMESTAMPTZ NOT NULL,
    revoked_at     TIMESTAMPTZ
);

-- At most one active (non-revoked) mandate per association; revoked rows are kept for history
CREATE UNIQUE INDEX uidx_fiscal_mandate_active
    ON fiscal_mandate(association_id)
    WHERE revoked_at IS NULL;

CREATE INDEX idx_fiscal_mandate_association_id
    ON fiscal_mandate(association_id);
