-- Composite (parent_id, sort_order) indexes for campaign child collections.
--
-- Previously, filter-by-parent queries (WHERE campaign_id = ?) used the single-column index
-- but ORDER BY sort_order still required a separate sort step.  A composite index with
-- sort_order as the second column serves both the filter and the order in one index scan.
--
-- The three old single-column indexes are dropped because the composite's leading column
-- (campaign_id / section_id) covers every query that the single-column index covered.
-- Trade-off: each index is slightly larger (one extra integer column per row); the gain is
-- the elimination of an explicit sort node on every campaign detail and reorder request.

-- campaign_milestones
DROP INDEX idx_milestones_campaign;
CREATE INDEX idx_milestones_campaign_sort ON campaign_milestones (campaign_id, sort_order);

-- campaign_budget_sections
DROP INDEX idx_budget_sections_campaign;
CREATE INDEX idx_budget_sections_campaign_sort ON campaign_budget_sections (campaign_id, sort_order);

-- campaign_budget_items
DROP INDEX idx_budget_items_section;
CREATE INDEX idx_budget_items_section_sort ON campaign_budget_items (section_id, sort_order);
