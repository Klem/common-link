-- Add RECORD_PAYOUT to the onchain_jobs action check constraint
-- PostgreSQL auto-names the constraint as onchain_jobs_action_check
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

-- Payouts table
CREATE TABLE payouts (
    id             UUID         PRIMARY KEY,
    campaign_id    UUID         NOT NULL REFERENCES campaigns(id),
    payee_id       UUID         NOT NULL REFERENCES payees(id),
    payee_iban_id  UUID         NOT NULL REFERENCES payee_ibans(id),
    amount         NUMERIC(12, 2) NOT NULL,
    kind           VARCHAR(20)  NOT NULL CHECK (kind IN ('REMUNERATION', 'EXPENSE')),
    type_code      VARCHAR(50)  NOT NULL,
    label          VARCHAR(500) NOT NULL,
    status         VARCHAR(20)  NOT NULL DEFAULT 'PENDING'
                       CHECK (status IN ('PENDING', 'CONFIRMED', 'FAILED')),
    created_at     TIMESTAMPTZ  NOT NULL DEFAULT now(),
    confirmed_at   TIMESTAMPTZ,
    onchain_job_id UUID
);

-- Indexes for common access patterns
CREATE INDEX idx_payouts_campaign_status   ON payouts (campaign_id, status);
CREATE INDEX idx_payouts_campaign_created  ON payouts (campaign_id, created_at DESC);
