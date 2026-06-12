-- P2-1: drop redundant plain index on users.email
-- CONSTRAINT users_email_unique UNIQUE (email) in V1 already creates an implicit unique index.
-- The separate users_email_idx on the same column is never preferred by the planner and only
-- adds write overhead on every user insert/update.
DROP INDEX IF EXISTS users_email_idx;

-- P2-2: replace single-column campaigns index with a composite that also covers ORDER BY created_at DESC
-- The new index serves both the associationId filter and the DESC sort in one index scan,
-- replacing the sort node that idx_campaigns_association (association_id) required.
DROP INDEX IF EXISTS idx_campaigns_association;
CREATE INDEX idx_campaigns_association_created ON campaigns (association_id, created_at DESC);
