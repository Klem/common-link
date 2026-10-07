-- Short, plain-text summary written by the association alongside the rich-text story: this is
-- the only text the impact gallery and the share card ever compose into "Ce projet a ... Vous y
-- avez contribué." -- storyText becomes rich HTML with this migration and must never be
-- interpolated raw into that sentence.
-- Default '' only backfills the handful of rows already written during Sprint 3, before this
-- field existed; the upsert form makes it mandatory going forward.
ALTER TABLE campaign_stories ADD COLUMN story_summary VARCHAR(220) NOT NULL DEFAULT '';
ALTER TABLE campaign_stories ALTER COLUMN story_summary DROP DEFAULT;

-- Binary content of images embedded in a campaign story. Own table (not 1:1 like
-- campaign_cover_images) because a story can reference several images; own UUID primary key,
-- referenced from <img src="/api/public/campaigns/{campaignId}/story-images/{id}"> inside the
-- sanitized storyText HTML.
CREATE TABLE campaign_story_images (
    id           UUID        NOT NULL DEFAULT gen_random_uuid(),
    campaign_id  UUID        NOT NULL,
    data         BYTEA       NOT NULL,
    content_type VARCHAR(100) NOT NULL,
    size_bytes   BIGINT      NOT NULL,
    created_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT pk_campaign_story_images        PRIMARY KEY (id),
    CONSTRAINT fk_campaign_story_images_campaign FOREIGN KEY (campaign_id)
        REFERENCES campaigns (id) ON DELETE CASCADE
);
CREATE INDEX idx_campaign_story_images_campaign_id ON campaign_story_images (campaign_id);
