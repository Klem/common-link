-- Payment service provider that collects this association's donations. Every existing association
-- uses Mollie: the DEFAULT fills them, no data update. In-flight payments are not routed by this
-- column but by the prefix of donations.provider_ref.
ALTER TABLE association_profiles
    ADD COLUMN payment_provider VARCHAR(16) NOT NULL DEFAULT 'MOLLIE';

-- Values must stay identical to the PaymentProviderId Kotlin enum entries.
ALTER TABLE association_profiles
    ADD CONSTRAINT chk_association_payment_provider
        CHECK (payment_provider IN ('MOLLIE', 'HELLOASSO'));
