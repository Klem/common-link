-- V55 : catégorie juridique INSEE sur association_registry_check
-- Rollback : U55__registry_check_legal_category.sql

ALTER TABLE association_registry_check
    ADD COLUMN legal_category VARCHAR(10);
