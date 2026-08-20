-- Rollback of V67: recreate monerium_connections / monerium_oauth_states in their final
-- pre-removal shape (V11 + V13 + V14 + V15 + V16 + the two V18 indexes).
CREATE TABLE monerium_connections
(
    id                    UUID        NOT NULL DEFAULT gen_random_uuid(),
    association_id        UUID        NOT NULL,
    monerium_profile_id   VARCHAR(255),
    monerium_profile_name VARCHAR(255),
    access_token          TEXT        NOT NULL,
    refresh_token         TEXT        NOT NULL,
    connected_at          TIMESTAMPTZ NOT NULL,
    expires_at            TIMESTAMPTZ NOT NULL,
    state                 VARCHAR(16) NOT NULL DEFAULT 'ACTIVE',
    wallet_address        VARCHAR(42),
    wallet_chain          VARCHAR(32),
    CONSTRAINT pk_monerium_connections PRIMARY KEY (id),
    CONSTRAINT uq_monerium_connections_association UNIQUE (association_id),
    CONSTRAINT fk_monerium_connections_association
        FOREIGN KEY (association_id) REFERENCES association_profiles (id) ON DELETE CASCADE
);

CREATE UNIQUE INDEX idx_monerium_wallet_address
    ON monerium_connections (wallet_address) WHERE wallet_address IS NOT NULL;

CREATE TABLE monerium_oauth_states
(
    state          VARCHAR(255) NOT NULL,
    code_verifier  TEXT         NOT NULL,
    association_id UUID         NOT NULL,
    expires_at     TIMESTAMPTZ  NOT NULL,
    CONSTRAINT pk_monerium_oauth_states PRIMARY KEY (state),
    CONSTRAINT fk_monerium_oauth_states_association
        FOREIGN KEY (association_id) REFERENCES association_profiles (id) ON DELETE CASCADE
);

CREATE INDEX idx_monerium_oauth_states_expires   ON monerium_oauth_states (expires_at);
CREATE INDEX idx_monerium_oauth_states_assoc_exp ON monerium_oauth_states (association_id, expires_at);
