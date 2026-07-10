-- ROLLBACK : Revert association_profiles RNA / SIREN migration

-- 1. Annuler le swap des données (remettre identifier = ancien SIREN, siren = ancien RNA)
WITH current AS (
    SELECT id, identifier AS current_identifier, siren AS current_siren
    FROM association_profiles
    WHERE identifier LIKE 'W%'                    -- les RNA commencent par W
       OR siren IS NOT NULL
)
UPDATE association_profiles ap
SET identifier = current.current_siren,           -- remettre l'ancien SIREN dans identifier
    siren      = current.current_identifier       -- remettre l'ancien RNA dans siren
FROM current
WHERE ap.id = current.id;

-- 2. Renommer siren → rna (retour à l'ancien nom)
ALTER TABLE association_profiles
    RENAME COLUMN siren TO rna;

-- 3. Rétrécir identifier à sa taille d'origine (ajuste selon ton ancien type)
--    Le plus courant était VARCHAR(14) ou VARCHAR(9) pour SIREN
ALTER TABLE association_profiles
    ALTER COLUMN identifier TYPE VARCHAR(14);     -- ← À adapter si ton ancien type était différent