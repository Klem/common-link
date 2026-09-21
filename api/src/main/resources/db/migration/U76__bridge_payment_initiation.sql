-- Rollback de V76__bridge_payment_initiation.sql
--
-- Attention : supprimer bridge_payment_transaction_id fait perdre le lien de rapprochement
-- bancaire entre un payout CommonLink et le virement réellement exécuté. À n'exécuter que si
-- aucune initiation n'a été créée, ou après export de ces colonnes.

DROP INDEX IF EXISTS idx_payouts_bridge_payment_link;

ALTER TABLE payouts DROP CONSTRAINT IF EXISTS payouts_bridge_status_check;

ALTER TABLE payouts
    DROP COLUMN IF EXISTS bridge_synced_at,
    DROP COLUMN IF EXISTS bridge_last_error,
    DROP COLUMN IF EXISTS bridge_status,
    DROP COLUMN IF EXISTS bridge_checkout_url,
    DROP COLUMN IF EXISTS bridge_payment_transaction_id,
    DROP COLUMN IF EXISTS bridge_payment_link_id;
