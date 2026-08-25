-- U70 : Rollback of V70__donation_public_ref.sql

DROP INDEX IF EXISTS donations_public_ref_unique;

ALTER TABLE donations
    DROP COLUMN IF EXISTS public_ref;
