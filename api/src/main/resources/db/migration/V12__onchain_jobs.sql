-- Create the table using VARCHAR + CHECK constraints instead of ENUM
CREATE TABLE onchain_jobs (
                              id              UUID PRIMARY KEY,

                              action          VARCHAR(64) NOT NULL
                                  CHECK (action IN (
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
                                      )),

                              payload_json    JSONB NOT NULL,

                              status          VARCHAR(20) NOT NULL DEFAULT 'PENDING'
                                  CHECK (status IN ('PENDING', 'RUNNING', 'DONE', 'FAILED')),

                              attempts        INT NOT NULL DEFAULT 0,
                              last_error      TEXT,
                              tx_hash         VARCHAR(66),
                              block_number    BIGINT,
                              correlation_key VARCHAR(128) UNIQUE,

                              created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
                              updated_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- Index
CREATE INDEX idx_onchain_jobs_status_created
    ON onchain_jobs (status, created_at);