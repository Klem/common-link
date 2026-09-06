-- V75 : Ajout du flag actif/désactivé sur les IBAN de payee
-- Rollback : U75__payee_iban_active.sql
--
-- Un IBAN VERIFIED ayant déjà servi à au moins un paiement ne peut plus être supprimé
-- (audit trail) : il peut seulement être désactivé via ce flag, ce qui l'exclut de la
-- sélection lors de la création d'un paiement (voir PayoutService.blockingReasonsFor).

ALTER TABLE payee_ibans
    ADD COLUMN active BOOLEAN NOT NULL DEFAULT TRUE;
