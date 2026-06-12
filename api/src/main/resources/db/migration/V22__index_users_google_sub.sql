-- Partial unique index on users.google_sub.
-- Partial: NULL rows (non-Google users) are excluded, so multiple NULLs are allowed.
-- Unique: no two Google-provider users can share the same Google subject identifier.
-- Expected planner change: sequential scan → index scan on UserRepository.findByGoogleSub().
CREATE UNIQUE INDEX idx_users_google_sub ON users (google_sub) WHERE google_sub IS NOT NULL;
