-- Sprint 20 / S2 — onboarding status tracking for the "Bank account" tab.
--
-- in_review_observed: TRUE once a sync has ever observed IN_REVIEW for this connection. Combined
-- with a current NEEDS_DATA it means Mollie sent the dossier back for more information
-- ("complement requested"). Backfilled only for rows currently IN_REVIEW — the only observed fact;
-- past transitions were never recorded.
--
-- onboarding_status_since: when the current onboarding_status was first observed. Drives the
-- scheduled sweep cadence (NEEDS_DATA for longer than N days is polled hourly instead of every
-- 5 minutes). Backfilled from connected_at, the only lower bound available for existing rows.
ALTER TABLE mollie_connections
    ADD COLUMN in_review_observed      BOOLEAN     NOT NULL DEFAULT FALSE,
    ADD COLUMN onboarding_status_since TIMESTAMPTZ;

UPDATE mollie_connections
SET in_review_observed = TRUE
WHERE onboarding_status = 'IN_REVIEW';

UPDATE mollie_connections
SET onboarding_status_since = connected_at;

ALTER TABLE mollie_connections
    ALTER COLUMN onboarding_status_since SET NOT NULL,
    ALTER COLUMN onboarding_status_since SET DEFAULT now();
