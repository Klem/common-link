ALTER TABLE association_profiles
    DROP CONSTRAINT chk_association_embed_type;
ALTER TABLE association_profiles
    DROP COLUMN embed_type;
