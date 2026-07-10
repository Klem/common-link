-- Widen identifier to hold RNA (10 chars: W + 9 digits)
ALTER TABLE association_profiles
    ALTER COLUMN identifier TYPE VARCHAR(20);

-- Rename rna → siren (SIREN is now the secondary identifier)
ALTER TABLE association_profiles
    RENAME COLUMN rna TO siren;

-- For rows that already have an RNA (now in 'siren' after rename):
-- Swap: identifier = RNA, siren = old SIREN
WITH old AS (
    SELECT id, identifier AS old_siren, siren AS old_rna
    FROM association_profiles
    WHERE siren IS NOT NULL
)
UPDATE association_profiles ap
SET identifier = old.old_rna,
    siren      = old.old_siren
FROM old
WHERE ap.id = old.id;
