# CommonLinkRegistry — Spécification (MVP)

> Source de vérité pour le smart contract `CommonLinkRegistry`. **Le code (`src/CommonLinkRegistry.sol`) et les tests Foundry font foi** ;
> toute déviation de ce document est un bug de documentation.
>
> **Version**: 2.0.0-mvp
> **Solidity**: 0.8.30 (EVM `cancun`)
> **Stack**: Foundry + OpenZeppelin 5.x
> **Cibles**: Gnosis Chiado (10200, testnet) → Gnosis (100, mainnet) — gas en xDAI
> **Principe directeur**: la blockchain est invisible à l'utilisateur final. CommonLink atteste, les utilisateurs ne signent jamais.

### Changelog

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
4. les **décaissements réglés** (preuve qu'un virement des fonds d'une campagne a été réglé) ;
5. les **mints de badges** soulbound, relayés vers le contrat de badges.

Le contrat ne déplace **aucun fonds**. Les montants sont en cents EUR (`uint96`). Un don enregistré est définitif : un remboursement fiat est tracé en Postgres et n'altère jamais le registre.

### 1.1 Principe : un notaire, sans cycle de vie

**Le contrat n'enregistre que des faits déjà décidés et confirmés par le backend. Il ne rejoue aucune règle métier.** Le backend décide, puis seulement il enfile le job on-chain. Ce que la chaîne prouve : « CommonLink a déclaré tel fait à telle date et ne peut plus le réécrire » — **pas** que la règle métier était satisfaite. Ne jamais écrire (doc, UI, fiche) « la chaîne a validé ».

**Inscrit une fois, inscrit pour toujours.** Une association est inscrite quand elle devient apte à collecter (Mollie COMPLETED) ; une campagne à son premier passage LIVE. Elles ne sont **jamais retirées, suspendues ni réactivées** on-chain : rendre une entité inaccessible en écriture est une décision du backend ; on-chain, elle existe toujours et reste lisible. (À rouvrir seulement si un besoin réel de cycle de vie on-chain apparaît.)

| Reste 100 % backend (le contrat ne le contrôle PAS) | |
|---|---|
| Statut de campagne, transitions, clôture, retour en brouillon | aucun statut on-chain |
| Éligibilité de l'association (KYC, révocation, gel) | l'inscription est définitive |
| Goals, objectifs, critères d'attribution des badges | calculés par le backend |
| Confirmation de paiement, solde disponible, éligibilité d'un décaissement | règles off-chain |

| Gardes conservées (intégrité du registre) | |
|---|---|
| Access control | `RECORDER_ROLE` (écritures) / `DEFAULT_ADMIN_ROLE` (pause, adresse du badge) |
| Pause | arrêt d'urgence de toutes les écritures |
| Anti-doublon | association, SIREN, campagne, don, décaissement ; doublon de badge refusé par le contrat de badges |
| Intégrité référentielle | campagne ⇒ association inscrite ; don / décaissement / budget ⇒ campagne inscrite ; badge ⇒ donateur ayant au moins un don |
| Sanity d'entrée | ids, montants, hashes ≠ 0 ; `RaisedOverflow` |

### 1.2 Sémantique des appels

| Action | Appelant on-chain | Rôle requis |
|---|---|---|
| Inscrire une association / une campagne, mettre à jour un budget | Hot wallet CommonLink | `RECORDER_ROLE` |
| Enregistrer un don / un décaissement | Hot wallet CommonLink | `RECORDER_ROLE` |
| Minter un badge (relais) | Hot wallet CommonLink | `RECORDER_ROLE` |
| Fixer l'adresse du contrat de badges (une fois), pause d'urgence | Multisig CommonLink (Safe) | `DEFAULT_ADMIN_ROLE` |

**Aucun appel n'est jamais signé par une association ni par un donateur.** Leurs adresses sont pseudonymes, dérivées côté serveur (HMAC-SHA256), sans clé privée.

