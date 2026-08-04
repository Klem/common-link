-- Snapshot d'identité — date et lieu de naissance (requis pour le reçu fiscal Cerfa 2041-RD).
-- Nullable en base pour compatibilité avec les donations existantes ; obligatoire via validation applicative.
ALTER TABLE donations
    ADD COLUMN donor_birth_date DATE,
    ADD COLUMN donor_birth_city VARCHAR(128);
