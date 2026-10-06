-- Campaign causes and place of action (discovery filters, Asana 1219068425130508).
--
-- 1. campaigns.category moves from free text to the CampaignCause enum. Historical values
--    (French labels from the old editor select, English codes from the seeds) are remapped per the
--    "Changement" column; NULL and empty strings stay NULL; any other value becomes AUTRE.
-- 2. Place of action: type (COMMUNE / DEPARTEMENT / FRANCE / PAYS), code, label, coordinates.
--    The scope (local / national / international) is derived from the type, never stored.
--    Existing campaigns are filled by ActionPlaceBackfill (association city), not here:
--    geocoding requires a call to geo.api.gouv.fr.
--
-- Rollback: U82__campaign_cause_and_action_place.sql

-- Upper-case accented variants ("Éducation") are listed explicitly: under a C LC_CTYPE, lower()
-- does not fold non-ASCII characters.
UPDATE campaigns
SET category = CASE
    WHEN category IS NULL OR trim(category) = '' THEN NULL
    WHEN lower(trim(category)) IN ('education', 'éducation', 'Éducation', 'enfance & éducation', 'enfance_education')
        THEN 'ENFANCE_EDUCATION'
    WHEN lower(trim(category)) IN ('santé', 'Santé', 'sante', 'health') THEN 'SANTE'
    WHEN lower(trim(category)) IN ('environnement', 'environment') THEN 'ENVIRONNEMENT'
    WHEN lower(trim(category)) IN ('alimentation', 'food') THEN 'ALIMENTATION'
    WHEN lower(trim(category)) IN ('solidarité', 'Solidarité', 'solidarite', 'social', 'humanitarian')
        THEN 'SOLIDARITE'
    WHEN lower(trim(category)) = 'culture' THEN 'CULTURE'
    WHEN lower(trim(category)) = 'sport' THEN 'SPORT'
    WHEN upper(trim(category)) IN ('SOLIDARITE', 'ALIMENTATION', 'SANTE', 'ENFANCE_EDUCATION', 'ANIMAUX',
                                   'HANDICAP', 'ENVIRONNEMENT', 'CULTURE', 'SPORT', 'DROITS_CITOYENNETE',
                                   'AUTRE')
        THEN upper(trim(category))
    ELSE 'AUTRE'
END;

ALTER TABLE campaigns
    ADD CONSTRAINT campaigns_category_check CHECK (category IN (
        'SOLIDARITE', 'ALIMENTATION', 'SANTE', 'ENFANCE_EDUCATION', 'ANIMAUX', 'HANDICAP',
        'ENVIRONNEMENT', 'CULTURE', 'SPORT', 'DROITS_CITOYENNETE', 'AUTRE'));

ALTER TABLE campaigns
    ADD COLUMN action_place_type  VARCHAR(20)  NULL
        CONSTRAINT campaigns_action_place_type_check
            CHECK (action_place_type IN ('COMMUNE', 'DEPARTEMENT', 'FRANCE', 'PAYS')),
    ADD COLUMN action_place_code  VARCHAR(10)  NULL,
    ADD COLUMN action_place_label VARCHAR(255) NULL,
    ADD COLUMN action_latitude    DOUBLE PRECISION NULL,
    ADD COLUMN action_longitude   DOUBLE PRECISION NULL;
