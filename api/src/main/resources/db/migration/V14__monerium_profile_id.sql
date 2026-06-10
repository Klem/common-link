ALTER TABLE monerium_connections
    RENAME COLUMN monerium_user_id TO monerium_profile_id;

ALTER TABLE monerium_connections
    ADD COLUMN monerium_profile_name VARCHAR(255);
