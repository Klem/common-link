-- =============================================================
-- CommonLink — create_db.sql
-- Schéma consolidé : état final équivalent aux migrations V1 → V32.
--
-- Ce script remplace l'exécution séquentielle des 27 migrations Flyway
-- par les formes finales : les ALTER / RENAME / DROP INDEX intermédiaires
-- sont supprimés, seules les définitions résultantes subsistent.
--
-- À utiliser pour : créer un schéma neuf rapidement (tests, CI, dev local).
-- À NE PAS committer dans db/migration — ce n'est pas une migration Flyway.
--
-- Pré-requis : extension pgcrypto pour gen_random_uuid().
-- =============================================================

CREATE EXTENSION IF NOT EXISTS pgcrypto;

-- =============================================================
-- 1. UTILISATEURS & AUTHENTIFICATION
-- =============================================================

-- users  (V1 ; role étendu CURATOR en V21)
CREATE TABLE users
(
    id             uuid         NOT NULL DEFAULT gen_random_uuid(),
    email          varchar(255) NOT NULL,
    role           varchar(20)  NOT NULL,
    provider       varchar(20)  NOT NULL,
    password_hash  text,
    google_sub     varchar(255),
    display_name   varchar(255),
    avatar_url     text,
    email_verified boolean      NOT NULL DEFAULT false,
    created_at     timestamptz  NOT NULL DEFAULT now(),
    updated_at     timestamptz  NOT NULL DEFAULT now(),

    CONSTRAINT users_pkey PRIMARY KEY (id),
    CONSTRAINT users_email_unique UNIQUE (email),
    CONSTRAINT users_role_check CHECK (role IN ('DONOR', 'ASSOCIATION', 'CURATOR')),
    CONSTRAINT users_provider_check CHECK (provider IN ('EMAIL', 'GOOGLE', 'MAGIC_LINK'))
);

-- V25 a supprimé l'index plain users_email_idx (redondant avec users_email_unique) : non recréé.
-- V22 : index unique partiel sur google_sub.
CREATE UNIQUE INDEX idx_users_google_sub ON users (google_sub) WHERE google_sub IS NOT NULL;

-- magic_link_tokens  (V2 ; colonnes assoc_* ajoutées en V7)
CREATE TABLE magic_link_tokens
(
    id                uuid         NOT NULL DEFAULT gen_random_uuid(),
    email             varchar(255) NOT NULL,
    token_hash        varchar(64)  NOT NULL, -- SHA-256 hex du token opaque
    role              varchar(20)  NOT NULL,
    expires_at        timestamptz  NOT NULL,
    used_at           timestamptz,
    created_at        timestamptz  NOT NULL DEFAULT now(),
    assoc_name        varchar(255),
    assoc_identifier  varchar(36),
    assoc_city        varchar(100),
    assoc_postal_code varchar(16),

    CONSTRAINT magic_link_tokens_pkey PRIMARY KEY (id),
    CONSTRAINT magic_link_tokens_token_hash_unique UNIQUE (token_hash)
);

CREATE INDEX magic_link_tokens_token_hash_idx   ON magic_link_tokens (token_hash);
CREATE INDEX magic_link_tokens_email_used_at_idx ON magic_link_tokens (email, used_at);
CREATE INDEX idx_ml_tokens_email_created          ON magic_link_tokens (email, created_at); -- V18

-- refresh_tokens  (V5)
CREATE TABLE refresh_tokens
(
    id         uuid        NOT NULL DEFAULT gen_random_uuid(),
    user_id    uuid        NOT NULL,
    token_hash varchar(64) NOT NULL, -- SHA-256 hex du refresh token opaque
    expires_at timestamptz NOT NULL,
    revoked    boolean     NOT NULL DEFAULT false,
    created_at timestamptz NOT NULL DEFAULT now(),

    CONSTRAINT refresh_tokens_pkey PRIMARY KEY (id),
    CONSTRAINT refresh_tokens_token_hash_unique UNIQUE (token_hash),
    CONSTRAINT refresh_tokens_user_id_fkey FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE
);

CREATE INDEX refresh_tokens_token_hash_idx ON refresh_tokens (token_hash);
CREATE INDEX refresh_tokens_user_id_idx    ON refresh_tokens (user_id);

