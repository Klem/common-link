-- Rollback of V65__donation_payment_method.sql
ALTER TABLE donations
    DROP COLUMN IF EXISTS payment_method;
