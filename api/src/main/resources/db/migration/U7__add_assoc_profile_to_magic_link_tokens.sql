ALTER TABLE magic_link_tokens
    DROP COLUMN IF EXISTS assoc_name,
    DROP COLUMN IF EXISTS assoc_identifier,
    DROP COLUMN IF EXISTS assoc_city,
    DROP COLUMN IF EXISTS assoc_postal_code;
