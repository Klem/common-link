# CommonLinkRegistry — Spécification figée (MVP)

> Source de vérité unique pour l'implémentation du smart contract `CommonLinkRegistry`.
> Toute déviation par rapport à ce document doit faire l'objet d'une discussion explicite.
>
> **Version**: 1.2.0-mvp
> **Solidity**: 0.8.24
> **Stack**: Foundry + OpenZeppelin 5.x
> **Cibles**: Polygon Amoy (testnet MVP) → Polygon mainnet (P1.5)
> **Principe directeur**: la blockchain est invisible à l'utilisateur final. CommonLink atteste, les utilisateurs ne signent jamais.

### Changelog

- **1.2.0-mvp** : Enrichissement de l'attestation association : ajout du struct `Association` (remplace le `bool verifiedAssociations`), ajout du `sirenHash` indexé pour empêcher les doublons, ajout du flux de dévérification/restauration avec events distincts. Vérification on-chain du statut `verified` dans `recordDonation` (soft revoke runtime). Section §2.6 nouvelle (convention SIREN canonique). Refunds explicitement bannis on-chain (cf. §9). Tracking des dépenses (`ExpenseProofRecorded`) déplacé en Phase 1.5.
- **1.1.0-mvp** : Identifiants Postgres unifiés (`campaignId` et `donationId` = UUID encodés bytes32). `donationId` désormais fourni par le backend, suppression de `donationCounter`. Section §2.5 nouvelle (convention d'identifiants). Section §7.8 nouvelle (accès en lecture frontend).
- **1.0.0-mvp** : Version initiale.

---

## 1. Vue d'ensemble

`CommonLinkRegistry` est le **registre unique on-chain** de la plateforme CommonLink. Il consigne :

1. La liste des associations vérifiées (KYC métier acté on-chain), avec leur SIREN hashé et leur cycle de vie (vérification, dévérification, restauration).
2. Les campagnes de collecte créées par ces associations.
3. Les dons reçus, avec leur attribution donateur/campagne et leur preuve fiscale.
4. Les statistiques cumulées par donateur (total, nombre, campagnes uniques, première action).
5. Les atteintes de milestones (jalons de collecte).

Le contrat ne déplace **aucun fonds**. Les montants sont en cents EUR (`uint96`), c'est une couche de **preuve et traçabilité**, pas une couche financière.

**Principe d'immuabilité** : un don enregistré on-chain l'est définitivement. Les remboursements éventuels côté fiat (Stripe refund, virement retour) n'altèrent jamais le registre — ils sont tracés en Postgres comme événements compensatoires. Cette propriété est invariante et structure tous les autres choix.

### Sémantique des appels

| Action | Appelant on-chain | Rôle requis |
|---|---|---|
| Vérifier / dévérifier / restaurer une asso | Hot wallet CommonLink | `CURATOR_ROLE` |
| Créer une campagne | Hot wallet CommonLink | `RECORDER_ROLE` |
| Enregistrer un don | Hot wallet CommonLink | `RECORDER_ROLE` |
| Pauser / Reprendre / Annuler / Compléter | Hot wallet CommonLink | `CURATOR_ROLE` |
| Marquer un milestone atteint | Hot wallet CommonLink | `RECORDER_ROLE` |
| Pause d'urgence globale | Multisig CommonLink | `DEFAULT_ADMIN_ROLE` |

**Aucun appel direct n'est jamais signé par une association ni par un donateur.**

---

## 2. Constantes et rôles

```solidity
bytes32 public constant RECORDER_ROLE = keccak256("RECORDER_ROLE");
bytes32 public constant CURATOR_ROLE  = keccak256("CURATOR_ROLE");
string  public constant VERSION       = "1.2.0-mvp";
// DEFAULT_ADMIN_ROLE hérité d'AccessControl OZ
```

**Attribution des rôles à la mainnet :**
- `DEFAULT_ADMIN_ROLE` → multisig Safe CommonLink (gouvernance, 2/3 minimum)
- `RECORDER_ROLE` → hot wallet backend (`OnchainWorker`)
- `CURATOR_ROLE` → wallet équipe modération (peut être identique au hot wallet en MVP, à séparer en P1.5)

**Le contrat hérite de `Pausable` (OpenZeppelin).** Le pause global gèle `recordDonation`, `createCampaign` et `markMilestoneReached`. Les fonctions de curation (statuts campagne, vérification asso) restent disponibles pour la gouvernance.

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

**Règle absolue** : une **seule** classe (`IdEncoder` ou `Bytes32Codec`) dans `org.commonlink.util` gère la conversion UUID ↔ Bytes32. Tous les autres composants l'utilisent.

Toute conversion ad-hoc dans le code (ex : un service qui réimplémente la conversion) est un anti-pattern et doit échouer la code review.

### 2.5.4 Garantie d'unicité

L'unicité des identifiants on-chain est garantie par :
- **L'unicité Postgres** (PRIMARY KEY, contraintes DB).
- **La vérification on-chain** dans `createCampaign` et `recordDonation` (`require campaigns[id].association == address(0)` et `require donations[id].donor == address(0)`).

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

**Règle absolue** : une seule classe (`SirenHasher` dans `org.commonlink.util`) calcule le hash. Comme pour `IdEncoder`, toute implémentation ad-hoc échoue la code review.

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

## 3. Types et structures

### 3.1 Énumération `CampaignStatus`

```solidity
enum CampaignStatus {
    Active,      // 0 — défaut à la création, dons acceptés
    Paused,      // 1 — temporairement suspendue par CURATOR_ROLE
    Completed,   // 2 — terminée normalement (clôture manuelle ou à endDate)
    Cancelled    // 3 — annulée, dons définitivement bloqués
}
```

**Notes** :
- Pas d'état `Draft` on-chain — une campagne n'apparaît dans le registre qu'au moment de la publication.
- Transitions autorisées :
    - `Active` → `Paused`, `Completed`, `Cancelled`
    - `Paused` → `Active`, `Cancelled`
    - `Completed`, `Cancelled` → **état terminal**, aucune transition

### 3.2 Struct `Association` (NOUVEAU v1.2)

```solidity
struct Association {
    // Slot unique (41 bytes utilisés / mais alignés sur 2 slots à cause de bytes32)
    bool    verified;       //  1 B — statut courant
    bytes32 sirenHash;      // 32 B — keccak256 du SIREN canonique
    uint32  verifiedAt;     //  4 B — timestamp de la vérification en cours (ou la dernière)
    uint32  revokedAt;      //  4 B — 0 si actuellement vérifiée, sinon timestamp dernière révocation
}
```

**Décisions de packing** :
- Le `bytes32 sirenHash` occupe un slot complet à lui seul. Les autres champs (`bool`, deux `uint32`) tiennent dans un second slot avec 23 bytes libres pour évolutions futures (ex : `uint8 rejectionReason`, `address verifier`).
- `verifiedAt` et `revokedAt` peuvent être tous deux > 0 simultanément : `verifiedAt` = timestamp de la **vérification courante**, `revokedAt` = timestamp de la **dernière révocation**. Si actuellement vérifiée (`verified == true`), `revokedAt` peut être > 0 (cas d'une restauration après dévérification).

**Sémantique des états (combinaisons valides)** :

| `verified` | `verifiedAt` | `revokedAt` | Signification |
|---|---|---|---|
| `false` | 0 | 0 | Jamais vérifiée (état initial) |
| `true` | > 0 | 0 | Vérifiée et n'a jamais été révoquée |
| `false` | > 0 | > 0 | Vérifiée puis révoquée (et non restaurée depuis) |
| `true` | > 0 | > 0 | Vérifiée → révoquée → restaurée. `verifiedAt` = date de la restauration. |

### 3.3 Struct `Campaign`

```solidity
struct Campaign {
    // Slot 0 (32 bytes)
    address association;        // 20 B — adresse du Safe asso
    uint96  goal;               // 12 B — objectif en cents EUR

    // Slot 1 (22 bytes utilisés / 10 libres)
    uint96  raised;             // 12 B — collecté en cents EUR
    uint32  startDate;          //  4 B — timestamp unix
    uint32  endDate;            //  4 B — timestamp unix
    CampaignStatus status;      //  1 B — enum
    uint8   milestoneCount;     //  1 B — nombre total de milestones (0-255)

    // Slot 2 (32 bytes)
    bytes32 budgetHash;         // 32 B — keccak256 du JSON canonique (JCS) du budget
}
```

**Note importante** : le `campaignId` (qui sert de clé dans `mapping(bytes32 => Campaign)`) **n'est pas** stocké dans le struct. C'est la clé du mapping, pas un champ. Quand on lit `campaigns[id]`, on a déjà l'ID implicitement.

**Décisions de packing** :
- `uint96` pour les montants : suffisant pour 79 × 10²⁷ cents EUR.
- `uint32` pour les timestamps : valide jusqu'à 2106.
- 10 bytes libres dans slot 1 pour évolutions sans breaking change.

### 3.4 Struct `Donation`

```solidity
struct Donation {
    // Slot 0
    address donor;              // 20 B
    uint96  amount;             // 12 B — cents EUR

    // Slot 1
    bytes32 campaignId;         // 32 B — UUID campagne encodé

    // Slot 2 (4 bytes utilisés / 28 libres)
    uint32  timestamp;          //  4 B — block.timestamp

    // Slot 3
    bytes32 receiptHash;        // 32 B — keccak256 du PDF reçu fiscal

    // Slot 4
    bytes32 txRef;              // 32 B — référence Stripe/Monerium (encodée)
}
```

**Note importante** : comme pour `Campaign`, le `donationId` (UUID Postgres) n'est pas un champ du struct — c'est la clé du mapping `donations`.

**Note sur `txRef`** : Stripe `payment_intent` typique fait ~27 chars. Monerium ref fait ~16 chars. Tous deux tiennent dans 32 bytes. Encodage côté backend en `bytes32(bytes("pi_xxx..."))` avec padding zéro.

### 3.5 Struct `DonorStats`

```solidity
struct DonorStats {
    // Slot unique (28 bytes utilisés / 4 libres)
    uint128 totalDonated;       // 16 B — cumul cents EUR
    uint32  donationCount;      //  4 B — nombre total de dons
    uint32  uniqueCampaigns;    //  4 B — nombre de campagnes distinctes soutenues
    uint32  firstDonationAt;    //  4 B — timestamp du tout premier don (0 si jamais donné)
}
```

**Décision majeure** : un seul slot pour tout. Une mise à jour `donorStats` = **1 SSTORE** (~5k gas après initialisation, ~20k au premier don).

`uint128` pour `totalDonated` permet de gérer 3.4 × 10³⁸ cents — surdimensionné mais on a la place.

---

## 4. Storage layout du contrat

```solidity
// Identités et attestations (v1.2)
mapping(address => Association)                             public associations;
mapping(bytes32 => address)                                 public sirenHashToAssociation;

// Campagnes
mapping(bytes32 => Campaign)                                public campaigns;

// Dons
mapping(bytes32 => Donation)                                public donations;

// Stats donateur
mapping(address => DonorStats)                              public donorStats;
mapping(address => mapping(bytes32 => uint128))             public donorCampaignTotal;
```

**Coût en slots utilisés** (statique, pas les entrées des mappings) : 6 slots.

**Changement v1.2** : remplacement de `mapping(address => bool) verifiedAssociations` par `mapping(address => Association)` qui embarque le SIREN hash et le cycle de vie. Ajout de `sirenHashToAssociation` pour empêcher les doublons d'asso.

**Coût par opération** (avec OpenZeppelin AccessControl + Pausable) :

| Opération | SSTORE neufs | SSTORE update | Total gas approx. |
|---|---|---|---|
| `verifyAssociation` (1ʳᵉ vérification, jamais enregistrée) | 3 (assoc + sirenHash index) | 0 | ~75k |
| `revokeAssociation` | 0 | 1 | ~10k |
| `restoreAssociation` | 0 | 1 | ~10k |
| `createCampaign` | 3 (Campaign) | 0 | ~120k |
| `recordDonation` (1ᵉʳ don du donateur, 1ʳᵉ fois sur cette campagne) | 5 + Donation slots | 1 (campaign.raised) | ~185k |
| `recordDonation` (donateur connu, déjà sur cette campagne) | Donation slots | 3 (campaign + stats + campTotal) | ~95k |
| `markMilestoneReached` | 0 | 0 (event only) | ~35k |

**Surcoût v1.2 vs v1.1** :
- `verifyAssociation` : +50k (3 SSTORE au lieu de 1) — opération rare, acceptable.
- `recordDonation` : +2k (1 SLOAD supplémentaire pour vérifier `associations[campaign.association].verified`). Négligeable.

À ~30 gwei sur Polygon, un `recordDonation` coûte toujours ~0.0005 € maximum. Tenable.

---

## 5. Custom errors

```solidity
// Access & state
error UnauthorizedRole(bytes32 role, address caller);
error ContractPaused();
error AssociationNotVerified(address association);

// Association lifecycle (v1.2)
error EmptySirenHash();
error SirenAlreadyRegistered(bytes32 sirenHash, address existingAssociation);
error AssociationAlreadyVerified(address association);
error AssociationNotRevoked(address association);    // pour restore : doit être révoquée
error AssociationNeverVerified(address association); // pour revoke : doit avoir été vérifiée
error InvalidAssociation();

// Campaign creation
error CampaignAlreadyExists(bytes32 campaignId);
error InvalidDateRange(uint32 startDate, uint32 endDate);
error InvalidGoal();
error EmptyBudgetHash();
error InvalidMilestoneCount();
error EmptyCampaignId();

// Donation recording
error DonationAlreadyExists(bytes32 donationId);
error EmptyDonationId();
error CampaignDoesNotExist(bytes32 campaignId);
error CampaignNotActive(bytes32 campaignId, CampaignStatus actualStatus);
error CampaignNotInWindow(uint32 currentTime, uint32 startDate, uint32 endDate);
error InvalidAmount();
error EmptyReceiptHash();
error EmptyTxRef();
error InvalidDonor();

// Status transitions
error InvalidStatusTransition(CampaignStatus from, CampaignStatus to);
error CampaignAlreadyTerminal(bytes32 campaignId);

// Milestones
error InvalidMilestoneIndex(uint8 index, uint8 milestoneCount);
error EmptyProofHash();
```

**Convention** : tous les paramètres pertinents pour le debug sont passés aux errors. Le frontend peut décoder l'erreur via ABI et afficher un message i18n approprié.

---

## 6. Events

```solidity
// Associations (v1.2 — trois events distincts pour audit clair)
event AssociationVerified(
    address indexed association,
    bytes32 indexed sirenHash,
    uint32  verifiedAt,
    address indexed by
);

event AssociationRevoked(
    address indexed association,
    bytes32 indexed sirenHash,
    uint32  revokedAt,
    address indexed by
);

event AssociationRestored(
    address indexed association,
    bytes32 indexed sirenHash,
    uint32  restoredAt,
    address indexed by
);

// Campagnes
event CampaignCreated(
    address indexed association,
    bytes32 indexed campaignId,
    uint96  goal,
    uint32  startDate,
    uint32  endDate,
    uint8   milestoneCount,
    bytes32 budgetHash,
    address indexed attestedBy
);

event CampaignStatusChanged(
    bytes32 indexed campaignId,
    CampaignStatus indexed oldStatus,
    CampaignStatus indexed newStatus,
    address by
);

// Dons
event DonationRecorded(
    address indexed donor,
    bytes32 indexed campaignId,
    address indexed association,
    bytes32 donationId,
    uint96  amount,
    bytes32 receiptHash,
    bytes32 txRef
);

event DonorFirstDonation(
    address indexed donor,
    uint32  timestamp
);

event DonorJoinedCampaign(
    address indexed donor,
    bytes32 indexed campaignId
);

// Milestones
event MilestoneReached(
    bytes32 indexed campaignId,
    uint8   indexed milestoneIndex,
    bytes32 proofHash,
    uint32  timestamp
);
```

**Changement v1.2** :
- Trois events distincts pour le cycle de vie de l'association (`AssociationVerified`, `AssociationRevoked`, `AssociationRestored`) au lieu de l'unique `AssociationVerified(bool)` v1.1. Ponder peut ainsi distinguer trivialement les trois opérations sans inspecter le payload.
- `sirenHash` est indexé dans les trois events, permettant à Ponder de reconstituer l'historique complet d'une asso identifiée par son SIREN, même en cas de changement d'adresse (cas hypothétique de Phase 2).

**Notes sur l'indexation** :
- 3 `indexed` max par event (limite EVM). Utilisés pour les filtres Ponder.
- `DonationRecorded` indexe `donor`, `campaignId`, `association` — couvre tous les patterns de requête frontend (dons par donateur, par campagne, par asso).
- `attestedBy` dans `CampaignCreated` = identité du signataire CommonLink, utile pour audit.

---

## 7. Spec des fonctions externes

### 7.1 Administration des associations (v1.2 — refondue)

#### `verifyAssociation(address association, bytes32 sirenHash)`

```solidity
function verifyAssociation(address association, bytes32 sirenHash)
    external
    onlyRole(CURATOR_ROLE);
```

- **Préconditions** :
    - `association != address(0)` → sinon `InvalidAssociation`.
    - `sirenHash != bytes32(0)` → sinon `EmptySirenHash`.
    - `associations[association].verified == false` → sinon `AssociationAlreadyVerified`. (Évite la double-vérification accidentelle ; pour réactiver une asso révoquée, utiliser `restoreAssociation`.)
    - `associations[association].sirenHash == bytes32(0) || associations[association].sirenHash == sirenHash` (cohérence : on ne peut pas changer le sirenHash d'une asso une fois fixé).
    - `sirenHashToAssociation[sirenHash] == address(0) || sirenHashToAssociation[sirenHash] == association` → sinon `SirenAlreadyRegistered`. (Empêche deux adresses différentes de revendiquer le même SIREN.)
- **Effets** :
    - `associations[association] = Association(true, sirenHash, uint32(block.timestamp), 0)`.
    - `sirenHashToAssociation[sirenHash] = association` (si pas déjà fait).
- **Event** : `AssociationVerified(association, sirenHash, uint32(block.timestamp), msg.sender)`.

**Note importante** : cette fonction est utilisée **uniquement pour une première vérification**. Si une asso a déjà été vérifiée puis révoquée, utiliser `restoreAssociation`. Cela rend les transitions explicites côté audit.

---

#### `revokeAssociation(address association)`

```solidity
function revokeAssociation(address association)
    external
    onlyRole(CURATOR_ROLE);
```

- **Préconditions** :
    - `association != address(0)` → sinon `InvalidAssociation`.
    - `associations[association].verifiedAt != 0` → sinon `AssociationNeverVerified`. (On ne peut pas révoquer une asso qui n'a jamais été vérifiée.)
    - `associations[association].verified == true` → sinon `AssociationNotVerified`. (On ne peut pas révoquer une asso déjà révoquée.)
- **Effets** :
    - `associations[association].verified = false`.
    - `associations[association].revokedAt = uint32(block.timestamp)`.
- **Event** : `AssociationRevoked(association, sirenHash, uint32(block.timestamp), msg.sender)`.

**Conséquence runtime** : `recordDonation` vérifie `associations[campaign.association].verified` et revert si l'asso est révoquée. Les campagnes existantes ne sont **pas** automatiquement passées en `Cancelled` ; le backend doit traiter cette transition métier hors-chain (notifications, blocage UI, etc.).

---

#### `restoreAssociation(address association)`

```solidity
function restoreAssociation(address association)
    external
    onlyRole(CURATOR_ROLE);
```

- **Préconditions** :
    - `association != address(0)` → sinon `InvalidAssociation`.
    - `associations[association].verifiedAt != 0` → sinon `AssociationNeverVerified`.
    - `associations[association].verified == false` → sinon `AssociationNotRevoked`. (On ne peut restaurer qu'une asso actuellement révoquée.)
- **Effets** :
    - `associations[association].verified = true`.
    - `associations[association].verifiedAt = uint32(block.timestamp)` (date de la restauration).
    - `associations[association].revokedAt` n'est **pas** réinitialisé — il garde la trace de la dernière révocation pour audit.
- **Event** : `AssociationRestored(association, sirenHash, uint32(block.timestamp), msg.sender)`.

---

### 7.2 Création de campagne

#### `createCampaign(...)`

```solidity
function createCampaign(
    bytes32 campaignId,         // UUID Postgres encodé (cf. §2.5)
    address association,
    uint96  goal,
    uint32  startDate,
    uint32  endDate,
    uint8   milestoneCount,
    bytes32 budgetHash
)
    external
    whenNotPaused
    onlyRole(RECORDER_ROLE);
```

- **Préconditions** :
    - `campaignId != bytes32(0)` → sinon `EmptyCampaignId`.
    - `associations[association].verified == true` → sinon `AssociationNotVerified`.
    - `campaigns[campaignId].association == address(0)` → sinon `CampaignAlreadyExists`.
    - `goal > 0` → sinon `InvalidGoal`.
    - `endDate > startDate` → sinon `InvalidDateRange`.
    - `endDate > block.timestamp` → sinon `InvalidDateRange`.
    - `budgetHash != bytes32(0)` → sinon `EmptyBudgetHash`.
    - Aucune validation sur `milestoneCount` (peut être 0).
- **Effets** :
    - `campaigns[campaignId] = Campaign(association, goal, 0, startDate, endDate, Active, milestoneCount, budgetHash)`.
- **Event** : `CampaignCreated(association, campaignId, goal, startDate, endDate, milestoneCount, budgetHash, msg.sender)`.

**Changement v1.2** : la vérification de `verified` se fait via `associations[association].verified` (champ du struct) au lieu de `verifiedAssociations[association]` (mapping bool de v1.1).

---

### 7.3 Enregistrement de don

#### `recordDonation(...)`

```solidity
function recordDonation(
    bytes32 donationId,         // UUID Postgres encodé (cf. §2.5)
    address donor,
    bytes32 campaignId,
    uint96  amount,
    bytes32 receiptHash,
    bytes32 txRef
)
    external
    whenNotPaused
    onlyRole(RECORDER_ROLE);
```

- **Préconditions** :
    - `donationId != bytes32(0)` → sinon `EmptyDonationId`.
    - `donations[donationId].donor == address(0)` → sinon `DonationAlreadyExists`.
    - `donor != address(0)` → sinon `InvalidDonor`.
    - `campaigns[campaignId].association != address(0)` → sinon `CampaignDoesNotExist`.
    - **(v1.2)** `associations[campaigns[campaignId].association].verified == true` → sinon `AssociationNotVerified`. **Check runtime du soft revoke.**
    - `campaigns[campaignId].status == Active` → sinon `CampaignNotActive`.
    - `block.timestamp >= startDate && block.timestamp <= endDate` → sinon `CampaignNotInWindow`.
    - `amount > 0` → sinon `InvalidAmount`.
    - `receiptHash != bytes32(0)` → sinon `EmptyReceiptHash`.
    - `txRef != bytes32(0)` → sinon `EmptyTxRef`.
- **Effets** :
    1. `donations[donationId] = Donation(donor, amount, campaignId, uint32(block.timestamp), receiptHash, txRef)`.
    2. `campaigns[campaignId].raised += amount`.
    3. **Mise à jour `donorStats[donor]`** :
        - Si `firstDonationAt == 0` : positionner à `block.timestamp` + émettre `DonorFirstDonation`.
        - `totalDonated += amount`.
        - `donationCount += 1`.
        - Si `donorCampaignTotal[donor][campaignId] == 0` (premier don sur cette campagne) :
            - `uniqueCampaigns += 1`.
            - Émettre `DonorJoinedCampaign(donor, campaignId)`.
    4. `donorCampaignTotal[donor][campaignId] += amount`.
- **Event** : `DonationRecorded(donor, campaignId, association, donationId, amount, receiptHash, txRef)`.
- **Retour** : aucun (le `donationId` est connu de l'appelant).

**Changement v1.2** : ajout du check `associations[...].verified` (soft revoke runtime). Si une asso est révoquée entre la création de la campagne et un don, ce don sera rejeté. Le backend doit gérer ce cas (notification, statut campagne, etc.).

**Note de sécurité** : pas d'overflow possible car `uint96` × ~10⁹ dons max + Solidity 0.8 checks par défaut. `uint128 totalDonated` est volontairement plus large que `uint96 amount` pour absorber les cumuls sans risque.

---

### 7.4 Transitions de statut

```solidity
function pauseCampaign(bytes32 campaignId)    external onlyRole(CURATOR_ROLE);
function unpauseCampaign(bytes32 campaignId)  external onlyRole(CURATOR_ROLE);
function cancelCampaign(bytes32 campaignId)   external onlyRole(CURATOR_ROLE);
function completeCampaign(bytes32 campaignId) external onlyRole(CURATOR_ROLE);
```

- **Préconditions communes** :
    - `campaigns[campaignId].association != address(0)` → sinon `CampaignDoesNotExist`.
- **Transitions** (validation stricte) :
    - `pauseCampaign` : `Active` → `Paused`. Sinon `InvalidStatusTransition`.
    - `unpauseCampaign` : `Paused` → `Active`. Sinon `InvalidStatusTransition`.
    - `cancelCampaign` : `Active` ou `Paused` → `Cancelled`. Sinon `InvalidStatusTransition`. Si déjà `Cancelled` ou `Completed` → `CampaignAlreadyTerminal`.
    - `completeCampaign` : `Active` ou `Paused` → `Completed`. Sinon `InvalidStatusTransition`. Si déjà terminal → `CampaignAlreadyTerminal`.
- **Effets** : update `status`.
- **Event** : `CampaignStatusChanged(campaignId, oldStatus, newStatus, msg.sender)`.

---

### 7.5 Milestones

#### `markMilestoneReached(bytes32 campaignId, uint8 index, bytes32 proofHash)`

```solidity
function markMilestoneReached(
    bytes32 campaignId,
    uint8   index,
    bytes32 proofHash
)
    external
    whenNotPaused
    onlyRole(RECORDER_ROLE);
```

- **Préconditions** :
    - Campagne existe et `status == Active`.
    - `index < campaigns[campaignId].milestoneCount` → sinon `InvalidMilestoneIndex`.
    - `proofHash != bytes32(0)` → sinon `EmptyProofHash`.
- **Effets** : event-only en MVP. Pas de storage du fait d'atteinte.
- **Event** : `MilestoneReached(campaignId, index, proofHash, uint32(block.timestamp))`.

**Note importante** : option B retenue = "compteur + event". Pas de storage des atteintes on-chain. L'historique des atteintes est entièrement dans les events (indexés par Ponder).

**Conséquence** : la protection "MilestoneAlreadyReached" ne peut pas se faire on-chain sans stockage. Le backend doit vérifier en DB que le milestone n'a pas déjà été marqué avant d'appeler la fonction. En P1.5, ajout possible d'un `mapping(bytes32 => uint256) reachedMilestonesBitmap` (1 bit par index, jusqu'à 256 milestones par campagne, packé sur 1 slot) sans breaking change.

---

### 7.6 Pause d'urgence globale

```solidity
function pause()   external onlyRole(DEFAULT_ADMIN_ROLE);
function unpause() external onlyRole(DEFAULT_ADMIN_ROLE);
```

Standard OpenZeppelin `Pausable`. Bloque `createCampaign`, `recordDonation`, `markMilestoneReached`. Les transitions de statut campagne et le cycle de vie des associations restent disponibles pour permettre le triage manuel pendant un incident.

---

### 7.7 Lectures (views directes)

Les mappings publics génèrent leurs getters automatiquement. On ajoute toutefois cinq convenience getters qui retournent les structs entiers :

```solidity
function getAssociation(address association)    external view returns (Association memory);
function getCampaign(bytes32 campaignId)        external view returns (Campaign memory);
function getDonation(bytes32 donationId)        external view returns (Donation memory);
function getDonorStats(address donor)           external view returns (DonorStats memory);
function getDonorCampaignTotal(address donor, bytes32 campaignId)
    external view returns (uint128);
```

**Utilité** : retourner les structs entiers en une seule lecture (vs N lectures pour chaque champ via le getter auto). Idéal pour les vues côté frontend / Ponder qui font des fetch ponctuels.

---

### 7.8 Accès en lecture pour le frontend — via Ponder

Les vues "liste" (tous les dons d'un donateur, tous les dons d'une campagne, toutes les campagnes d'une asso, historique de vérification d'une asso) **ne sont PAS exposées par le contrat**. Le contrat n'a aucune fonction `getAllDonationsForDonor(address)` ou équivalent.

**Pourquoi** :
- Les listes non bornées en storage sont un anti-pattern Solidity classique (peuvent dépasser le gas limit en lecture).
- Stocker ces listes coûterait ~40 000 gas additionnels par don (push dans un tableau dynamique).
- Les events `DonationRecorded` sont **indexés** sur `donor`, `campaignId` et `association` — ils permettent à n'importe quel client de reconstituer ces listes en filtrant les events depuis le bloc de déploiement.
- Les events `AssociationVerified`, `AssociationRevoked`, `AssociationRestored` indexent `sirenHash` — Ponder reconstitue l'historique complet de chaque asso.

**L'architecture est donc** :

```
Frontend  ──GraphQL──►  Ponder  ──read events──►  Polygon
                          │
                          └──► Postgres Ponder (indexé)
```

**Exemple de requête GraphQL Ponder** :

```graphql
query DonorHistory($donor: String!, $first: Int = 20, $skip: Int = 0) {
  donations(
    where: { donor: $donor },
    orderBy: timestamp,
    orderDirection: desc,
    first: $first,
    skip: $skip
  ) {
    id              # bytes32 = UUID donation
    donor
    campaignId
    amount
    timestamp
    receiptHash
    txRef
    campaign {
      association
      goal
      raised
    }
  }
}
```

**Garantie d'intégrité** : si Ponder venait à être indisponible, n'importe quel client peut rejouer les events on-chain pour reconstituer les listes. La promesse "trace, ne transmet pas" est tenue **par les events**, pas par le storage.

**Côté backend Spring** : la table `donation` en Postgres reste la source de vérité métier (avec le payment_intent Stripe, le PDF reçu, etc.). Ponder est un cache indexé secondaire qui sert les vues frontend. Le backend Spring ne doit **jamais** appeler Ponder — il a sa propre DB.

---

## 8. Invariants à fuzzer (Foundry)

Les invariants suivants doivent être vrais **après n'importe quelle séquence d'appels publics**. À écrire dans `test/invariant/CommonLinkRegistry.invariants.t.sol`.

### 8.1 Cohérence des cumuls donateur

- **INV-1** : `donorStats[d].totalDonated == Σ(donations[id].amount where donations[id].donor == d)` pour tout `d`.
- **INV-2** : `donorStats[d].donationCount == count(donations where donor == d)` pour tout `d`.
- **INV-3** : `donorStats[d].uniqueCampaigns == count(distinct campaignId in donations where donor == d)` pour tout `d`.
- **INV-4** : `donorCampaignTotal[d][c] == Σ(donations[id].amount where donor == d AND campaignId == c)` pour tout `(d, c)`.

### 8.2 Cohérence des cumuls campagne

- **INV-5** : `campaigns[c].raised == Σ(donations[id].amount where donations[id].campaignId == c)` pour tout `c`.
- **INV-6** : `campaigns[c].raised == Σ(donorCampaignTotal[d][c] for all d)` pour tout `c`.

### 8.3 Immutabilité

- **INV-7** : `campaigns[c].budgetHash` ne change jamais après création.
- **INV-8** : `campaigns[c].association` ne change jamais après création.
- **INV-9** : `campaigns[c].goal`, `startDate`, `endDate`, `milestoneCount` ne changent jamais.
- **INV-10** : `donorStats[d].firstDonationAt` ne change jamais une fois positionné (> 0).
- **INV-11** : `donations[id]` est immuable une fois créé.
- **INV-12 (v1.2)** : `associations[a].sirenHash` ne change jamais une fois positionné (> 0).
- **INV-13 (v1.2)** : Pour tout don `id`, le montant ne peut décroître ; `donations[id]` est write-once strict (cf. INV-11). Aucune fonction n'existe pour décrémenter `totalDonated`, `donationCount`, `raised`, ou `donorCampaignTotal`. **Conséquence directe de la décision "refunds impossibles".**

### 8.4 Garde-fous d'état

- **INV-14** : Aucune `recordDonation` n'a pu être enregistrée sur une campagne dont le statut au moment de l'appel n'était pas `Active`.
- **INV-15** : Aucune `recordDonation` n'a pu être enregistrée hors de la fenêtre temporelle `[startDate, endDate]`.
- **INV-16** : Unicité — pour tout `donationId` déjà utilisé, un second appel `recordDonation` avec le même ID revert toujours.
- **INV-17** : Unicité — pour tout `campaignId` déjà utilisé, un second appel `createCampaign` avec le même ID revert toujours.
- **INV-18** : Une campagne en état terminal (`Completed` ou `Cancelled`) ne peut plus changer de statut.
- **INV-19 (v1.2)** : Aucune `recordDonation` n'a pu être enregistrée si `associations[campaign.association].verified == false` au moment de l'appel.
- **INV-20 (v1.2)** : Unicité SIREN — pour tout `sirenHash`, il existe au plus une seule adresse `a` telle que `associations[a].sirenHash == sirenHash` et `associations[a].verifiedAt > 0`.
- **INV-21 (v1.2)** : Cohérence index inversé — `sirenHashToAssociation[associations[a].sirenHash] == a` pour toute asso `a` ayant été vérifiée au moins une fois (`verifiedAt > 0`).

### 8.5 Sécurité d'accès

- **INV-22** : Toute mutation a été précédée d'un check de rôle valide.
- **INV-23** : Aucune écriture en storage n'a eu lieu pendant que le contrat était globalement en pause (sauf transitions de statut campagne et cycle de vie asso, qui sont des outils de curation).

**Changement v1.2** : ajout de INV-12, INV-13, INV-19, INV-20, INV-21. Renumérotation des invariants 14-23.

---

## 9. Cas hors périmètre (MVP) — à ne PAS implémenter

À noter explicitement pour éviter le scope creep :

- ❌ Stockage on-chain des descriptions de milestones (juste compteur + events).
- ❌ Stockage on-chain de l'historique des atteintes de milestones.
- ❌ Smart contract `Campaign.sol` séparé par campagne (mapping suffit).
- ❌ Cascade automatique sur les campagnes d'une asso révoquée (soft revoke runtime via check `verified` dans `recordDonation` — la gestion d'état des campagnes existantes est métier backend).
- ❌ Modification du `budgetHash` (immuable par design).
- ❌ Modification du `sirenHash` d'une asso après première vérification (immuable par design, cf. INV-12).
- ❌ **Refund / annulation de don individuel — impossible structurellement.** Les refunds fiat sont tracés en Postgres uniquement (cf. §1 principe d'immuabilité). Aucune fonction `voidDonation`, `refundDonation`, etc. n'existe ni n'existera dans la même major version.
- ❌ Transfert de fonds par le contrat (jamais).
- ❌ Upgradeabilité (proxy, UUPS).
- ❌ Pause par campagne automatique sur seuils (anti-fraude).
- ❌ Quadratic funding / matching pools.
- ❌ Badge minting (séparé dans `DonorBadge.sol`).
- ❌ **Listes on-chain** (`donations[]`, `campaigns[]` par asso, etc.) — passe par Ponder.
- ❌ **Tracking on-chain des dépenses publiées par les assos** (`ExpenseProofRecorded`) — déplacé en **Phase 1.5**. Le MVP se concentre sur la traçabilité des dons.

---

## 10. Hors-contrat — engagements côté backend

Le contrat ne peut pas garantir ces propriétés à lui seul. Le backend doit :

- **Générer les UUIDs côté Postgres** (PRIMARY KEY UUID v4) avant tout appel on-chain.
- **Utiliser la classe unique `IdEncoder`** (cf. §2.5) pour la conversion UUID ↔ bytes32. Tests unitaires obligatoires.
- **Utiliser la classe unique `SirenHasher`** (cf. §2.6) pour le calcul du sirenHash. Validation du format à 9 chiffres avant hash. Tests unitaires obligatoires.
- **Canonicaliser le JSON budget via JCS (RFC 8785)** avant calcul du `keccak256`. Une seule classe `BudgetHasher`.
- **Stocker le JSON canonique en DB et sur IPFS** avant d'appeler `createCampaign`.
- **Garantir `concurrency=1` sur le worker** RabbitMQ (gestion du nonce).
- **Vérifier en DB** qu'un milestone n'a pas déjà été marqué avant d'appeler `markMilestoneReached`.
- **Idempotence webhook** : check `txRef` (Stripe payment_intent) en DB **et** unicité du `donationId` UUID avant publication AMQ.
- **Hash receipt** : keccak256 du PDF en bytes bruts. PDF uploadé sur IPFS pour archivage.
- **Ne jamais réutiliser un UUID** : même en cas de retry, le backend doit générer un nouvel UUID seulement si on est sûr que l'ancien n'a jamais été soumis on-chain.
- **Gestion métier d'une révocation d'asso** : quand `revokeAssociation` est appelée, le backend doit :
    1. Suspendre l'UI de don pour toutes les campagnes de l'asso.
    2. Notifier l'asso et les donateurs concernés.
    3. Décider du sort des campagnes en cours (passage en `Paused` ou `Cancelled` selon la gravité).
    4. Bloquer la consommation de webhooks Stripe pour ces campagnes.
       Le contrat ne fait que rejeter les nouveaux `recordDonation` (INV-19) ; toute la logique de communication métier est hors-chain.
- **Refunds fiat (cas exceptionnel)** : si un don doit être remboursé côté Stripe, le backend doit :
    1. Conserver le `DonationEvent` original avec son enregistrement on-chain inchangé.
    2. Créer un événement compensatoire `DonationRefund` en Postgres lié au don original par `donationId`.
    3. Mettre à jour l'affichage donateur pour montrer "Don remboursé le …" sans toucher au registre.
    4. Émettre un nouveau reçu fiscal rectificatif si pertinent (article 200 CGI).
       La blockchain reste la trace de ce qui s'est passé ; le remboursement est un événement métier ultérieur, non un effacement.

---

## 11. Plan de déploiement

### Phase MVP (Polygon Amoy)

1. Déploiement `CommonLinkRegistry` par le wallet de gouvernance.
2. `grantRole(RECORDER_ROLE, hotWalletBackend)`.
3. `grantRole(CURATOR_ROLE, hotWalletBackend)` (même wallet en MVP).
4. Renoncement à `DEFAULT_ADMIN_ROLE` par le déployeur si gouvernance multisig prête, sinon différé en P1.5.

### Phase P1.5 (Polygon mainnet)

1. Audit externe du contrat (CertiK, OpenZeppelin, Trail of Bits — à arbitrer).
2. Multisig Safe CommonLink pour `DEFAULT_ADMIN_ROLE` (2/3 minimum).
3. Séparation `RECORDER_ROLE` et `CURATOR_ROLE` (wallets distincts).
4. Backfill de l'état Amoy vers mainnet via events Ponder.
5. Ajout du tracking des dépenses (`ExpenseProofRecorded`) — extension non-breaking.

---

## 12. Versioning de la spec

Toute modification de cette spec après acceptation suit le processus suivant :

- **Patch (1.x.y)** : correction de typo, clarification — pas d'impact code.
- **Minor (1.y.0)** : ajout de fonctionnalité non-breaking (nouvelle fonction view, nouvel event, nouveau mapping additionnel).
- **Major (2.0.0)** : breaking change. Implique nouveau déploiement de contrat.

Le numéro de version est dans le header du document et dans une constante `string public constant VERSION` du contrat.

**Note v1.2** : cette version est techniquement *breaking* par rapport à v1.1 (remplacement du mapping `verifiedAssociations` par `associations`, changement de signature de `verifyAssociation`, nouveaux events). En toute rigueur elle aurait pu être v2.0. La désignation 1.2 est conservée car aucune v1.x n'a été déployée en production — la v1.2 est donc la première version réellement implémentée.

---

## Annexe A — Tableau récapitulatif des coûts gas estimés

| Fonction | Gas approx. | Coût Polygon (30 gwei, MATIC à 0.50€) |
|---|---|---|
| `verifyAssociation` (nouveau) | ~75 000 | ~0.0011 € |
| `revokeAssociation` | ~10 000 | ~0.00015 € |
| `restoreAssociation` | ~10 000 | ~0.00015 € |
| `createCampaign` | ~120 000 | ~0.0018 € |
| `recordDonation` (donateur nouveau) | ~185 000 | ~0.0028 € |
| `recordDonation` (donateur récurrent) | ~95 000 | ~0.0014 € |
| `markMilestoneReached` | ~35 000 | ~0.00053 € |
| `pauseCampaign` / `cancelCampaign` etc. | ~30 000 | ~0.00045 € |

**Budget annuel estimé** pour 100 campagnes + 100 000 dons : ~100-200 €. Provisionnement hot wallet : 50 MATIC/trimestre.

---

## Annexe B — Glossaire

| Terme | Définition |
|---|---|
| **EOA** | Externally Owned Account — wallet contrôlé par une clé privée classique. |
| **Safe** | Multisig Gnosis Safe v1.4+, contrat de portefeuille à plusieurs signataires. |
| **SIREN** | Système d'Identification du Répertoire des Entreprises — 9 chiffres, identifie une entité juridique (association comprise). |
| **SIRET** | SIREN + 5 chiffres (NIC) — identifie un établissement particulier. CommonLink utilise le SIREN (entité), pas le SIRET (établissement). |
| **JCS** | JSON Canonicalization Scheme — RFC 8785, sérialisation déterministe d'un JSON. |
| **UUID** | Universally Unique Identifier (RFC 4122) — 128 bits, encodé en `bytes32` avec 16 bytes de padding zéro pour le on-chain. |
| **Soulbound** | Token (NFT) non-transférable, attaché définitivement à une adresse. |
| **Ponder** | Indexeur blockchain TypeScript, écoute les events et expose une API GraphQL. |
| **AccessControl** | Pattern OpenZeppelin de gestion de rôles. |
| **Pausable** | Pattern OpenZeppelin permettant de geler les fonctions sensibles. |
| **SSTORE / SLOAD** | Opcodes EVM d'écriture / lecture en storage. SSTORE est cher (20k/5k gas), SLOAD est ~100 gas chaud. |
| **Soft revoke** | Dévérification d'une asso qui ne modifie pas les états passés (campagnes, dons) mais bloque les nouveaux dons via check runtime. |