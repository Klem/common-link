ALTER TABLE magic_link_tokens
    ADD COLUMN assoc_name        VARCHAR(255),
    ADD COLUMN assoc_identifier  VARCHAR(36),
    ADD COLUMN assoc_city        VARCHAR(100),
    ADD COLUMN assoc_postal_code VARCHAR(16);
