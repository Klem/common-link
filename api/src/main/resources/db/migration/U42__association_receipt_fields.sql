ALTER TABLE association_profiles
    DROP COLUMN IF EXISTS address_line1,
    DROP COLUMN IF EXISTS legal_object,
    DROP COLUMN IF EXISTS signer_name,
    DROP COLUMN IF EXISTS signer_role;
