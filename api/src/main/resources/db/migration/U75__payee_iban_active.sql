-- Rollback V75 : suppression du flag actif/désactivé sur les IBAN de payee

ALTER TABLE payee_ibans
    DROP COLUMN active;
