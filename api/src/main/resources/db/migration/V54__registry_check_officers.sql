-- V54 : colonne officers (représentants) et rna_active sur association_registry_check
-- Rollback : U54__registry_check_officers.sql

ALTER TABLE association_registry_check
    ADD COLUMN officers   TEXT    NOT NULL DEFAULT '[]',
    ADD COLUMN rna_active BOOLEAN;
