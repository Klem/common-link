-- Rollback of V40__donation_widget_fields.sql

ALTER TABLE donations DROP COLUMN IF EXISTS source_site;
ALTER TABLE donations DROP COLUMN IF EXISTS donor_full_name;
ALTER TABLE donations DROP COLUMN IF EXISTS donor_address_line1;
ALTER TABLE donations DROP COLUMN IF EXISTS donor_address_line2;
ALTER TABLE donations DROP COLUMN IF EXISTS donor_postal_code;
ALTER TABLE donations DROP COLUMN IF EXISTS donor_city;
ALTER TABLE donations DROP COLUMN IF EXISTS donor_country;
