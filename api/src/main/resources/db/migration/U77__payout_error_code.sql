-- Rollback de V77__payout_error_code.sql
--
-- `bridge_last_error` n'a pas été touché par V77 : il porte toujours le message verbatim, donc
-- rien n'est perdu côté diagnostic. Seule la cause exploitable par l'interface disparaît, et la
-- bulle d'aide revient à afficher la chaîne brute.

ALTER TABLE payouts DROP CONSTRAINT IF EXISTS payouts_bridge_last_error_code_check;
ALTER TABLE payouts DROP COLUMN IF EXISTS bridge_last_error_code;
