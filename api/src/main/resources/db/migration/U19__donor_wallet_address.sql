DROP INDEX IF EXISTS idx_donor_wallet_address;
ALTER TABLE donor_profiles DROP COLUMN IF EXISTS wallet_address;
