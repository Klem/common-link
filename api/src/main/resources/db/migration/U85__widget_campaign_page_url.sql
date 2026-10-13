-- Fails if a stored URL is longer than 255 characters: shorten or clear those rows first.
ALTER TABLE association_profiles
    ALTER COLUMN widget_allowed_origin TYPE VARCHAR(255);
