-- V99: the privacy policy becomes a versioned legal document (PRIVACY), accepted like the CGU/CGV.
--
-- Schema only: the PRIVACY text itself is inserted at startup by LegalDocumentSeeder from the landing
-- site's legal.politiqueConfidentialite. Values mirror org.commonlink.entity.LegalDocumentType.
--
-- Rollback: U99__legal_document_privacy.sql

ALTER TABLE legal_document DROP CONSTRAINT legal_document_type_check;
ALTER TABLE legal_document
    ADD CONSTRAINT legal_document_type_check CHECK (document_type IN ('CGU', 'CGV', 'PRIVACY'));

ALTER TABLE legal_acceptance DROP CONSTRAINT legal_acceptance_document_type_check;
ALTER TABLE legal_acceptance
    ADD CONSTRAINT legal_acceptance_document_type_check CHECK (document_type IN ('CGU', 'CGV', 'PRIVACY'));
