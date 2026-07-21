-- Adds fields required for Cerfa 2041-RD fiscal receipt generation
ALTER TABLE association_profiles
    ADD COLUMN address_line1 VARCHAR(255),
    ADD COLUMN legal_object  TEXT,
    ADD COLUMN signer_name   VARCHAR(255),
    ADD COLUMN signer_role   VARCHAR(100);