---

## 2. Constantes et rôles

```solidity
bytes32 public constant RECORDER_ROLE = keccak256("RECORDER_ROLE");
string  public constant VERSION       = "2.0.0-mvp";
// DEFAULT_ADMIN_ROLE hérité d'AccessControl OZ
```

- `DEFAULT_ADMIN_ROLE` → multisig Safe CommonLink (gouvernance, 2/3 minimum) en mainnet ; jamais détenu par le backend.
- `RECORDER_ROLE` → hot wallet backend (`OnchainJobWorker`), seul rôle d'écriture.

Le contrat hérite de `Pausable` : la pause globale gèle toutes les écritures (inscriptions, budget, dons, décaissements, badges).

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

```solidity
struct Association { bytes32 sirenHash; uint32 registeredAt; }                       // registeredAt == 0 : jamais inscrite
struct Campaign    { address association; uint96 raised; uint32 registeredAt; bytes32 budgetHash; } // association == 0 : jamais inscrite
struct Donation    { address donor; uint96 amount; bytes32 campaignId; uint32 timestamp; bytes32 receiptHash; bytes32 txRef; }
struct DonorStats  { uint128 totalDonated; uint32 donationCount; uint32 uniqueCampaigns; uint32 firstDonationAt; }

mapping(address => Association)                 public associations;
mapping(bytes32 => address)                     public sirenHashToAssociation; // un SIREN n'est inscrit qu'une fois
mapping(bytes32 => Campaign)                    public campaigns;
mapping(bytes32 => Donation)                    public donations;               // write-once
mapping(address => DonorStats)                  public donorStats;
mapping(address => mapping(bytes32 => uint128)) public donorCampaignTotal;
mapping(bytes32 => bool)                        public payoutRecorded;          // write-once
IDonorBadge                                     public badgeContract;           // fixé une fois par l'admin
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

RECORDER, pause, `nonReentrant`. Gardes : campagne inscrite (`CampaignNotRegistered`) ; `donationId` ≠ 0 et jamais utilisé (`EmptyDonationId`, `DonationAlreadyExists`) ; `donor` ≠ 0 (`InvalidDonor`) ; `amount` ≠ 0 (`InvalidAmount`) ; `receiptHash`, `txRef` ≠ 0 ; `raised + amount` tient en `uint96` (`RaisedOverflow`). **Aucun contrôle** de statut de campagne, d'éligibilité de l'association ni de confirmation de paiement : c'est le backend qui n'enfile que des dons confirmés sur des campagnes qu'il juge ouvertes.

Effets : don écrit, `raised` incrémenté, `donorStats` (premier don, total, nombre, campagnes uniques) et `donorCampaignTotal` mis à jour. Events : `DonationRecorded(donor, campaignId, association, …)` (3 champs indexés), `DonorFirstDonation`, `DonorJoinedCampaign`.

### 4.3 Décaissements — `recordPayout(payoutId, campaignId, amountCents)`

RECORDER, pause. Gardes : campagne inscrite ; `payoutId` ≠ 0 et jamais utilisé (`EmptyPayoutId`, `PayoutAlreadyRecorded`) ; montant ≠ 0. **Aucun contrôle** de solde, de statut ni d'éligibilité. Effet : `payoutRecorded[payoutId] = true` ; event `PayoutRecorded(payoutId, campaignId, amount, timestamp)`. Aucun fonds n'est déplacé.

### 4.4 Badges

- `setBadgeContract(address badge)` — `DEFAULT_ADMIN_ROLE`. **Write-once** : `badge` doit être un contrat (`InvalidBadgeContract`) et l'adresse n'est fixable qu'une fois (`BadgeContractAlreadySet`). Conséquence assumée : un défaut du contrat de badges impose de redéployer le registre.
- `mintBadge(address donor, uint16 badgeType) returns (uint256 tokenId)` — RECORDER, pause, `nonReentrant`. Gardes : badge configuré (`BadgeContractNotSet`) ; donateur ayant au moins un don enregistré (`DonorUnknown`). Appelle `IDonorBadge.mint(donor, badgeType)` et émet `BadgeMinted(donor, badgeType, tokenId)`. Les règles d'attribution sont **off-chain** ; le doublon (même donateur, même type) est refusé par le contrat de badges.
- `src/interfaces/IDonorBadge.sol` est **figée** : le contrat de badges (sprint B5) doit l'implémenter telle quelle et n'accepter de mint que du registre.

### 4.5 Pause et lectures

`pause()` / `unpause()` — `DEFAULT_ADMIN_ROLE`. Lectures : `getAssociation`, `getCampaign`, `getDonation`, `getDonorStats`, `getDonorCampaignTotal`, `payoutRecorded`, `badgeContract`, `sirenHashToAssociation`. **Aucun getter de liste** : les listes se reconstituent depuis les events indexés (ou depuis Postgres).

---

## 5. Erreurs et events

```solidity
error InvalidAdmin();
error InvalidAssociation(); error EmptySirenHash();
error AssociationAlreadyRegistered(address); error SirenAlreadyRegistered(bytes32, address); error AssociationNotRegistered(address);
error EmptyCampaignId(); error CampaignAlreadyRegistered(bytes32); error CampaignNotRegistered(bytes32); error EmptyBudgetHash();
error EmptyDonationId(); error DonationAlreadyExists(bytes32); error InvalidDonor(); error InvalidAmount();
error EmptyReceiptHash(); error EmptyTxRef(); error RaisedOverflow(bytes32, uint96, uint96);
error EmptyPayoutId(); error PayoutAlreadyRecorded(bytes32);
error InvalidBadgeContract(); error BadgeContractAlreadySet(address); error BadgeContractNotSet(); error DonorUnknown(address);

