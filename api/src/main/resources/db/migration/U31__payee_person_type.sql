DROP INDEX IF EXISTS payees_asso_identifier1_unique;

ALTER TABLE payees
    ADD CONSTRAINT payees_asso_identifier1_unique UNIQUE (association_id, identifier_1);

ALTER TABLE payees
    ALTER COLUMN identifier_1 SET NOT NULL;

ALTER TABLE payees
    DROP COLUMN IF EXISTS payee_type;
