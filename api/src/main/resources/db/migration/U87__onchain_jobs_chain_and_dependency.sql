-- Rollback of V87.

ALTER TABLE onchain_jobs DROP CONSTRAINT IF EXISTS onchain_jobs_action_check;
ALTER TABLE onchain_jobs ADD CONSTRAINT onchain_jobs_action_check CHECK (action IN (
    'VERIFY_ASSOCIATION',
    'REVOKE_ASSOCIATION',
    'RESTORE_ASSOCIATION',
    'CREATE_CAMPAIGN',
    'PUBLISH_CAMPAIGN',
    'REVERT_CAMPAIGN_TO_DRAFT',
    'UPDATE_CAMPAIGN_BUDGET',
    'PAUSE_CAMPAIGN',
    'UNPAUSE_CAMPAIGN',
    'CANCEL_CAMPAIGN',
    'COMPLETE_CAMPAIGN',
    'RECORD_DONATION',
    'MARK_MILESTONE_REACHED',
    'RECORD_PAYOUT'
));

DROP INDEX IF EXISTS idx_onchain_jobs_depends_on;
DROP INDEX IF EXISTS idx_onchain_jobs_chain_status_created;

ALTER TABLE onchain_jobs DROP CONSTRAINT IF EXISTS fk_onchain_jobs_depends_on;
ALTER TABLE onchain_jobs DROP COLUMN IF EXISTS depends_on_job_id;

ALTER TABLE onchain_jobs DROP CONSTRAINT IF EXISTS uq_onchain_jobs_chain_correlation;
ALTER TABLE onchain_jobs ADD CONSTRAINT onchain_jobs_correlation_key_key UNIQUE (correlation_key);

ALTER TABLE onchain_jobs DROP COLUMN IF EXISTS chain_id;
