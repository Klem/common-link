-- Rollback: remove payouts table
DROP TABLE IF EXISTS payouts;

-- Rollback: revert onchain_jobs action constraint (remove RECORD_PAYOUT)
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
    'MARK_MILESTONE_REACHED'
));
