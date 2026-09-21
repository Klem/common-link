-- V76 : Exécution réelle des décaissements via Bridge (initiation de virement IBAN → IBAN)
-- Rollback : U76__bridge_payment_initiation.sql
--
-- Jusqu'ici un payout CONFIRMED n'était qu'une écriture comptable + une attestation on-chain :
-- aucun virement bancaire n'était émis. Bridge apporte l'exécution.
--
-- Modèle retenu : **initiation de paiement Open Banking à bénéficiaire dynamique**.
-- Le débiteur est l'association elle-même : elle authentifie le virement auprès de sa propre
-- banque, et les fonds vont directement de son compte vers l'IBAN du bénéficiaire. Bridge ne
-- détient jamais les fonds, et aucun compte de paiement au nom de CommonLink n'est nécessaire.
-- L'IBAN du bénéficiaire est transmis en clair dans la requête (`transactions[].beneficiary.iban`),
-- il n'y a donc aucun bénéficiaire à pré-enregistrer chez Bridge.
--
-- Deux cycles de vie coexistent et ne doivent PAS être confondus :
--   - payouts.status        = cycle de vie métier CommonLink (PENDING / CONFIRMED / FAILED)
--   - payouts.bridge_status = état de l'initiation chez Bridge (voir BridgePaymentStatus)
-- Fusionner les deux casserait le calcul de solde, les KPI et le donut de répartition, qui
-- s'appuient tous sur les trois états métier.

ALTER TABLE payouts
    ADD COLUMN bridge_payment_link_id        VARCHAR(64),
    ADD COLUMN bridge_payment_transaction_id VARCHAR(64),
    ADD COLUMN bridge_checkout_url           VARCHAR(512),
    ADD COLUMN bridge_status                 VARCHAR(32),
    ADD COLUMN bridge_last_error             VARCHAR(500),
    ADD COLUMN bridge_synced_at              TIMESTAMPTZ;

COMMENT ON COLUMN payouts.bridge_payment_link_id IS
    'Identifiant du payment link Bridge (POST /v3/payment/payment-links). NULL tant qu''aucune initiation n''a été créée.';
COMMENT ON COLUMN payouts.bridge_payment_transaction_id IS
    'Identifiant de la transaction Bridge, connu seulement après que l''association a authentifié le virement auprès de sa banque.';
COMMENT ON COLUMN payouts.bridge_checkout_url IS
    'URL d''authentification bancaire à laquelle rediriger l''association. Conservée pour permettre de reprendre un virement non finalisé.';
COMMENT ON COLUMN payouts.bridge_status IS
    'Dernier statut Bridge connu (BridgePaymentStatus). NULL = aucune initiation en cours ; non NULL sur un payout PENDING = montant déjà engagé.';
COMMENT ON COLUMN payouts.bridge_last_error IS
    'Message d''erreur du dernier échec Bridge, pour diagnostic sans relire les logs.';
COMMENT ON COLUMN payouts.bridge_synced_at IS
    'Horodatage du dernier rapprochement de bridge_status auprès de Bridge.';

-- Valeurs identiques aux entrées de l'enum Kotlin BridgePaymentStatus : la base ne doit jamais
-- dépendre de la seule couche applicative pour refuser un statut inconnu.
ALTER TABLE payouts ADD CONSTRAINT payouts_bridge_status_check CHECK (bridge_status IN (
    'CREA',
    'ACTC',
    'PDNG',
    'ACSC',
    'RJCT',
    'PART',
    'LINK_EXPIRED',
    'LINK_REVOKED'
));

-- Le webhook Bridge ne porte pas de signature vérifiable : il est traité comme une simple
-- notification et l'état est relu auprès de Bridge. La recherche se fait donc par identifiant de
-- payment link, qui est la seule clé présente dans la notification.
CREATE INDEX idx_payouts_bridge_payment_link ON payouts (bridge_payment_link_id);
