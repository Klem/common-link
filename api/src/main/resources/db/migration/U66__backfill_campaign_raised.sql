-- Rollback of V66__backfill_campaign_raised.sql
--
-- The pre-V66 value of campaigns.raised is not recoverable: it was a stale figure no writer
-- maintained, and V66 replaced it with the sum of confirmed donations. Resetting to the recomputed
-- sum is the only defensible state, and it is what V66 already produces — so this script restores
-- the schema-level status quo (nothing to undo structurally) without inventing a previous value.
--
-- Reverting the code that maintains the column without reverting the data is safe: readers then see
-- a correct-but-frozen figure instead of a wrong-and-frozen one.
SELECT 1;
