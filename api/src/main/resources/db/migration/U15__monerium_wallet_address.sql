DROP INDEX IF EXISTS idx_monerium_wallet_address;
ALTER TABLE monerium_connections DROP COLUMN IF EXISTS wallet_address;
