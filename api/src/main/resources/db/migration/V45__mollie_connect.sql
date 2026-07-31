CREATE TABLE mollie_oauth_states (
    state          VARCHAR(255) PRIMARY KEY,
    association_id UUID         NOT NULL REFERENCES association_profiles (id) ON DELETE CASCADE,
    expires_at     TIMESTAMPTZ  NOT NULL
);
CREATE INDEX idx_mollie_oauth_states_association ON mollie_oauth_states (association_id);

CREATE TABLE mollie_connections (
    id                      UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    association_id          UUID         NOT NULL UNIQUE REFERENCES association_profiles (id) ON DELETE CASCADE,
    access_token            TEXT         NOT NULL,
    refresh_token           TEXT         NOT NULL,
    connected_at            TIMESTAMPTZ  NOT NULL DEFAULT now(),
    expires_at              TIMESTAMPTZ  NOT NULL,
    state                   VARCHAR(16)  NOT NULL DEFAULT 'ACTIVE'
                            CHECK (state IN ('ACTIVE', 'BROKEN')),
    onboarding_status       VARCHAR(16)  NOT NULL DEFAULT 'NEEDS_DATA'
                            CHECK (onboarding_status IN ('NEEDS_DATA', 'IN_REVIEW', 'COMPLETED')),
    can_receive_payments    BOOLEAN      NOT NULL DEFAULT FALSE,
    can_receive_settlements BOOLEAN      NOT NULL DEFAULT FALSE,
    mollie_organization_id  VARCHAR(255),
    last_synced_at          TIMESTAMPTZ
);
CREATE UNIQUE INDEX uq_mollie_connections_organization
    ON mollie_connections (mollie_organization_id)
    WHERE mollie_organization_id IS NOT NULL;
