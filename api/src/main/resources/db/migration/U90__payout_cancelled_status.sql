-- Fails on the CHECK if CANCELLED rows exist: they have no equivalent in the previous status set.
ALTER TABLE payouts
    DROP COLUMN updated_at;
ALTER TABLE payouts
    DROP CONSTRAINT payouts_status_check;
ALTER TABLE payouts
    ADD CONSTRAINT payouts_status_check
        CHECK (status IN ('PENDING', 'CONFIRMED', 'FAILED'));
