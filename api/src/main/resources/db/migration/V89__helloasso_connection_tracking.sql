-- HelloAsso link tracking (decision 2026-11-02):
-- webhook_missing_at: last time the payment reconciler confirmed a HelloAsso donation whose notification never
--   arrived — the association's notification URL is probably not configured (status warning WEBHOOK_MISSING until a
--   notification is received again).
-- onboarding_started_at / reminder_j1_sent_at / reminder_j7_sent_at: the association started the "no HelloAsso
--   account yet" journey without linking; reminders are sent once each at D+1 and D+7, never after a link.
ALTER TABLE helloasso_connections
    ADD COLUMN webhook_missing_at     TIMESTAMPTZ,
    ADD COLUMN onboarding_started_at  TIMESTAMPTZ,
    ADD COLUMN reminder_j1_sent_at    TIMESTAMPTZ,
    ADD COLUMN reminder_j7_sent_at    TIMESTAMPTZ;
