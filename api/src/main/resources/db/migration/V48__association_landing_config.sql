-- Landing page customization per association: visual theme, logo and section visibility.
-- Defaults reproduce today's rendering exactly, so every existing landing page is unchanged.
ALTER TABLE association_profiles
    ADD COLUMN landing_theme             VARCHAR(20)  NOT NULL DEFAULT 'DEFAULT',
    ADD COLUMN landing_logo              VARCHAR(255),
    ADD COLUMN landing_show_project      BOOLEAN      NOT NULL DEFAULT TRUE,
    ADD COLUMN landing_show_transparency BOOLEAN      NOT NULL DEFAULT TRUE,
    ADD COLUMN landing_show_trust        BOOLEAN      NOT NULL DEFAULT TRUE;

-- Values must stay identical to the LandingTheme Kotlin enum entries.
ALTER TABLE association_profiles
    ADD CONSTRAINT chk_association_landing_theme
        CHECK (landing_theme IN ('DEFAULT', 'WARM', 'TRUST', 'NATURE', 'SOBER'));

-- Logo binaries, mirroring `campaign_cover_images`: a dedicated table (not a BYTEA column on
-- `association_profiles`) so the bytes are never loaded when a profile is fetched — a
-- @Basic(LAZY) bytea column would only stay lazy with bytecode enhancement enabled.
-- `association_profiles.landing_logo` keeps the public serving path.
CREATE TABLE association_logos (
    association_id UUID         PRIMARY KEY REFERENCES association_profiles (id) ON DELETE CASCADE,
    data           BYTEA        NOT NULL,
    content_type   VARCHAR(100) NOT NULL,
    size_bytes     BIGINT       NOT NULL,
    uploaded_at    TIMESTAMPTZ  NOT NULL
);
