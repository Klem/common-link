-- Composite indexes for per-campaign donor aggregate queries (Step 6).
--
-- idx_donations_campaign_confirmed: covers WHERE campaign_id = ? AND confirmed_at IS NOT NULL
-- used by donor aggregate grouping queries without a separate filter step.
--
-- idx_donations_donor_campaign: covers WHERE donor_id = ? AND campaign_id = ?
-- used by per-donor transaction list on a specific campaign.

CREATE INDEX idx_donations_campaign_confirmed ON donations (campaign_id, confirmed_at)
    WHERE confirmed_at IS NOT NULL;

CREATE INDEX idx_donations_donor_campaign ON donations (donor_id, campaign_id);
