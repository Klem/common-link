-- Real payment method used by the payer, as reported by the payment provider.
-- Rendered as "Mode de versement" on the Cerfa fiscal receipt, which must state the mode
-- actually used and not the list of modes the platform accepts.
-- Nullable by design: donations confirmed without a provider payload (reconciler path) have
-- no known method, and the receipt then states "Non précisé".
-- No CHECK constraint: the value set is owned by Mollie and grows without notice — an unknown
-- code must be stored and printed as-is, never rejected.
ALTER TABLE donations
    ADD COLUMN payment_method VARCHAR(32);

COMMENT ON COLUMN donations.payment_method IS
    'Provider payment method code (e.g. creditcard, banktransfer). Null when unknown.';
