DROP INDEX IF EXISTS idx_milestones_campaign_sort;
CREATE INDEX idx_milestones_campaign ON campaign_milestones (campaign_id);

DROP INDEX IF EXISTS idx_budget_sections_campaign_sort;
CREATE INDEX idx_budget_sections_campaign ON campaign_budget_sections (campaign_id);

DROP INDEX IF EXISTS idx_budget_items_section_sort;
CREATE INDEX idx_budget_items_section ON campaign_budget_items (section_id);
