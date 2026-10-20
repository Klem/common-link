-- Diffusion method the association picked in the settings "Diffusion" tab. NULL = never picked:
-- the settings UI then falls back to its own default. Not read by any public endpoint.
ALTER TABLE association_profiles
    ADD COLUMN embed_type VARCHAR(10);

-- Values must stay identical to the EmbedType Kotlin enum entries.
ALTER TABLE association_profiles
    ADD CONSTRAINT chk_association_embed_type
        CHECK (embed_type IN ('DIRECT', 'SCRIPT', 'IFRAME', 'HTML', 'WIDGET'));
