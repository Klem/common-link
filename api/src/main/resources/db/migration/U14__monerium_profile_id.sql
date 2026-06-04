ALTER TABLE monerium_connections
    RENAME COLUMN monerium_profile_id TO monerium_user_id;

ALTER TABLE monerium_connections
    DROP COLUMN monerium_profile_name;
