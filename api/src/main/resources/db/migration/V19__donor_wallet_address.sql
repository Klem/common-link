ALTER TABLE donor_profiles ADD COLUMN wallet_address VARCHAR(42);
CREATE UNIQUE INDEX idx_donor_wallet_address
    ON donor_profiles (wallet_address) WHERE wallet_address IS NOT NULL;
