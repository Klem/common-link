CREATE TABLE campaigns
(
    id               UUID          PRIMARY KEY DEFAULT gen_random_uuid(),
    association_id   UUID          NOT NULL REFERENCES association_profiles(id) ON DELETE CASCADE,
    name             VARCHAR(255)  NOT NULL,
    emoji            VARCHAR(10)   NOT NULL DEFAULT '🌍',
    description      TEXT,
    goal             NUMERIC(12,2) NOT NULL DEFAULT 0,
    raised           NUMERIC(12,2) NOT NULL DEFAULT 0,
    status           VARCHAR(20)   NOT NULL DEFAULT 'DRAFT',
    start_date       DATE,
    end_date         DATE,
    contract_address VARCHAR(255),
    created_at       TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at       TIMESTAMPTZ   NOT NULL DEFAULT now()
);

CREATE INDEX idx_campaigns_association ON campaigns(association_id);

CREATE TABLE campaign_budget_sections
(
    id          UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    campaign_id UUID        NOT NULL REFERENCES campaigns(id) ON DELETE CASCADE,
    side        VARCHAR(10) NOT NULL,
    code        VARCHAR(50) NOT NULL,
    name        VARCHAR(255) NOT NULL,
    sort_order  INT         NOT NULL DEFAULT 0,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_budget_sections_campaign ON campaign_budget_sections(campaign_id);

CREATE TABLE campaign_budget_items
(
    id         UUID          PRIMARY KEY DEFAULT gen_random_uuid(),
    section_id UUID          NOT NULL REFERENCES campaign_budget_sections(id) ON DELETE CASCADE,
    label      VARCHAR(255)  NOT NULL,
    amount     NUMERIC(12,2) NOT NULL DEFAULT 0,
    sort_order INT           NOT NULL DEFAULT 0
);

CREATE INDEX idx_budget_items_section ON campaign_budget_items(section_id);

CREATE TABLE campaign_milestones
(
    id            UUID          PRIMARY KEY DEFAULT gen_random_uuid(),
    campaign_id   UUID          NOT NULL REFERENCES campaigns(id) ON DELETE CASCADE,
    emoji         VARCHAR(10)   NOT NULL DEFAULT '🎯',
    title         VARCHAR(255)  NOT NULL,
    description   TEXT,
    target_amount NUMERIC(12,2) NOT NULL DEFAULT 0,
    status        VARCHAR(20)   NOT NULL DEFAULT 'LOCKED',
    sort_order    INT           NOT NULL DEFAULT 0,
    reached_at    TIMESTAMPTZ,
    created_at    TIMESTAMPTZ   NOT NULL DEFAULT now()
);

CREATE INDEX idx_milestones_campaign ON campaign_milestones(campaign_id);
