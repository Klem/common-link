-- CANCELLED: a PENDING payout the association withdrew while no transfer was in progress
-- (bridge_status NULL). Its amount stops being reserved on the campaign's available balance.
-- Values must stay identical to the PayoutStatus Kotlin enum entries.
ALTER TABLE payouts
    DROP CONSTRAINT payouts_status_check;
ALTER TABLE payouts
    ADD CONSTRAINT payouts_status_check
        CHECK (status IN ('PENDING', 'CONFIRMED', 'FAILED', 'CANCELLED'));

-- Last write on the row, stamped by Hibernate (@UpdateTimestamp). NULL on rows not written since
-- this column was added: no value is back-filled.
ALTER TABLE payouts
    ADD COLUMN updated_at TIMESTAMPTZ;
