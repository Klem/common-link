CREATE TYPE onchain_job_status AS ENUM ('PENDING','RUNNING','DONE','FAILED');
CREATE TYPE onchain_job_action AS ENUM (
    'VERIFY_ASSOCIATION','REVOKE_ASSOCIATION','RESTORE_ASSOCIATION',
    'CREATE_CAMPAIGN','PUBLISH_CAMPAIGN','REVERT_CAMPAIGN_TO_DRAFT','UPDATE_CAMPAIGN_BUDGET',
    'PAUSE_CAMPAIGN','UNPAUSE_CAMPAIGN','CANCEL_CAMPAIGN','COMPLETE_CAMPAIGN',
    'RECORD_DONATION','MARK_MILESTONE_REACHED'
);

CREATE TABLE onchain_jobs (
    id              UUID PRIMARY KEY,
    action          onchain_job_action       NOT NULL,
    payload_json    JSONB                    NOT NULL,
    status          onchain_job_status       NOT NULL DEFAULT 'PENDING',
    attempts        INT                      NOT NULL DEFAULT 0,
    last_error      TEXT,
    tx_hash         VARCHAR(66),
    block_number    BIGINT,
    correlation_key VARCHAR(128) UNIQUE,
    created_at      TIMESTAMPTZ              NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ              NOT NULL DEFAULT now()
);

CREATE INDEX idx_onchain_jobs_status_created ON onchain_jobs (status, created_at);
