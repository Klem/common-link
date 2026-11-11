-- Cash-out tracking of HelloAsso donations: where the money of each confirmed helloasso: donation stands between
-- HelloAsso and the association's bank account (.tasks/handoff-mise-a-disposition-fonds-helloasso.md, MD3b).
-- One row per confirmed helloasso: donation, created at its confirmation; no back-fill of donations confirmed before
-- (user decision T2 = (a): no real HelloAsso donation in production).
--
-- Only HelloAsso ids and states are stored, never any payer data. cash_out_state is HelloAsso's raw value, kept for
-- diagnosis; funds_state is ours (FundsState enum).
CREATE TABLE helloasso_donation_funds (
    donation_id                UUID         PRIMARY KEY REFERENCES donations (id) ON DELETE CASCADE,
    ha_payment_id              BIGINT       NOT NULL,
    cash_out_state             VARCHAR(64),
    funds_state                VARCHAR(20)  NOT NULL,
    cash_out_date              TIMESTAMPTZ,
    id_cash_out                BIGINT,
    cashed_out_seen_at         TIMESTAMPTZ,
    last_checked_at            TIMESTAMPTZ,
    -- First observation of an unrecognised or Unknown payout state (cleared by a known one), and when it was alerted.
    unknown_since              TIMESTAMPTZ,
    unknown_alerted_at         TIMESTAMPTZ,
    -- When the "paid out but not arrived for too long" alert was raised for this donation.
    in_transit_alerted_at      TIMESTAMPTZ,
    -- How the donation became AVAILABLE: AUTO (settlement delay elapsed), MANUAL (the association confirmed the
    -- transfer was received) or CURATOR_RELEASE (manual release by a curator). receipt_confirmed_by is the user who
    -- did it, NULL for AUTO.
    receipt_confirmed_at       TIMESTAMPTZ,
    receipt_confirmation_mode  VARCHAR(20),
    receipt_confirmed_by       UUID         REFERENCES users (id) ON DELETE SET NULL,
    created_at                 TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at                 TIMESTAMPTZ  NOT NULL DEFAULT now()
);

-- Values must stay identical to the FundsState Kotlin enum entries.
ALTER TABLE helloasso_donation_funds
    ADD CONSTRAINT chk_helloasso_donation_funds_state
        CHECK (funds_state IN ('AT_PROVIDER', 'PAYOUT_PREPARING', 'IN_TRANSIT', 'AVAILABLE', 'WITHDRAWN'));

-- Values must stay identical to the ReceiptConfirmationMode Kotlin enum entries.
ALTER TABLE helloasso_donation_funds
    ADD CONSTRAINT chk_helloasso_donation_funds_receipt_mode
        CHECK (receipt_confirmation_mode IS NULL OR receipt_confirmation_mode IN ('AUTO', 'MANUAL', 'CURATOR_RELEASE'));

CREATE INDEX idx_helloasso_donation_funds_state ON helloasso_donation_funds (funds_state);
CREATE INDEX idx_helloasso_donation_funds_cash_out ON helloasso_donation_funds (id_cash_out) WHERE id_cash_out IS NOT NULL;

-- Outcome of the last cash-out tracking pass, per association (read by the Payments tab), and the anti-multiclick
-- guard of the on-demand refresh (last_refresh_requested_at, claimed by an atomic UPDATE).
CREATE TABLE helloasso_cash_out_syncs (
    association_id             UUID         PRIMARY KEY REFERENCES association_profiles (id) ON DELETE CASCADE,
    last_sync_at               TIMESTAMPTZ,
    sync_error                 VARCHAR(64),
    consecutive_failures       INTEGER      NOT NULL DEFAULT 0,
    failure_alerted_at         TIMESTAMPTZ,
    -- When the "donations not paid out yet while the HelloAsso link is gone" alert was raised (cleared on relink).
    unlinked_alerted_at        TIMESTAMPTZ,
    last_refresh_requested_at  TIMESTAMPTZ,
    created_at                 TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at                 TIMESTAMPTZ  NOT NULL DEFAULT now()
);
