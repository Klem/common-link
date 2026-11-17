ALTER TABLE association_profiles
    DROP CONSTRAINT chk_association_payment_provider;
ALTER TABLE association_profiles
    DROP COLUMN payment_provider;
