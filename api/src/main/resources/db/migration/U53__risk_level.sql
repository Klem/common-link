-- Rollback de V53__risk_level.sql
-- Supprime les colonnes de niveau de risque LCB-FT ajoutées par V53.
--
-- Précondition : aucun objet ne dépend de ces colonnes (index, vues, fonctions dérivées).
-- Vérification recommandée avant exécution :
--   SELECT count(*) FROM association_profiles WHERE risk_level != 'STANDARD';
--   SELECT count(*) FROM association_profiles WHERE risk_level_assessed_at IS NOT NULL;
-- Si des évaluations ont déjà été enregistrées, ce rollback est irréversible.

ALTER TABLE association_profiles
    DROP COLUMN IF EXISTS risk_level,
    DROP COLUMN IF EXISTS risk_level_assessed_at,
    DROP COLUMN IF EXISTS risk_classification_version;

ALTER TABLE donations
    DROP COLUMN IF EXISTS risk_level;
