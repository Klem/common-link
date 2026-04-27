CREATE TABLE monerium_connections
(
    id               UUID        NOT NULL DEFAULT gen_random_uuid(),
    association_id   UUID        NOT NULL,
    monerium_user_id VARCHAR(255) NOT NULL,
    access_token     TEXT        NOT NULL,
    refresh_token    TEXT        NOT NULL,
    connected_at     TIMESTAMPTZ NOT NULL,
    expires_at       TIMESTAMPTZ NOT NULL,
    CONSTRAINT pk_monerium_connections PRIMARY KEY (id),
    CONSTRAINT uq_monerium_connections_association UNIQUE (association_id),
    CONSTRAINT fk_monerium_connections_association
        FOREIGN KEY (association_id) REFERENCES association_profiles (id) ON DELETE CASCADE
);

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
