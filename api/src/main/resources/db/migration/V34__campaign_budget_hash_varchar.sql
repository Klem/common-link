-- Campaign.budgetHash entity maps to VARCHAR(66); DB had CHAR(66) (bpchar), causing
-- Hibernate schema validation failure at boot. Convert to match.
ALTER TABLE campaigns ALTER COLUMN budget_hash TYPE VARCHAR(66);
