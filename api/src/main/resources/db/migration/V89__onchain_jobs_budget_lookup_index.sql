-- Budget lookup of a campaign on a chain (OnchainJobRepository.findLatestBudgetJob): its registration
-- (exact key 'REGISTER_CAMPAIGN:<id>', served by uq_onchain_jobs_chain_correlation) or its latest budget update
-- (key prefix 'UPDATE_CAMPAIGN_BUDGET:<id>:'). A LIKE prefix search needs pattern ops under a non-C collation.
-- Partial: only budget updates are indexed.
CREATE INDEX idx_onchain_jobs_budget_update_key
    ON onchain_jobs (chain_id, correlation_key varchar_pattern_ops)
    WHERE action = 'UPDATE_CAMPAIGN_BUDGET';
