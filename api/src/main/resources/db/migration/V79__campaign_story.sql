-- Free-text impact narrative written by the association for one campaign (D5, option C).
-- One story per campaign (UNIQUE campaign_id) -- no version history in this sprint.
-- published_at null = draft, visible only in the association editor; set once, at publish time.
CREATE TABLE campaign_stories (
    id            UUID        NOT NULL DEFAULT gen_random_uuid(),
    campaign_id   UUID        NOT NULL,
    story_text    TEXT        NOT NULL,
    published_at  TIMESTAMPTZ,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT pk_campaign_stories         PRIMARY KEY (id),
    CONSTRAINT uq_campaign_stories_campaign UNIQUE (campaign_id),
    CONSTRAINT fk_campaign_stories_campaign FOREIGN KEY (campaign_id)
        REFERENCES campaigns (id) ON DELETE CASCADE
);
