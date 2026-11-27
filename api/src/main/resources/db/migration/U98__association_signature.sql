-- U98 : Rollback of V98__association_signature.sql
-- Stored signatures are lost. Receipts already issued keep their image: their PDF bytes live in
-- donation_receipts and are never regenerated.
-- Restores the V35 fiscal_mandate eligibility CHECK exactly.

DROP TABLE IF EXISTS association_signature;

ALTER TABLE fiscal_mandate DROP CONSTRAINT fiscal_mandate_eligibility_check;
ALTER TABLE fiscal_mandate
    ADD CONSTRAINT fiscal_mandate_eligibility_check
        CHECK (eligibility IN ('OIG_66', 'OIG_75_COLUCHE', 'PUBLIC_UTILITY_66'));
