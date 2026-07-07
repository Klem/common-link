-- Adds a type_code column to donations for budget variance reporting (Step 8).
-- Mirrors payout.type_code: the prefix before '-' maps to a CampaignBudgetSection.code.
-- Default '74' = subventions / produits courants (French associative plan comptable).

ALTER TABLE donations
    ADD COLUMN type_code VARCHAR(50) NOT NULL DEFAULT '74';
