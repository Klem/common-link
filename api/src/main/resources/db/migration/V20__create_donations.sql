CREATE TABLE donations (
    id              UUID          PRIMARY KEY DEFAULT gen_random_uuid(),
    donor_id        UUID          NOT NULL REFERENCES donor_profiles(id) ON DELETE CASCADE,
    campaign_id     UUID          NOT NULL REFERENCES campaigns(id) ON DELETE CASCADE,
    amount          NUMERIC(12,2) NOT NULL,
    provider_ref    VARCHAR(255)  NOT NULL,
    confirmed_at    TIMESTAMPTZ,
    created_at      TIMESTAMPTZ   NOT NULL DEFAULT now()
);
CREATE INDEX idx_donations_donor ON donations(donor_id);
CREATE INDEX idx_donations_campaign ON donations(campaign_id);
