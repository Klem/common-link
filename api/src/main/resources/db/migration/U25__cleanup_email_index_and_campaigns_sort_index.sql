DROP INDEX IF EXISTS idx_campaigns_association_created;
CREATE INDEX idx_campaigns_association ON campaigns (association_id);
CREATE INDEX users_email_idx ON users (email);
