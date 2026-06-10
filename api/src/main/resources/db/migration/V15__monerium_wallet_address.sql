ALTER TABLE monerium_connections
    ADD COLUMN wallet_address VARCHAR(42);
CREATE UNIQUE INDEX idx_monerium_wallet_address
    ON monerium_connections (wallet_address) WHERE wallet_address IS NOT NULL;
