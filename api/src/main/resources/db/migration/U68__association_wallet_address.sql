DROP INDEX IF EXISTS idx_association_wallet_address;
ALTER TABLE association_profiles DROP COLUMN IF EXISTS wallet_address;
