-- U99: Rollback of V99__legal_document_privacy.sql
-- Fails while PRIVACY rows exist in legal_document or legal_acceptance: they are acceptance proofs and are
-- never deleted by a rollback script.

ALTER TABLE legal_acceptance DROP CONSTRAINT legal_acceptance_document_type_check;
ALTER TABLE legal_acceptance
    ADD CONSTRAINT legal_acceptance_document_type_check CHECK (document_type IN ('CGU', 'CGV'));

ALTER TABLE legal_document DROP CONSTRAINT legal_document_type_check;
ALTER TABLE legal_document
    ADD CONSTRAINT legal_document_type_check CHECK (document_type IN ('CGU', 'CGV'));
