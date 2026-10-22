-- Registry v2 outbox: per-chain jobs, job dependencies, registration actions.
--
-- chain_id: chain the job targets (0 = mock / synthetic receipts).
-- Idempotency is scoped per chain: the same business fact (e.g. 'DONATION:<uuid>') can be
-- recorded once on each chain (mock, Chiado, Gnosis mainnet).
ALTER TABLE onchain_jobs ADD COLUMN chain_id BIGINT NOT NULL DEFAULT 0;

ALTER TABLE onchain_jobs DROP CONSTRAINT IF EXISTS onchain_jobs_correlation_key_key;
ALTER TABLE onchain_jobs
    ADD CONSTRAINT uq_onchain_jobs_chain_correlation UNIQUE (chain_id, correlation_key);

-- depends_on_job_id: a job is claimable only once this prerequisite job is DONE
-- (campaign registration -> association registration; donation / payout / budget -> campaign registration).
ALTER TABLE onchain_jobs
    ADD COLUMN depends_on_job_id UUID NULL
        CONSTRAINT fk_onchain_jobs_depends_on REFERENCES onchain_jobs (id);

CREATE INDEX idx_onchain_jobs_chain_status_created ON onchain_jobs (chain_id, status, created_at);
CREATE INDEX idx_onchain_jobs_depends_on ON onchain_jobs (depends_on_job_id);

-- Registry v2 actions only: association and campaign are "registered" once, without lifecycle
-- (no statuses, no revoke/restore, no milestones on-chain).
ALTER TABLE onchain_jobs DROP CONSTRAINT IF EXISTS onchain_jobs_action_check;
ALTER TABLE onchain_jobs ADD CONSTRAINT onchain_jobs_action_check CHECK (action IN (
    'REGISTER_ASSOCIATION',
    'REGISTER_CAMPAIGN',
    'UPDATE_CAMPAIGN_BUDGET',
    'RECORD_DONATION',
    'RECORD_PAYOUT'
));