-- email_verification_tokens  (V6)
CREATE TABLE email_verification_tokens
(
    id         uuid        NOT NULL DEFAULT gen_random_uuid(),
    user_id    uuid        NOT NULL,
    token_hash varchar(64) NOT NULL,
    expires_at timestamptz NOT NULL,
    used_at    timestamptz,
    created_at timestamptz NOT NULL DEFAULT now(),

    CONSTRAINT email_verification_tokens_pkey PRIMARY KEY (id),
    CONSTRAINT email_verification_tokens_token_hash_unique UNIQUE (token_hash),
    CONSTRAINT email_verification_tokens_user_fk FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE
);

CREATE INDEX email_verification_tokens_token_hash_idx ON email_verification_tokens (token_hash);
CREATE INDEX email_verification_tokens_user_id_idx    ON email_verification_tokens (user_id);
CREATE INDEX idx_evtokens_user_created                ON email_verification_tokens (user_id, created_at); -- V18

-- =============================================================
-- 2. PROFILS
-- =============================================================

-- donor_profiles  (V3 ; wallet_address ajouté en V19)
CREATE TABLE donor_profiles
(
    id             uuid        NOT NULL DEFAULT gen_random_uuid(),
    user_id        uuid        NOT NULL,
    display_name   varchar(255),
    anonymous      boolean     NOT NULL DEFAULT false,
    wallet_address varchar(42),

    CONSTRAINT donor_profiles_pkey PRIMARY KEY (id),
    CONSTRAINT donor_profiles_user_id_unique UNIQUE (user_id),
    CONSTRAINT donor_profiles_user_id_fkey FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE
);

CREATE UNIQUE INDEX idx_donor_wallet_address
    ON donor_profiles (wallet_address) WHERE wallet_address IS NOT NULL;

-- association_profiles  (V4)
CREATE TABLE association_profiles
(
    id           uuid         NOT NULL DEFAULT gen_random_uuid(),
    user_id      uuid         NOT NULL,
    name         varchar(255) NOT NULL,
    identifier   varchar(9)   NOT NULL,
    city         varchar(255),
    postal_code  varchar(10),
    contact_name varchar(255),
    description  text,
    verified     boolean      NOT NULL DEFAULT false,

    CONSTRAINT association_profiles_pkey PRIMARY KEY (id),
    CONSTRAINT association_profiles_user_id_unique UNIQUE (user_id),
    CONSTRAINT association_profiles_user_id_fkey FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE
);

CREATE INDEX association_profiles_siren_idx ON association_profiles (identifier);

-- =============================================================
-- 3. PAYEES  (V8 beneficiaries → renommé payees en V10)
-- =============================================================