event AssociationRegistered(address indexed association, bytes32 indexed sirenHash, uint32 registeredAt);
event CampaignRegistered(bytes32 indexed campaignId, address indexed association, bytes32 budgetHash, uint32 registeredAt);
event CampaignBudgetUpdated(bytes32 indexed campaignId, bytes32 oldBudgetHash, bytes32 newBudgetHash, uint32 timestamp);
event DonationRecorded(address indexed donor, bytes32 indexed campaignId, address indexed association, bytes32 donationId, uint96 amount, bytes32 receiptHash, bytes32 txRef);
event DonorFirstDonation(address indexed donor, uint32 timestamp);
event DonorJoinedCampaign(address indexed donor, bytes32 indexed campaignId);
event PayoutRecorded(bytes32 indexed payoutId, bytes32 indexed campaignId, uint96 amount, uint32 timestamp);
event BadgeContractSet(address indexed badgeContract);
event BadgeMinted(address indexed donor, uint16 indexed badgeType, uint256 tokenId);
```

---

## 6. Invariants (Foundry, `test/CommonLinkRegistry.invariant.t.sol`)

- **INV-1** une inscription est définitive : `sirenHash`, `registeredAt`, association propriétaire d'une campagne ne changent jamais ; `budgetHash` jamais vide.
- **INV-2** chaque identifiant (association, SIREN, campagne, don, décaissement, couple badge) n'est écrit qu'une fois.
- **INV-3** `raised` = somme des dons enregistrés sur la campagne.
- **INV-4** statistiques donateur = somme des dons enregistrés pour le donateur.
- **INV-5** le registre ne refuse jamais une écriture pour une autre raison que ses gardes d'intégrité (aucune règle métier).
- **INV-6** le registre ne détient jamais de fonds.

Le handler travaille sur de petits domaines d'identifiants pour rejouer réellement des doublons ; des mutations manuelles (retrait d'un anti-doublon, réintroduction d'une garde métier, `raised` non mis à jour) font échouer les invariants.

---

## 7. Hors périmètre — à ne PAS implémenter

- ❌ Toute règle métier (statut, transitions, KYC, solde, frais, barèmes, critères de badge).
- ❌ Tout cycle de vie on-chain : retrait, suspension, réactivation, révocation d'association ou de campagne ; réattribution d'un SIREN.
- ❌ Goals, milestones, objectifs.
- ❌ Refund / annulation de don ; transfert de fonds ; upgradeabilité ; listes on-chain.

---

## 8. Hors-contrat — engagements côté backend

- **Décider avant d'enregistrer** : inscription d'association quand Mollie passe COMPLETED ; inscription de campagne à son premier LIVE ; don seulement confirmé et sur une campagne ouverte ; décaissement seulement réglé par la banque ; badge selon des règles off-chain. Le contrat ne revérifie rien de tout cela.
- **Ordonner l'outbox** : association → campagne → dons / décaissements / budget (dépendances de jobs) ; un enregistrement sur une campagne non inscrite reverte (`CampaignNotRegistered`).
- **Idempotence** : `correlationKey` unique par fait ; ne jamais réutiliser un UUID ; mono-worker (un seul flux de nonce pour la clé recorder).
- **Conventions** : UUID → bytes32 (§2.5), SIREN canonique (§2.6), budget canonicalisé JCS avant `keccak256`.
- **Remboursements fiat** : tracés en Postgres comme événements compensatoires ; le registre n'est jamais modifié.

---

## 9. Déploiement

Ordre : (1) `script/DeployRegistry.s.sol` (`ADMIN_ADDR`, `RECORDER_ADDR`, `ADMIN_PK`), (2) contrat de badges avec le registre comme seul minter (sprint B5), (3) l'admin appelle `setBadgeContract(badge)` (une seule fois).

- **Gnosis Chiado (10200)** : testnet, recette staging.
- **Gnosis (100)** : après audit externe ; `DEFAULT_ADMIN_ROLE` transféré à un Safe 2/3 ; aucune reprise de l'historique : seuls les faits postérieurs au passage en réel sont enregistrés.

---

## Annexe A — Coûts gas mesurés

| Fonction | Gas médian | Gas max |
|---|---|---|
| `registerAssociation` | ~95 900 | ~95 900 |
| `registerCampaign` | ~98 200 | ~98 200 |
| `updateCampaignBudget` | ~36 000 | ~36 000 |
| `recordDonation` (1er don du donateur / donateur connu) | ~200 800 | ~201 000 |
| `recordPayout` | ~53 300 | ~53 400 |
| `mintBadge` (avec un contrat de badges factice ; le coût réel dépendra de `DonorBadge`) | ~88 100 | ~98 800 |
| `setBadgeContract` (une fois) | ~50 300 | ~50 300 |

Taille du contrat : 7 539 octets de runtime (limite 24 576).

Mesures `forge test --gas-report` (médiane). Le gas est payé en xDAI sur Gnosis ; aucun coût en € n'est avancé ici : il dépend du prix du gas observé et sera mesuré sur Chiado.

---

## Annexe B — Glossaire

| Terme | Définition |
|---|---|
| **EOA** | Externally Owned Account — compte contrôlé par une clé privée (seulement admin et recorder côté CommonLink). |
| **Safe** | Multisig Safe, contrat de portefeuille à plusieurs signataires (gouvernance de `DEFAULT_ADMIN_ROLE`). |
| **SIREN** | 9 chiffres identifiant une entité juridique. Seul son hash est on-chain. |
| **JCS** | JSON Canonicalization Scheme — RFC 8785. |
| **UUID** | Identifiant Postgres de 128 bits, encodé en `bytes32` (16 octets + 16 octets nuls). |
| **Soulbound** | Jeton non transférable, attaché définitivement à une adresse (badges). |
| **Pausable / AccessControl** | Patterns OpenZeppelin (arrêt d'urgence, rôles). |
