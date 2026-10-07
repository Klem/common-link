-- Rollback V78 : suppression du socle « dashboard donateur »

DROP INDEX IF EXISTS idx_donations_donor_confirmed;

ALTER TABLE donor_profiles DROP COLUMN notify_suggestions;
ALTER TABLE donor_profiles DROP COLUMN notify_goal_reached;
ALTER TABLE donor_profiles DROP COLUMN notify_new_payout;
ALTER TABLE donor_profiles DROP COLUMN notify_monthly_report;
ALTER TABLE donor_profiles DROP COLUMN last_name;
ALTER TABLE donor_profiles DROP COLUMN first_name;
