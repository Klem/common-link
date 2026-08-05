-- U49 : Rollback of V49__donation_birth_fields.sql

ALTER TABLE donations
    DROP COLUMN IF EXISTS donor_birth_date,
    DROP COLUMN IF EXISTS donor_birth_city;
