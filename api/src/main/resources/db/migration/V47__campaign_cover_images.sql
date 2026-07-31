-- Cover image binaries for campaigns.
-- Stored in a dedicated table (not a column on `campaigns`) so the bytes are never
-- loaded when a campaign is fetched: the association <-> image link is a LAZY
-- one-to-one, whereas a @Basic(LAZY) bytea column on `campaigns` would require
-- bytecode enhancement to actually stay lazy.
-- `campaigns.cover_image` keeps the public serving path of the image.
CREATE TABLE campaign_cover_images (
    campaign_id  UUID        PRIMARY KEY REFERENCES campaigns (id) ON DELETE CASCADE,
    data         BYTEA       NOT NULL,
    content_type VARCHAR(100) NOT NULL,
    size_bytes   BIGINT      NOT NULL,
    uploaded_at  TIMESTAMPTZ NOT NULL
);
