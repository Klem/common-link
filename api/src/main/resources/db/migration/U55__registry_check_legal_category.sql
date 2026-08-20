-- U55 : rollback catégorie juridique INSEE
ALTER TABLE association_registry_check
    DROP COLUMN IF EXISTS legal_category;
