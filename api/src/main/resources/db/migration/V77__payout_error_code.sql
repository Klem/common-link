-- V77 : Code de cause stable sur un décaissement en échec
-- Rollback : U77__payout_error_code.sql
--
-- `bridge_last_error` mélange deux choses depuis V76 : le `status_reason` de Bridge, qui est un
-- code ISO 20022 nu (`AC01`, `NOAS`…), et nos propres messages d'échec, en anglais, dont un qui
-- porte un UUID de payout. Le tout est affiché **brut** en bulle d'aide à l'association. Une asso
-- y lit `AC01`, ou « Bridge recorded a different destination IBAN for payout 696de1eb-… ».
--
-- On ne peut pas retrouver la cause en reconnaissant la chaîne : ce serait l'anti-motif déjà
-- écarté pour `BridgeInitiationNotStartedException` — reconnaître un cas au texte de son message
-- casse à la première reformulation, et se traduit ici par une bulle d'aide fausse.
--
-- Donc une colonne dédiée, posée **au site où la cause est connue** et jamais dérivée après coup.
-- `bridge_last_error` reste tel quel : c'est la trace verbatim pour le support, désormais plus
-- jamais montrée à une association.

ALTER TABLE payouts
    ADD COLUMN bridge_last_error_code VARCHAR(32);

COMMENT ON COLUMN payouts.bridge_last_error_code IS
    'Cause stable du dernier échec (PayoutErrorCode), traduite côté front. Les 17 premières valeurs sont les status_reason ISO de Bridge, les suivantes nos propres causes. NULL tant qu''aucun échec n''est survenu.';

-- Valeurs identiques aux entrées de l'enum Kotlin PayoutErrorCode : la base ne doit jamais
-- dépendre de la seule couche applicative pour refuser une valeur inconnue.
ALTER TABLE payouts ADD CONSTRAINT payouts_bridge_last_error_code_check CHECK (bridge_last_error_code IN (
    -- status_reason renvoyés par Bridge (docs.bridgeapi.io/docs/payments-statuses-2)
    'AC01',
    'AC04',
    'AC06',
    'AG01',
    'AM04',
    'AM18',
    'CH03',
    'CUST',
    'DS02',
    'FF01',
    'FRAD',
    'MS03',
    'NOAS',
    'RR01',
    'RR03',
    'RR04',
    'RR12',
    -- La banque a refusé sans communiquer de motif : Bridge omet alors le champ.
    'UNSPECIFIED',
    -- Bridge a renvoyé un code que cette version ne connaît pas. Conservé plutôt qu'écrasé en
    -- NULL : « refusé pour une raison que nous ne savons pas nommer » n'est pas « aucun échec ».
    'UNKNOWN',
    -- Causes internes, sans rapport avec un refus bancaire.
    'LINK_EXPIRED',
    'LINK_REVOKED',
    'INITIATION_FAILED',
    'DESTINATION_UNVERIFIED',
    'LINK_NOT_RECORDED'
));
