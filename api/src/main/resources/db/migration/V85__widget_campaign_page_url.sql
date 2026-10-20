-- widget_allowed_origin now stores the full URL of the association's campaign page (path and query
-- kept), not only its origin: it is the post-payment return target. Existing origin-only values stay
-- valid as-is, so no data change is needed.
ALTER TABLE association_profiles
    ALTER COLUMN widget_allowed_origin TYPE VARCHAR(2048);
