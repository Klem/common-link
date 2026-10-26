# CommonLinkRegistry — Spécification (MVP)

> Source de vérité pour le smart contract `CommonLinkRegistry`. **Le code (`src/CommonLinkRegistry.sol`) et les tests Foundry font foi** ;
> toute déviation de ce document est un bug de documentation.
>
> **Version**: 3.0.0-mvp (`READER_VERSION` = 1)
> **Solidity**: 0.8.30 (EVM `cancun`)
> **Stack**: Foundry + OpenZeppelin 5.x
> **Cibles**: Gnosis Chiado (10200, testnet) → Gnosis (100, mainnet) — gas en xDAI
> **Principe directeur**: la blockchain est invisible à l'utilisateur final. CommonLink atteste, les utilisateurs ne signent jamais.

### Changelog

- **3.0.0-mvp** : **hub d'une architecture hub + satellites** (décisions 27–41 de `.tasks/blockchain-integration-sprint.md`).
  Retrait complet du relais de badge (`setBadgeContract`, `mintBadge`, `badgeContract`, `IDonorBadge`, events et erreurs associés) :
  le hub n'appelle plus aucun contrat, donc `ReentrancyGuard` retiré. Admin via `AccessControlDefaultAdminRules` (transfert en deux
  étapes avec délai, `grantRole` / `revokeRole` de l'admin interdits) et **renonciation à l'admin interdite** (`AdminRenounceForbidden`).
  Nouveau `PAUSER_ROLE` : pause par le pauser, reprise par l'admin seul. Interface de lecture `ICommonLinkRegistryReader` (structs
  + getters `get*` + `READER_VERSION`), annoncée via ERC-165 ; mappings passés `internal` (surface de lecture unique : les `get*` ;
  l'index SIREN n'a plus de getter). **Aucun fonds** : ni `receive`, ni `fallback`, ni fonction `payable`, ni hook de réception de
  jeton (contrôle `script/check-no-funds.sh`). Constructeur `(adminDelay, admin, recorder, pauser)`, recorder et pauser obligatoires
  (`InvalidRecorder`, `InvalidPauser` ; `InvalidAdmin` remplacée par l'erreur OpenZeppelin `AccessControlInvalidDefaultAdmin`).
  Rupture assumée : seul Anvil local était déployé.
- **2.1.0-mvp** : décaissement enregistré comme une **fiche** (`payouts[id]` = campagne, montant, date, `txRef` = référence du virement Bridge, lisible par `getPayout`), qui remplace le booléen `payoutRecorded` ; `recordPayout(payoutId, campaignId, amountCents, txRef)` (`txRef` ≠ 0, `EmptyTxRef`) ; event `PayoutRecorded` avec `txRef` ; **statistiques de campagne** `campaignStats` (`getCampaignStats`) : total et nombre des décaissements (garde `PaidOutOverflow`), nombre de dons, donateurs uniques, dates du dernier don / décaissement. Aucune garde « décaissé ≤ collecté ». Rupture assumée : aucune version antérieure n'a été déployée sur une chaîne publique.
- **2.0.0-mvp** : **registre « inscrit une fois, inscrit pour toujours »**. Le contrat ne gère plus **aucun cycle de vie** : plus de statuts ni de transitions de campagne, plus de vérification / révocation / restauration d'association, plus de milestones, plus de goal, plus de `CURATOR_ROLE`. Il garde : inscription unique d'association (`registerAssociation`) et de campagne (`registerCampaign`, avec hash de budget), mise à jour du hash de budget, dons (+ agrégats donateur et `raised`), décaissements (`recordPayout`), relais de mint de badge (`mintBadge`, adresse du contrat de badge fixée une fois par l'admin). Constructeur `(admin, recorder)`. Rupture assumée : aucune version antérieure n'a été déployée.
- **1.4.0-mvp** *(jamais déployée)* : « notaire » — retrait des gardes métier (statut, transitions, `verified`, `raised == 0`, budget gelé) ; ajout de `recordPayout`.
- **1.3.0-mvp** *(jamais déployée, reconstituée depuis le code)* : statut `Draft`, `publishCampaign`, `revertCampaignToDraft`, `updateCampaignBudget`, `startDate`/`endDate` posés par le contrat.
- **1.2.0-mvp** : struct `Association` avec `sirenHash` indexé, flux de dévérification/restauration, refunds bannis on-chain.
- **1.1.0-mvp** : identifiants Postgres unifiés (`campaignId` et `donationId` = UUID encodés bytes32).
- **1.0.0-mvp** : version initiale.

---

## 1. Vue d'ensemble

`CommonLinkRegistry` est le **registre de preuve** de CommonLink. Il consigne :

1. un **référentiel d'intégrité** : les associations et les campagnes, chacune inscrite **une seule fois et pour toujours** ;
2. le **hash du budget** de chaque campagne (et ses mises à jour) ;
3. les **dons** reçus, attribués donateur → campagne, avec leur preuve fiscale, et les **statistiques cumulées par donateur** ;
4. les **décaissements réglés** (preuve qu'un virement des fonds d'une campagne a été réglé) et les **statistiques par campagne**.

Le contrat ne déplace **aucun fonds** et **ne peut en recevoir aucun** (§1.3). Les montants sont en cents EUR (`uint96`). Un don enregistré
est définitif. Aucun remboursement n'existe on-chain (un don n'est jamais remboursé) ; si le cas apparaissait un jour, il vivrait dans un
satellite dédié, jamais dans le hub.

**Hub + satellites.** Ce contrat est le **hub** : immuable, il détient l'état canonique et **n'appelle jamais un autre contrat**. Les
fonctionnalités futures (badge soulbound, etc.) sont des **satellites** déployés à part, qui lisent le hub via
`ICommonLinkRegistryReader` (adresse du registre immuable chez eux, vérifiée par ERC-165 et `READER_VERSION`). Le hub ne connaît aucun
satellite.

### 1.1 Principe : un notaire, sans cycle de vie

**Le contrat n'enregistre que des faits déjà décidés et confirmés par le backend. Il ne rejoue aucune règle métier.** Le backend décide, puis seulement il enfile le job on-chain. Ce que la chaîne prouve : « CommonLink a déclaré tel fait à telle date et ne peut plus le réécrire » — **pas** que la règle métier était satisfaite. Ne jamais écrire (doc, UI, fiche) « la chaîne a validé ».

**Inscrit une fois, inscrit pour toujours.** Une association est inscrite quand elle devient apte à collecter (Mollie COMPLETED) ; une campagne à son premier passage LIVE. Elles ne sont **jamais retirées, suspendues ni réactivées** on-chain : rendre une entité inaccessible en écriture est une décision du backend ; on-chain, elle existe toujours et reste lisible. (À rouvrir seulement si un besoin réel de cycle de vie on-chain apparaît.)

| Reste 100 % backend (le contrat ne le contrôle PAS) | |
|---|---|
| Statut de campagne, transitions, clôture, retour en brouillon | aucun statut on-chain |
| Éligibilité de l'association (KYC, révocation, gel) | l'inscription est définitive |
| Goals, objectifs, critères d'attribution des badges | calculés par le backend |
| Confirmation de paiement, solde disponible, éligibilité d'un décaissement | règles off-chain |
| Allocation don → dépense (FIFO), remboursements | 100 % backend, rien on-chain |

| Gardes conservées (intégrité du registre) | |
|---|---|
| Access control | `RECORDER_ROLE` (écritures) / `PAUSER_ROLE` (pause) / `DEFAULT_ADMIN_ROLE` (reprise, rôles) |
| Pause | arrêt d'urgence de toutes les écritures |
| Anti-doublon | association, SIREN, campagne, don, décaissement |
| Intégrité référentielle | campagne ⇒ association inscrite ; don / décaissement / budget ⇒ campagne inscrite |
| Sanity d'entrée | ids, montants, hashes ≠ 0 ; `RaisedOverflow`, `PaidOutOverflow` |

### 1.2 Sémantique des appels

| Action | Appelant on-chain | Rôle requis |
|---|---|---|
| Inscrire une association / une campagne, mettre à jour un budget | Hot wallet CommonLink | `RECORDER_ROLE` |
| Enregistrer un don / un décaissement | Hot wallet CommonLink | `RECORDER_ROLE` |
| Pause d'urgence | Clé backend dédiée, déclenchée par un responsable conformité | `PAUSER_ROLE` |
| Reprise après pause, octroi / révocation des rôles, transfert d'admin | Multisig CommonLink (Safe) en mainnet | `DEFAULT_ADMIN_ROLE` |

**Aucun appel n'est jamais signé par une association ni par un donateur.** Leurs adresses sont pseudonymes, dérivées côté serveur (HMAC-SHA256), sans clé privée.

### 1.3 Aucun fonds, même par accident

| Bloqué par le code (testé : `NoFundsTest`, contrôle statique `script/check-no-funds.sh`) | |
|---|---|
| xDAI envoyé directement, avec ou sans données | pas de `receive`, pas de `fallback` → revert |
| Valeur jointe à un appel ou au déploiement | aucune fonction `payable`, constructeur compris → revert |
| NFT / jetons « safe » (ERC-721, ERC-1155), ERC-777, `transferAndCall` (ERC-677, jetons pontés Gnosis) | aucun hook de réception, aucun enregistrement ERC-1820 → revert |
| Logique dépendant d'un solde | le code ne lit jamais `address(this).balance` ni un solde de jeton |

| Non bloquable par du code (documenté pour l'audit) | |
|---|---|
| `SELFDESTRUCT` d'un autre contrat vers le hub (transfert de solde toujours effectif après Cancun / EIP-6780) | solde forcé |
| Désignation comme destinataire de frais de bloc ou de retraits de validateur ; envoi à l'adresse avant déploiement | solde forcé |
| `transfer` ERC-20 simple, `transferFrom` ERC-721 non « safe » (ERC-1155 n'a que des transferts « safe », refusés) (le destinataire n'est jamais consulté) | jetons reçus |

Ces fonds restent **bloqués à jamais** : il n'existe **aucune fonction de récupération** (ce serait un chemin de déplacement de fonds).
Ils n'affectent aucune preuve, aucune lecture et aucune écriture (`test_forcedBalance_isInert`).

---

## 2. Constantes et rôles

```solidity
string  public constant VERSION        = "3.0.0-mvp";
uint16  public constant READER_VERSION = 1;                         // ICommonLinkRegistryReader
bytes32 public constant RECORDER_ROLE  = keccak256("RECORDER_ROLE");
bytes32 public constant PAUSER_ROLE    = keccak256("PAUSER_ROLE");
// DEFAULT_ADMIN_ROLE : AccessControlDefaultAdminRules (OZ 5)
constructor(uint48 adminDelay, address admin, address recorder, address pauser);
```

- `DEFAULT_ADMIN_ROLE` → multisig Safe CommonLink (gouvernance, 2/3 minimum) en mainnet ; jamais détenu par le backend. Un seul
  admin (`defaultAdmin()`). Il ne change que par **transfert en deux étapes** : `beginDefaultAdminTransfer(nouveau)` puis, après
  `defaultAdminDelay()`, `acceptDefaultAdminTransfer()` signé par le nouvel admin (annulable entre-temps). `grantRole` / `revokeRole`
  sur ce rôle revertent ; **`renounceRole` aussi** (`AdminRenounceForbidden`) : sans admin, aucune clé ne pourrait plus être changée
  après une compromission et une pause ne pourrait plus être levée. Délai (décision 40) : 0 sur Anvil, 1 h sur Chiado, 3 jours sur
  Gnosis. Le délai ne ralentit que le transfert d'admin (pas la pause, pas la gestion des rôles).
- `RECORDER_ROLE` → hot wallet backend (`OnchainJobWorker`), seul rôle d'écriture.
- `PAUSER_ROLE` → clé backend **dédiée** (distincte du recorder : flux de nonce propre, gel possible même si le recorder est bloqué),
  déclenchée par un responsable conformité. Ne peut que geler.

Le contrat hérite de `Pausable` : `pause()` (PAUSER) gèle toutes les écritures (inscriptions, budget, dons, décaissements) ;
`unpause()` est réservé à l'admin. Les lectures restent disponibles.

| Point de mutabilité du hub | Contrôlé par | Gelable ? |
|---|---|---|
| Octroi / révocation RECORDER, PAUSER | admin (Safe) | Non (rotation des clés) |
| Pause | PAUSER (geler) / admin (reprendre) | Non ; bloque les écritures, n'altère aucune preuve |
| Transfert d'admin, délai | admin, en deux étapes avec délai | — ; renonciation interdite |
| Données enregistrées | personne | Écrites une fois, jamais modifiées (sauf `budgetHash`, re-déclaré) |

---

## 2.5 Convention d'identifiants (CRITIQUE)

> Cette section définit la convention d'encodage des identifiants Postgres en `bytes32` Solidity. **Tout le système en dépend.** Une seule classe backend doit gérer la conversion.

### 2.5.1 Principe

**Tous les identifiants métier on-chain (`campaignId`, `donationId`) sont les UUIDs Postgres correspondants, encodés en `bytes32`.**

Aucun identifiant on-chain ne doit jamais être généré on-chain (pas d'auto-incrément, pas de hash dérivé). Postgres est **la source de vérité d'identité** ; le contrat ne fait que recevoir et stocker.

### 2.5.2 Encodage

Un UUID standard fait 128 bits (16 bytes). Un `bytes32` fait 256 bits (32 bytes). L'encodage met l'UUID dans les **16 premiers bytes**, les 16 bytes restants sont à zéro.

**Côté Kotlin (Spring backend)** :

```kotlin
fun UUID.toBytes32(): ByteArray {
    val buffer = ByteBuffer.allocate(32)
    buffer.putLong(this.mostSignificantBits)
    buffer.putLong(this.leastSignificantBits)
    // bytes 16-31 restent à zéro (init par défaut)
    return buffer.array()
}

fun ByteArray.toUUID(): UUID {
    require(this.size == 32) { "bytes32 expected" }
    val buffer = ByteBuffer.wrap(this, 0, 16)
    return UUID(buffer.long, buffer.long)
}
```

**Côté Solidity** : aucune conversion nécessaire — le contrat reçoit et stocke `bytes32` opaquement. La sémantique "c'est un UUID" est invisible on-chain.

### 2.5.3 Point d'entrée unique côté backend

**Règle absolue** : une **seule** classe (`OnchainCodec.uuidToBytes32`, package `org.commonlink.onchain`) gère la conversion UUID ↔ Bytes32. Tous les autres composants l'utilisent.

Toute conversion ad-hoc dans le code (ex : un service qui réimplémente la conversion) est un anti-pattern et doit échouer la code review.

### 2.5.4 Garantie d'unicité

L'unicité des identifiants on-chain est garantie par :
- **L'unicité Postgres** (PRIMARY KEY, contraintes DB).
- **La vérification on-chain** dans `registerCampaign` et `recordDonation` (`CampaignAlreadyRegistered`, `DonationAlreadyExists`) ; idem `recordPayout` (`PayoutAlreadyRecorded`).

Si le backend tente d'enregistrer deux fois le même `donationId` (cas d'idempotence webhook mal géré), le contrat revert. **C'est une protection désirée**, pas un bug.

### 2.5.5 Affichage utilisateur

Le frontend affiche les identifiants sous leur forme **UUID Postgres native** (ou un slug court dérivé type `DON-2026-A7X3`), jamais sous forme `bytes32` hexadécimale. La représentation `bytes32` est un détail technique invisible.

---

## 2.6 Convention SIREN canonique (CRITIQUE)

> **Section nouvelle v1.2** — définit le format canonique du SIREN avant hash. Toute déviation casse l'unicité du `sirenHashToAssociation`.

### 2.6.1 Principe

Le SIREN (9 chiffres) identifie l'**entité juridique** de l'association (pas un établissement particulier). Il est :
- **Stable à vie** d'une asso, contrairement au SIRET qui change si l'asso change d'adresse.
- **Donnée publique** (consultable au RNA / data.gouv.fr), donc compatible RGPD pour stockage on-chain sous forme hashée.
- **Permet l'unicité** : une asso = un SIREN = un sirenHash on-chain = une seule adresse vérifiée.

### 2.6.2 Forme canonique

Avant hash, le SIREN doit être normalisé en :
- Chaîne ASCII de **9 chiffres exactement**, sans espace ni caractère de séparation.
- Pas de zéro initial supprimé (ex : `012345678` reste `012345678`).

Exemple : `"775672594"` (SIREN de la Croix-Rouge française).

### 2.6.3 Calcul du hash

```solidity
bytes32 sirenHash = keccak256(bytes(canonicalSiren));
```

Côté Kotlin :

```kotlin
fun computeSirenHash(siren: String): ByteArray {
    require(siren.length == 9 && siren.all { it.isDigit() }) {
        "SIREN must be exactly 9 digits"
    }
    return Hash.sha3(siren.toByteArray(Charsets.US_ASCII))  // keccak256 via web3j
}
```

### 2.6.4 Point d'entrée unique côté backend

**Règle absolue** : une seule fonction (`OnchainCodec.keccakSiren`, package `org.commonlink.onchain`) calcule le hash. Comme pour la conversion d'identifiants, toute implémentation ad-hoc échoue la code review.

### 2.6.5 Stockage de la correspondance claire ↔ hash

Le backend conserve la table `association_kyc` en Postgres :

```sql
CREATE TABLE association_kyc (
    id          UUID PRIMARY KEY,
    siren       VARCHAR(9) NOT NULL UNIQUE,
    siren_hash  BYTEA(32)  NOT NULL UNIQUE,
    rna_number  VARCHAR(20),
    legal_name  TEXT NOT NULL,
    created_at  TIMESTAMPTZ NOT NULL
);
```

Le SIREN clair est en DB (donnée publique, nécessaire à l'audit et à la génération des reçus). Le hash on-chain ne permet pas de retrouver le SIREN par brute-force inverse (9 chiffres = 10⁹ combinaisons, soit ~30 bits — un attaquant peut tester tous les SIREN existants en quelques secondes). **Le hash ne protège donc PAS la confidentialité du SIREN** ; il sert uniquement à :
1. Économiser de l'espace on-chain (32 bytes au lieu de 9 chars + padding).
2. Standardiser le format (toujours 32 bytes peu importe la source).
3. Permettre la comparaison d'unicité de manière efficace.

C'est acceptable car le SIREN est une donnée publique. **Ne jamais appliquer cette méthode à des données réellement sensibles** (numéro de sécurité sociale, etc.).

---

## 3. Types et stockage

Les structs sont déclarées dans `src/interfaces/ICommonLinkRegistryReader.sol` (les satellites compilent sans importer le hub). Elles ne
contiennent que des champs statiques : leur encodage ABI est la suite plate de leurs champs. **Toute modification d'une struct ou d'un
getter impose d'incrémenter `READER_VERSION`** : l'interfaceId ERC-165 ne voit que les sélecteurs, pas la forme des structs
(`test_structShapes_pinned` fige cette forme).

```solidity
struct Association { bytes32 sirenHash; uint32 registeredAt; }                       // registeredAt == 0 : jamais inscrite
struct Campaign    { address association; uint96 raised; uint32 registeredAt; bytes32 budgetHash; } // association == 0 : jamais inscrite
struct Donation    { address donor; uint96 amount; bytes32 campaignId; uint32 timestamp; bytes32 receiptHash; bytes32 txRef; }
struct DonorStats  { uint128 totalDonated; uint32 donationCount; uint32 uniqueCampaigns; uint32 firstDonationAt; }
struct Payout      { bytes32 campaignId; uint96 amount; uint32 timestamp; bytes32 txRef; }   // campaignId == 0 : jamais enregistré
struct CampaignStats { uint96 totalPaidOut; uint32 payoutCount; uint32 donationCount; uint32 uniqueDonors; uint32 lastDonationAt; uint32 lastPayoutAt; } // 1 slot

// Stockage internal : la seule surface de lecture est l'ensemble des getters get* (§4.5).
mapping(address => Association)                 internal associations;
mapping(bytes32 => address)                     internal sirenHashToAssociation; // un SIREN n'est inscrit qu'une fois
mapping(bytes32 => Campaign)                    internal campaigns;
mapping(bytes32 => Donation)                    internal donations;               // write-once
mapping(address => DonorStats)                  internal donorStats;
mapping(address => mapping(bytes32 => uint128)) internal donorCampaignTotal;
mapping(bytes32 => Payout)                      internal payouts;                 // write-once
mapping(bytes32 => CampaignStats)               internal campaignStats;
```

`Campaign` tient en deux slots (association + raised ; registeredAt + budgetHash). `raised` est la somme des dons enregistrés. Il n'y a ni goal, ni statut, ni date de fin, ni nombre de jalons.

---

## 4. Fonctions

### 4.1 Référentiel (inscription unique)

| Fonction | Gardes | Effet / event |
|---|---|---|
| `registerAssociation(address association, bytes32 sirenHash)` | RECORDER, pause ; adresse ≠ 0 (`InvalidAssociation`) ; `sirenHash` ≠ 0 (`EmptySirenHash`) ; jamais inscrite (`AssociationAlreadyRegistered`) ; SIREN jamais inscrit (`SirenAlreadyRegistered`) | `{sirenHash, registeredAt}` + index SIREN ; `AssociationRegistered` |
| `registerCampaign(bytes32 campaignId, address association, bytes32 budgetHash)` | RECORDER, pause ; id ≠ 0 (`EmptyCampaignId`) ; jamais inscrite (`CampaignAlreadyRegistered`) ; association inscrite (`AssociationNotRegistered`) ; budget ≠ 0 (`EmptyBudgetHash`) | `{association, 0, registeredAt, budgetHash}` ; `CampaignRegistered` |
| `updateCampaignBudget(bytes32 campaignId, bytes32 newBudgetHash)` | RECORDER, pause ; campagne inscrite (`CampaignNotRegistered`) ; hash ≠ 0 | nouveau `budgetHash` ; `CampaignBudgetUpdated(old, new)` (re-déclarer le même hash est inoffensif) |

Aucune fonction ne retire, suspend, réactive ni ne modifie une inscription.

### 4.2 Dons — `recordDonation(donationId, donor, campaignId, amount, receiptHash, txRef)`

RECORDER, pause. Gardes : campagne inscrite (`CampaignNotRegistered`) ; `donationId` ≠ 0 et jamais utilisé (`EmptyDonationId`, `DonationAlreadyExists`) ; `donor` ≠ 0 (`InvalidDonor`) ; `amount` ≠ 0 (`InvalidAmount`) ; `receiptHash`, `txRef` ≠ 0 ; `raised + amount` tient en `uint96` (`RaisedOverflow`). **Aucun contrôle** de statut de campagne, d'éligibilité de l'association ni de confirmation de paiement : c'est le backend qui n'enfile que des dons confirmés sur des campagnes qu'il juge ouvertes.

Effets : don écrit, `raised` incrémenté, `donorStats` (premier don, total, nombre, campagnes uniques), `donorCampaignTotal` et `campaignStats` (nombre de dons, donateurs uniques, date du dernier don) mis à jour. Events : `DonationRecorded(donor, campaignId, association, …)` (3 champs indexés), `DonorFirstDonation`, `DonorJoinedCampaign`.

### 4.3 Décaissements — `recordPayout(payoutId, campaignId, amountCents, txRef)`

RECORDER, pause. Gardes : campagne inscrite ; `payoutId` ≠ 0 et jamais utilisé (`EmptyPayoutId`, `PayoutAlreadyRecorded`) ; montant ≠ 0 ; `txRef` ≠ 0 (`EmptyTxRef`) ; `totalPaidOut + amount` tient en `uint96` (`PaidOutOverflow`). **Aucun contrôle** de solde (pas de « décaissé ≤ collecté »), de statut ni d'éligibilité. Effets : fiche `payouts[payoutId] = {campaignId, amount, timestamp, txRef}` ; `campaignStats` (total et nombre des décaissements, date du dernier) ; event `PayoutRecorded(payoutId, campaignId, amount, txRef, timestamp)`. `txRef` = référence du virement Bridge en texte (32 octets max). Aucun fonds n'est déplacé.

### 4.4 Badges

Plus rien dans le hub (3.0). Le badge soulbound est un **satellite** (`DonorBadge`, sprint C2) qui détient son propre `MINTER_ROLE`
et lit le hub (« donateur connu » = `getDonorStats(to).firstDonationAt != 0`). Le hub ne connaît pas son adresse.

### 4.5 Pause, gouvernance et lectures

- `pause()` — `PAUSER_ROLE` ; `unpause()` — `DEFAULT_ADMIN_ROLE`.
- Gouvernance (OpenZeppelin `AccessControlDefaultAdminRules`) : `defaultAdmin`, `defaultAdminDelay`, `pendingDefaultAdmin`,
  `beginDefaultAdminTransfer`, `acceptDefaultAdminTransfer`, `cancelDefaultAdminTransfer`, `changeDefaultAdminDelay`,
  `rollbackDefaultAdminDelay`, `grantRole` / `revokeRole` (hors admin), `renounceRole` (hors admin).
- Lectures (`ICommonLinkRegistryReader`) : `READER_VERSION`, `getAssociation`, `getCampaign`, `getDonation`, `getDonorStats`,
  `getDonorCampaignTotal`, `getPayout`, `getCampaignStats` ; `supportsInterface` (reader, AccessControl, DefaultAdminRules, ERC-165).
  **Aucun getter de liste** : les listes se reconstituent depuis les events indexés (ou depuis Postgres). L'index SIREN → adresse n'a
  pas de getter : la correspondance est dans l'event `AssociationRegistered` (indexé sur les deux).

---

## 5. Erreurs et events

```solidity
error InvalidRecorder(); error InvalidPauser(); error AdminRenounceForbidden();
error InvalidAssociation(); error EmptySirenHash();
error AssociationAlreadyRegistered(address); error SirenAlreadyRegistered(bytes32, address); error AssociationNotRegistered(address);
error EmptyCampaignId(); error CampaignAlreadyRegistered(bytes32); error CampaignNotRegistered(bytes32); error EmptyBudgetHash();
error EmptyDonationId(); error DonationAlreadyExists(bytes32); error InvalidDonor(); error InvalidAmount();
error EmptyReceiptHash(); error EmptyTxRef(); error RaisedOverflow(bytes32, uint96, uint96);
error EmptyPayoutId(); error PayoutAlreadyRecorded(bytes32); error PaidOutOverflow(bytes32, uint96, uint96);
// + erreurs OpenZeppelin : AccessControlUnauthorizedAccount, AccessControlInvalidDefaultAdmin,
//   AccessControlEnforcedDefaultAdminRules, AccessControlEnforcedDefaultAdminDelay, EnforcedPause, ExpectedPause

event AssociationRegistered(address indexed association, bytes32 indexed sirenHash, uint32 registeredAt);
event CampaignRegistered(bytes32 indexed campaignId, address indexed association, bytes32 budgetHash, uint32 registeredAt);
event CampaignBudgetUpdated(bytes32 indexed campaignId, bytes32 oldBudgetHash, bytes32 newBudgetHash, uint32 timestamp);
event DonationRecorded(address indexed donor, bytes32 indexed campaignId, address indexed association, bytes32 donationId, uint96 amount, bytes32 receiptHash, bytes32 txRef);
event DonorFirstDonation(address indexed donor, uint32 timestamp);
event DonorJoinedCampaign(address indexed donor, bytes32 indexed campaignId);
event PayoutRecorded(bytes32 indexed payoutId, bytes32 indexed campaignId, uint96 amount, bytes32 txRef, uint32 timestamp);
// + events OpenZeppelin : RoleGranted, RoleRevoked, Paused, Unpaused, DefaultAdminTransferScheduled,
//   DefaultAdminTransferCanceled, DefaultAdminDelayChangeScheduled, DefaultAdminDelayChangeCanceled
```

---

## 6. Invariants (Foundry, `test/CommonLinkRegistry.invariant.t.sol`)

- **INV-1** une inscription est définitive : `sirenHash`, `registeredAt`, association propriétaire d'une campagne ne changent jamais ; `budgetHash` jamais vide.
- **INV-2** chaque identifiant (association, campagne, don, décaissement) n'est écrit qu'une fois.
- **INV-3** `raised` = somme des dons enregistrés sur la campagne.
- **INV-4** statistiques donateur = somme des dons enregistrés pour le donateur.
- **INV-5** le registre ne refuse jamais une écriture pour une autre raison que ses gardes d'intégrité (aucune règle métier).
- **INV-6** statistiques de campagne = dons et décaissements enregistrés.
- **INV-7** le registre ne détient jamais de fonds.
- **INV-8** aucune écriture acceptée pendant une pause ; seul le PAUSER met en pause ; seul l'admin reprend ; l'admin ne change pas
  hors du transfert en deux étapes ; recorder et pauser gardent leur rôle.

Le handler travaille sur de petits domaines d'identifiants pour rejouer réellement des doublons, et pilote `pause` / `unpause` par le
pauser, l'admin, le recorder et un tiers ; des mutations manuelles (retrait d'un anti-doublon, réintroduction d'une garde métier,
`raised` non mis à jour, `unpause` ouvert au PAUSER, ajout d'un `receive()`) font échouer les tests.

---

## 7. Hors périmètre — à ne PAS implémenter

- ❌ Toute règle métier (statut, transitions, KYC, solde, frais, barèmes, critères de badge).
- ❌ Tout cycle de vie on-chain : retrait, suspension, réactivation, révocation d'association ou de campagne ; réattribution d'un SIREN.
- ❌ Goals, milestones, objectifs.
- ❌ Refund / annulation de don ; allocation don → dépense ; réception ou transfert de fonds, fonction de récupération ;
  upgradeabilité ; listes on-chain ; appel vers un satellite ; renonciation à l'admin.

---

## 8. Hors-contrat — engagements côté backend

- **Décider avant d'enregistrer** : inscription d'association quand Mollie passe COMPLETED ; inscription de campagne à son premier LIVE ; don seulement confirmé et sur une campagne ouverte ; décaissement seulement réglé par la banque. Le contrat ne revérifie rien de tout cela.
- **Ordonner l'outbox** : association → campagne → dons / décaissements / budget (dépendances de jobs) ; un enregistrement sur une campagne non inscrite reverte (`CampaignNotRegistered`).
- **Idempotence** : `correlationKey` unique par fait ; ne jamais réutiliser un UUID ; mono-worker (un seul flux de nonce pour la clé recorder).
- **Conventions** : UUID → bytes32 (§2.5), SIREN canonique (§2.6), budget canonicalisé JCS avant `keccak256`.
- **Remboursements** : n'existent pas (un don n'est jamais remboursé) ; rien on-chain. Satellite dédié seulement si le cas survient.
- **Pause** : déclenchée par un responsable conformité via la clé PAUSER du backend ; la reprise passe par l'admin (Safe).

---

## 9. Déploiement

`script/deploy-registry.sh <anvil|chiado|gnosis>` → `script/DeployRegistry.s.sol` (`ADMIN_ADDR`, `RECORDER_ADDR`, `PAUSER_ADDR`,
`ADMIN_DELAY`, `ADMIN_PK`). Admin, recorder et pauser distincts ; délai nul refusé hors Anvil ; contrôles après déploiement : admin,
délai, rôles, interface de lecture, solde nul. Les satellites se déploient ensuite avec l'adresse du hub ; rien à câbler sur le hub.

- **Gnosis Chiado (10200)** : testnet, recette staging.
- **Gnosis (100)** : après audit externe ; `DEFAULT_ADMIN_ROLE` transféré à un Safe 2/3 par le transfert en deux étapes ; aucune reprise de l'historique : seuls les faits postérieurs au passage en réel sont enregistrés.

---

## Annexe A — Coûts gas mesurés

| Fonction | Gas médian | Gas max |
|---|---|---|
| `registerAssociation` | ~95 900 | ~95 900 |
| `registerCampaign` | ~98 200 | ~98 200 |
| `updateCampaignBudget` | ~28 600 | ~35 900 |
| `recordDonation` | ~221 800 | ~221 900 |
| `recordPayout` | ~101 500 | ~121 700 |
| `pause` | ~35 500 | ~47 100 |
| `unpause` | ~23 700 | ~25 100 |
| Déploiement | ~2 180 200 | — |

Taille du contrat : 9 320 octets de runtime (limite 24 576). Mesures 3.0.0-mvp (`forge test --gas-report`, médianes sur la suite de
tests, cas de revert inclus).

Mesures `forge test --gas-report` (médiane). Le gas est payé en xDAI sur Gnosis ; aucun coût en € n'est avancé ici : il dépend du prix du gas observé et sera mesuré sur Chiado.

---

## Annexe B — Glossaire

| Terme | Définition |
|---|---|
| **EOA** | Externally Owned Account — compte contrôlé par une clé privée (admin, recorder et pauser côté CommonLink). |
| **Safe** | Multisig Safe, contrat de portefeuille à plusieurs signataires (gouvernance de `DEFAULT_ADMIN_ROLE`). |
| **SIREN** | 9 chiffres identifiant une entité juridique. Seul son hash est on-chain. |
| **JCS** | JSON Canonicalization Scheme — RFC 8785. |
| **UUID** | Identifiant Postgres de 128 bits, encodé en `bytes32` (16 octets + 16 octets nuls). |
| **Soulbound** | Jeton non transférable, attaché définitivement à une adresse (badges). |
| **Pausable / AccessControlDefaultAdminRules** | Patterns OpenZeppelin (arrêt d'urgence ; rôles avec admin unique transféré en deux étapes). |
| **Hub / satellite** | Le hub (ce registre) détient l'état et n'appelle personne ; un satellite est un contrat séparé qui le lit. |
| **ERC-165** | Standard de détection d'interface (`supportsInterface`). |
