-- Net amount of a HelloAsso payout (cash-out) as it reaches the association's bank account: Σ credits − Σ debits of
-- HelloAsso's payout export (.tasks/handoff-mise-a-disposition-fonds-helloasso.md, MD4), shown next to the
-- "transfer received" confirmation. The export lists third-party payers' data: only this total is kept, never a row.
-- Read lazily, once per payout.
CREATE TABLE helloasso_cash_outs (
    association_id  UUID           NOT NULL REFERENCES association_profiles (id) ON DELETE CASCADE,
    id_cash_out     BIGINT         NOT NULL,
    net_total       NUMERIC(12, 2) NOT NULL,
    fetched_at      TIMESTAMPTZ    NOT NULL DEFAULT now(),
    PRIMARY KEY (association_id, id_cash_out)
);
