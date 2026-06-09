-- V18: missing indexes for query patterns identified during performance review

-- monerium_oauth_states: cleanup job (deleteAllByExpiresAtBefore) and pending-flow check
CREATE INDEX idx_monerium_oauth_states_expires     ON monerium_oauth_states (expires_at);
CREATE INDEX idx_monerium_oauth_states_assoc_exp   ON monerium_oauth_states (association_id, expires_at);

-- email_verification_tokens: rate-limit guard (countByUserIdAndCreatedAtAfter)
CREATE INDEX idx_evtokens_user_created             ON email_verification_tokens (user_id, created_at);

-- magic_link_tokens: rate-limit guard (countByEmailAndCreatedAtAfter)
CREATE INDEX idx_ml_tokens_email_created           ON magic_link_tokens (email, created_at);
