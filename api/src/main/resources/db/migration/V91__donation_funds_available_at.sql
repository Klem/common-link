-- Instant from which a donation's money can fund a payout (payment to a payee). NULL = not available yet.
-- Mollie: equal to confirmed_at. HelloAsso: set later, once the provider has paid the money out to the
-- association (cash-out tracking), never back-dated.
ALTER TABLE donations
    ADD COLUMN funds_available_at TIMESTAMPTZ NULL;

-- Back-fill of every confirmed donation, helloasso: ones included (user decision T2 = (a),
-- .tasks/mise-a-disposition-fonds-helloasso-sprint.md section 0.5: no real HelloAsso donation in production).
UPDATE donations
SET funds_available_at = confirmed_at
WHERE confirmed_at IS NOT NULL;