-- NB : les noms beneficiaries_* sont conservés tels quels — un schéma issu des
-- migrations garde ces identifiants après le RENAME de V10 (Postgres ne renomme
-- pas automatiquement la PK ni la FK). Seuls les noms diffèrent, pas la structure.
CREATE TABLE payees
(
    id             UUID         NOT NULL DEFAULT gen_random_uuid(),
    association_id UUID         NOT NULL,
    name           VARCHAR(255) NOT NULL,
    payee_type     VARCHAR(10)  NOT NULL DEFAULT 'COMPANY'          -- V31
        CONSTRAINT payees_type_check CHECK (payee_type IN ('COMPANY', 'PERSON')),
    identifier_1   VARCHAR(9),                                      -- V31 : nullable (PERSON n'a pas de SIREN)
    identifier_2   VARCHAR(14),
    activity_code  VARCHAR(10),
    category       VARCHAR(100),
    city           VARCHAR(255),
    postal_code    VARCHAR(10),
    active         BOOLEAN      NOT NULL DEFAULT true,
    created_at     TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at     TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT beneficiaries_pkey PRIMARY KEY (id),
    CONSTRAINT beneficiaries_association_id_fkey
        FOREIGN KEY (association_id) REFERENCES association_profiles(id) ON DELETE CASCADE
);

CREATE INDEX payees_association_idx ON payees (association_id);
-- V31 : partiel — unicité (asso, SIREN) uniquement quand identifier_1 est renseigné.
CREATE UNIQUE INDEX payees_asso_identifier1_unique
    ON payees (association_id, identifier_1)
    WHERE identifier_1 IS NOT NULL;

CREATE TABLE payee_ibans
(
    id                 UUID        NOT NULL DEFAULT gen_random_uuid(),
    payee_id           UUID        NOT NULL,
    iban               VARCHAR(34) NOT NULL,
    status             VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    vop_result         VARCHAR(30),
    vop_suggested_name VARCHAR(255),
    vop_raw_response   TEXT,
    verified_at        TIMESTAMPTZ,
    created_at         TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT beneficiary_ibans_pkey PRIMARY KEY (id),
    CONSTRAINT payee_ibans_payee_id_fkey
        FOREIGN KEY (payee_id) REFERENCES payees(id) ON DELETE CASCADE,
    CONSTRAINT payee_ibans_unique UNIQUE (payee_id, iban)
);

CREATE INDEX payee_ibans_payee_idx ON payee_ibans (payee_id);

-- =============================================================
-- 4. CAMPAGNES & BUDGET  (V9 ; budget_hash ajouté en V17)
-- =============================================================

CREATE TABLE campaigns
(
    id               UUID          PRIMARY KEY DEFAULT gen_random_uuid(),
    association_id   UUID          NOT NULL REFERENCES association_profiles(id) ON DELETE CASCADE,
    name             VARCHAR(255)  NOT NULL,
    emoji            VARCHAR(10)   NOT NULL DEFAULT '🌍',
    description      TEXT,
    goal             NUMERIC(12,2) NOT NULL DEFAULT 0,
    raised           NUMERIC(12,2) NOT NULL DEFAULT 0,
    status           VARCHAR(20)   NOT NULL DEFAULT 'DRAFT',
    start_date       DATE,
    end_date         DATE,
    budget_hash      CHAR(66),
    category         VARCHAR(50),                                   -- V29
    reason           TEXT,                                          -- V29
    impact_goals     TEXT,                                          -- V29
    cover_image      TEXT,                                          -- V29
    created_at       TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at       TIMESTAMPTZ   NOT NULL DEFAULT now()
);

-- V25 : remplace idx_campaigns_association par un composite (association_id, created_at DESC).
CREATE INDEX idx_campaigns_association_created ON campaigns (association_id, created_at DESC);

CREATE TABLE campaign_budget_sections
(
    id          UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    campaign_id UUID         NOT NULL REFERENCES campaigns(id) ON DELETE CASCADE,
    side        VARCHAR(10)  NOT NULL,
    code        VARCHAR(50)  NOT NULL,
    name        VARCHAR(255) NOT NULL,
    sort_order  INT          NOT NULL DEFAULT 0,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now()
);

-- V24 : composite (campaign_id, sort_order) remplace l'index simple.
CREATE INDEX idx_budget_sections_campaign_sort ON campaign_budget_sections (campaign_id, sort_order);

CREATE TABLE campaign_budget_items
(
    id         UUID          PRIMARY KEY DEFAULT gen_random_uuid(),
    section_id UUID          NOT NULL REFERENCES campaign_budget_sections(id) ON DELETE CASCADE,
    label      VARCHAR(255)  NOT NULL,
    amount     NUMERIC(12,2) NOT NULL DEFAULT 0,
    sort_order INT           NOT NULL DEFAULT 0
);

-- V24 : composite (section_id, sort_order).
CREATE INDEX idx_budget_items_section_sort ON campaign_budget_items (section_id, sort_order);

CREATE TABLE campaign_milestones
(
    id            UUID          PRIMARY KEY DEFAULT gen_random_uuid(),
    campaign_id   UUID          NOT NULL REFERENCES campaigns(id) ON DELETE CASCADE,
    emoji         VARCHAR(10)   NOT NULL DEFAULT '🎯',
    title         VARCHAR(255)  NOT NULL,
    description   TEXT,
    target_amount NUMERIC(12,2) NOT NULL DEFAULT 0,
    status        VARCHAR(20)   NOT NULL DEFAULT 'LOCKED',
    sort_order    INT           NOT NULL DEFAULT 0,
    reached_at               TIMESTAMPTZ,
    transparency_commitment  TEXT,                                  -- V30
    created_at               TIMESTAMPTZ   NOT NULL DEFAULT now()
);

-- V24 : composite (campaign_id, sort_order).
CREATE INDEX idx_milestones_campaign_sort ON campaign_milestones (campaign_id, sort_order);

-- =============================================================
-- 5. DONATIONS  (V20 ; unique provider_ref en V23 ; index V27 ; type_code V28)
-- =============================================================

CREATE TABLE donations
(
    id           UUID          PRIMARY KEY DEFAULT gen_random_uuid(),
    donor_id     UUID          NOT NULL REFERENCES donor_profiles(id) ON DELETE CASCADE,
    campaign_id  UUID          NOT NULL REFERENCES campaigns(id) ON DELETE CASCADE,
    amount       NUMERIC(12,2) NOT NULL,
    provider_ref VARCHAR(255)  NOT NULL,
    confirmed_at TIMESTAMPTZ,
    type_code    VARCHAR(50)   NOT NULL DEFAULT '74',
    created_at   TIMESTAMPTZ   NOT NULL DEFAULT now(),

    CONSTRAINT donations_provider_ref_unique UNIQUE (provider_ref)
);

CREATE INDEX idx_donations_donor    ON donations (donor_id);
CREATE INDEX idx_donations_campaign ON donations (campaign_id);

-- V27 : index composites pour les agrégats par campagne / donateur.
CREATE INDEX idx_donations_campaign_confirmed ON donations (campaign_id, confirmed_at)
    WHERE confirmed_at IS NOT NULL;
CREATE INDEX idx_donations_donor_campaign     ON donations (donor_id, campaign_id);

-- =============================================================
-- 6. ON-CHAIN JOBS  (V12 ; action RECORD_PAYOUT ajoutée en V26)
-- =============================================================

CREATE TABLE onchain_jobs
(
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
                          'MARK_MILESTONE_REACHED',
                          'RECORD_PAYOUT'
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

CREATE INDEX idx_onchain_jobs_status_created ON onchain_jobs (status, created_at);

-- =============================================================
-- 7. PAYOUTS  (V26)
-- =============================================================

CREATE TABLE payouts
(
    id             UUID           PRIMARY KEY,
    campaign_id    UUID           NOT NULL REFERENCES campaigns(id),
    payee_id       UUID           NOT NULL REFERENCES payees(id),
    payee_iban_id  UUID           NOT NULL REFERENCES payee_ibans(id),
    amount         NUMERIC(12,2)  NOT NULL,
    kind           VARCHAR(20)    NOT NULL CHECK (kind IN ('REMUNERATION', 'EXPENSE')),
    type_code      VARCHAR(50)    NOT NULL,
    label          VARCHAR(500)   NOT NULL,
    status         VARCHAR(20)    NOT NULL DEFAULT 'PENDING'
        CHECK (status IN ('PENDING', 'CONFIRMED', 'FAILED')),
    created_at     TIMESTAMPTZ    NOT NULL DEFAULT now(),
    confirmed_at   TIMESTAMPTZ,
    onchain_job_id UUID
);

CREATE INDEX idx_payouts_campaign_status  ON payouts (campaign_id, status);
CREATE INDEX idx_payouts_campaign_created ON payouts (campaign_id, created_at DESC);

-- =============================================================
-- 8. MONERIUM  (V11 ; renommages/colonnes V13-V16)
-- =============================================================

CREATE TABLE monerium_connections
(
    id                    UUID        NOT NULL DEFAULT gen_random_uuid(),
    association_id        UUID        NOT NULL,
    monerium_profile_id   VARCHAR(255),                        -- renommé depuis monerium_user_id (V14)
    monerium_profile_name VARCHAR(255),                        -- V14
    access_token          TEXT        NOT NULL,
    refresh_token         TEXT        NOT NULL,
    connected_at          TIMESTAMPTZ NOT NULL,
    expires_at            TIMESTAMPTZ NOT NULL,
    state                 VARCHAR(16) NOT NULL DEFAULT 'ACTIVE', -- V13
    wallet_address        VARCHAR(42),                          -- V15
    wallet_chain          VARCHAR(32),                          -- V16
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

CREATE INDEX idx_monerium_oauth_states_expires   ON monerium_oauth_states (expires_at);                 -- V18
CREATE INDEX idx_monerium_oauth_states_assoc_exp ON monerium_oauth_states (association_id, expires_at); -- V18