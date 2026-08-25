ALTER TABLE association_profiles ADD COLUMN wallet_address VARCHAR(42);
CREATE UNIQUE INDEX idx_association_wallet_address
    ON association_profiles (wallet_address) WHERE wallet_address IS NOT NULL;
