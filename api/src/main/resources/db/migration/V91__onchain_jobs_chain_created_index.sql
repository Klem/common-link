-- Most recent jobs of one chain, every status (OnchainJobRepository.findTop50ByChainIdOrderByCreatedAtDesc: live
-- feed of the compliance « Gestion du contrat » screen, polled every few seconds). idx_onchain_jobs_chain_status_created
-- starts with status after chain_id, so it cannot serve an ORDER BY created_at across statuses.
CREATE INDEX idx_onchain_jobs_chain_created
    ON onchain_jobs (chain_id, created_at);
