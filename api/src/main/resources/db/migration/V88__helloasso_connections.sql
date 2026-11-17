-- Link between an association and its HelloAsso account (API keys mode). One row per association, kept when the
-- association moves to another payment provider and on disconnection (history and notification token survive).
--
-- The HelloAsso client_secret is NEVER stored: it is used once to obtain the tokens, then discarded. Only the
-- client_id (not a secret) and the tokens are kept; refresh_token and access_token are encrypted by the application
-- (OAuthTokenConverter, AES-256-GCM) before they reach this table.
--
-- webhook_token is created before the link (the notification URL is shown to the association while it links its
-- account), so every link column is nullable while state = 'NOT_LINKED'.
CREATE TABLE helloasso_connections (
    id                         UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    association_id             UUID         NOT NULL UNIQUE REFERENCES association_profiles (id) ON DELETE CASCADE,
    webhook_token              VARCHAR(64)  NOT NULL UNIQUE,
    state                      VARCHAR(16)  NOT NULL DEFAULT 'NOT_LINKED',
    organization_slug          VARCHAR(255),
    client_id                  VARCHAR(255),
    refresh_token              TEXT,
    access_token               TEXT,
    access_token_expires_at    TIMESTAMPTZ,
    secret_wiped_at            TIMESTAMPTZ,
    can_collect                BOOLEAN      NOT NULL DEFAULT FALSE,
    ever_compliant             BOOLEAN      NOT NULL DEFAULT FALSE,
    last_eligibility_check_at  TIMESTAMPTZ,
    webhook_last_received_at   TIMESTAMPTZ,
    last_refresh_at            TIMESTAMPTZ,
    linked_at                  TIMESTAMPTZ,
    linked_by                  UUID         REFERENCES users (id) ON DELETE SET NULL,
    created_at                 TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at                 TIMESTAMPTZ  NOT NULL DEFAULT now()
);

-- Values must stay identical to the HelloAssoConnectionState Kotlin enum entries.
ALTER TABLE helloasso_connections
    ADD CONSTRAINT chk_helloasso_connection_state
        CHECK (state IN ('NOT_LINKED', 'ACTIVE', 'BROKEN', 'DISCONNECTED'));
