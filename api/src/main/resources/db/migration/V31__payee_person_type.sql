-- Add payee_type discriminator; make identifier_1 nullable for physical persons.
-- Drop the unconditional unique constraint and replace with a partial unique index
-- that only enforces uniqueness when identifier_1 is present (COMPANY type).

ALTER TABLE payees
    ADD COLUMN payee_type VARCHAR(10) NOT NULL DEFAULT 'COMPANY'
        CONSTRAINT payees_type_check CHECK (payee_type IN ('COMPANY', 'PERSON'));

ALTER TABLE payees
    ALTER COLUMN identifier_1 DROP NOT NULL;

ALTER TABLE payees
    DROP CONSTRAINT IF EXISTS payees_asso_identifier1_unique;

CREATE UNIQUE INDEX payees_asso_identifier1_unique
    ON payees (association_id, identifier_1)
    WHERE identifier_1 IS NOT NULL;
