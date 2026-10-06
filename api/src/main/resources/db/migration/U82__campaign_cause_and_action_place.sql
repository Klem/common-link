-- Rollback of V82. Causes stay as enum names (the free-text originals are not recoverable).
ALTER TABLE campaigns DROP COLUMN IF EXISTS action_longitude;
ALTER TABLE campaigns DROP COLUMN IF EXISTS action_latitude;
ALTER TABLE campaigns DROP COLUMN IF EXISTS action_place_label;
ALTER TABLE campaigns DROP COLUMN IF EXISTS action_place_code;
ALTER TABLE campaigns DROP COLUMN IF EXISTS action_place_type;
ALTER TABLE campaigns DROP CONSTRAINT IF EXISTS campaigns_category_check;
