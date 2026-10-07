-- V78 : Socle « dashboard donateur » — identité civile, préférences de notification, index d'historique
-- Rollback : U78__donor_dashboard.sql
--
-- Numérotée 78 et non 77 : V77__payout_error_code.sql existe sur une autre branche et est déjà
-- appliquée en staging (24/09/2026). Le fichier est absent de cette branche, donc `ls` sur le
-- dossier de migrations ne suffit pas à déterminer le prochain numéro libre.
--
-- Identité civile : distincte de display_name, qui reste le pseudonyme public affiché sur les
-- listes de dons. Les deux coexistent : un donateur peut donner sous pseudonyme tout en ayant une
-- identité civile utilisée pour les reçus.
--
-- Préférences de notification : valeurs par défaut alignées sur l'écran Paramètres — rapport
-- mensuel, nouvelle dépense et objectif atteint activés, suggestions désactivées (opt-in).

ALTER TABLE donor_profiles ADD COLUMN first_name VARCHAR(128);
ALTER TABLE donor_profiles ADD COLUMN last_name  VARCHAR(128);

ALTER TABLE donor_profiles ADD COLUMN notify_monthly_report BOOLEAN NOT NULL DEFAULT TRUE;
ALTER TABLE donor_profiles ADD COLUMN notify_new_payout     BOOLEAN NOT NULL DEFAULT TRUE;
ALTER TABLE donor_profiles ADD COLUMN notify_goal_reached   BOOLEAN NOT NULL DEFAULT TRUE;
ALTER TABLE donor_profiles ADD COLUMN notify_suggestions    BOOLEAN NOT NULL DEFAULT FALSE;

-- Historique des dons du donateur : filtre donor_id + confirmed_at IS NOT NULL, tri confirmed_at DESC.
-- Index partiel : les lignes non confirmées (paniers abandonnés) ne sont jamais lues par ce chemin
-- et n'ont pas à peser sur l'index. idx_donations_donor est conservé : partiel, celui-ci ne couvre
-- pas les recherches par donor_id portant sur les dons en attente.
CREATE INDEX idx_donations_donor_confirmed
    ON donations (donor_id, confirmed_at DESC)
    WHERE confirmed_at IS NOT NULL;
