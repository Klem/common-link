# Inscription des dons et des dépenses au registre public on-chain

**Fiche de contrôle — hors dispositif LCB-FT (voir avertissement au point 1)**

| | |
|---|---|
| **Entité** | CommonLink |
| **Nature du document** | Fiche décrivant l'état réel d'un mécanisme mentionné au donateur, destinée à la commission juridique |
| **Rattachement** | Aucun — ce mécanisme concerne la certification on-chain de l'utilisation des fonds (dons et dépenses d'une campagne), donnée affichée au donateur ; il ne relève d'aucune des six responsabilités du dispositif LCB-FT et n'est pas une mesure de vigilance client. Voir `LCB-FT-compliance-overview.md`, section 5.3. |
| **Date de rédaction** | 29 septembre 2026 |
| **Dernière mise à jour** | 26 octobre 2026 (registre v3.0 : arrêt d'urgence par une clé dédiée, administration transférable en deux temps et non abandonnable, contrat incapable de recevoir des fonds, plus aucun lien vers le contrat de badges) |
| **État** | **Code** : dons **et** dépenses sont câblés vers le contrat (fonctions `recordDonation` et `recordPayout`). **Déploiement** : **aucun contrat n'est déployé sur une chaîne publique** — staging et production tournent en mode simulé (`onchain.mock: true`) : les inscriptions y produisent un reçu fictif et **rien n'est écrit sur une chaîne**. Le poste de développement local écrit, par défaut, sur un nœud Anvil **privé et jetable** (chaîne 31337, jamais publique). |
| **Rédacteur** | Équipe technique CommonLink |

---

## 1. Avertissement sur la portée de ce document

Ce document décrit un seul mécanisme : l'inscription, sur le registre tenu par le contrat
CommonLink, des dons et des dépenses d'une campagne. Il a été produit parce que le dashboard donateur
introduit, **côté donateur**, des écrans qui parlent de ce registre (bandeau de la page « bilan de
campagne », feed d'engagement, étape « inscrit » du parcours d'un don).

Ce document ne prétend établir la conformité d'aucune des six responsabilités du dispositif
LCB-FT. Il est **sans rapport** avec la fiche `E3-controle-registres-publics.md`, qui porte sur un
sujet différent : la consultation des registres publics **associatifs** (RNA, Sirene, Infogreffe)
pour vérifier l'existence juridique d'une association candidate — pas le registre **on-chain**
propre à CommonLink dont il est question ici.

## 2. Ce que le contrat prouve — et ce qu'il ne prouve pas

Le contrat de registre (v3) est un **notaire** : il enregistre des faits que CommonLink (le backend)
a **déjà décidés et confirmés**, et il ne rejoue **aucune règle métier**. Il ne vérifie ni le statut
d'une campagne, ni l'éligibilité d'une association, ni la confirmation d'un paiement, ni le solde
disponible, ni l'éligibilité d'un décaissement : ces décisions sont prises par le backend avant
l'enregistrement. Il n'a **aucun cycle de vie** : une association est inscrite une seule fois (quand
elle devient apte à collecter), une campagne une seule fois (à sa première mise en ligne, avec le hash
de son budget) ; aucune n'est jamais retirée, suspendue ni réactivée sur la chaîne — rendre une entité
inaccessible en écriture est une décision du backend. Le contrat ne garde que ses propres garde-fous
d'intégrité : droits d'écriture réservés au compte d'écriture CommonLink, arrêt d'urgence, refus
d'enregistrer deux fois le même identifiant, existence de la campagne et de l'association
référencées, valeurs non vides. Il n'enregistre **ni remboursement ni répartition des dons entre les
dépenses** : un don n'est jamais remboursé, et la répartition (premier entré, premier sorti) est
calculée par le backend uniquement.

Ce que l'inscription établit, une fois le contrat réellement déployé : « CommonLink a déclaré tel
fait à telle date et ne peut plus le réécrire ». Elle **n'établit pas** que la règle métier
correspondante était satisfaite. Aucun texte, écran ou document ne doit dire que « la chaîne a
validé » ou « garantit » un contrôle.

## 3. L'état réel du mécanisme

**Les dons** — `OnchainJobAction.RECORD_DONATION` est traité par `OnchainJobWorker`, qui appelle
`OnchainRegistry.recordDonation(...)` ; l'implémentation réelle,
`OnchainRegistryClient.recordDonation` (`api/src/main/kotlin/org/commonlink/onchain/
OnchainRegistryClient.kt`), est câblée au contrat.

**Les dépenses** — à la confirmation bancaire d'un décaissement, `PayoutConfirmer.finaliseSettled`
(`service/PayoutConfirmer.kt:210`) fait passer le `Payout` à `CONFIRMED` et enfile, dans la **même
transaction**, un job `OnchainJobAction.RECORD_PAYOUT` (idempotent — un rejeu de webhook Bridge ne
recrée pas le job). `OnchainJobWorker` le dispatche vers `OnchainRegistryClient.recordPayout`
(`onchain/OnchainRegistryClient.kt`), désormais **câblé** à la fonction `recordPayout` du
contrat (enregistrement du décaissement, montant en centimes d'euro ; aucun fonds n'est déplacé). Le
stub qui échouait systématiquement a été retiré.

*Complément du 23 octobre 2026 (registre v2.1).* Pour chaque décaissement, le registre conserve une
**fiche** consultable (`getPayout`) : campagne, montant en centimes, date, et **référence du virement**
— l'identifiant de transaction Bridge (32 caractères), à défaut l'identifiant du lien de paiement Bridge,
en dernier recours l'identifiant du décaissement. Cette référence devient **publique** sur la chaîne :
elle n'est pas une donnée personnelle, mais elle permet, à qui détient le relevé Bridge, de relier
l'inscription au virement. Le registre tient aussi des **statistiques par campagne** (`getCampaignStats`) :
total et nombre des décaissements, nombre de dons, nombre de donateurs distincts (adresses
pseudonymes), dates du dernier don et du dernier décaissement. Il ne refuse **aucun** décaissement
supérieur au total collecté inscrit : ce contrôle reste celui du backend, et le total inscrit ignore les
dons antérieurs au passage sur une chaîne réelle.

*Complément du 26 octobre 2026 (registre v3.0, déployé sur aucune chaîne publique).* Le contrat a été
durci sans changer ce qu'il enregistre :
- **arrêt d'urgence** : il est déclenché par une clé dédiée, distincte du compte d'écriture ; seul
  l'administrateur peut le lever. *(Complété le 26 octobre 2026.)* Le responsable de la conformité peut le
  déclencher par l'interface de programmation de son espace (motif obligatoire, chaque demande inscrite au
  journal de conformité, y compris une demande qui échoue) ; **aucun écran** ne le propose encore, et la plateforme
  n'expose aucun moyen de lever l'arrêt. En mode simulé — staging et production aujourd'hui — la demande est
  refusée : il n'y a aucun registre à geler. Vérifié par contrôles automatisés, **pas encore sur un nœud réel** ;
  *(Complété le 27 octobre 2026, registre v3.1, déployé sur aucune chaîne publique.)* L'arrêt a désormais un
  **écran** (section « Blockchain » de l'espace conformité). La phrase « seul l'administrateur peut le lever »
  n'est plus exacte : le responsable de la conformité peut aussi **réactiver** le registre depuis cet écran
  (motif obligatoire, chaque demande journalisée), grâce à un droit de reprise accordé à la clé du serveur.
  Ce droit est **révocable par l'administrateur sans redéploiement** ; tant qu'il est ouvert, une clé
  serveur compromise pourrait lever un gel (risque assumé jusqu'au portefeuille multisignature). Gel et
  réactivation ont été exercés le 27 octobre 2026 par l'interface de programmation sur des nœuds locaux
  (registre v3.0 pour le gel, v3.1 jetable pour la réactivation et le retrait du droit) ; l'écran n'a pas
  été vérifié dans un navigateur, et rien n'a été exercé sur une chaîne publique ;
- **administration** : l'administrateur (destiné à être un portefeuille multisignature) ne peut changer
  qu'en deux temps, après un délai public (1 heure sur le réseau de test, 3 jours prévus en production),
  et ne peut **jamais** être abandonné ;
- **aucun fonds** : le contrat refuse tout envoi de monnaie ou de jeton qu'il peut refuser ; un envoi forcé
  (cas techniques que le code ne peut pas empêcher) resterait bloqué définitivement sans altérer aucune
  inscription ;
- le contrat n'appelle plus aucun autre contrat (les badges seront un contrat séparé, non encore écrit).

**Mais rien n'est déployé sur une chaîne publique.** `onchain.mock: true` en staging et en production
(`application-staging.yml`, `application-prod.yml`) ; en local, le mode non simulé vise un nœud Anvil
privé et jetable (`application.yml`, `${ONCHAIN_MOCK:false}`), jamais une chaîne publique. En mode simulé, le worker est actif
(`onchain.worker.enabled: true`) et traite bien les jobs, mais contre `MockOnchainRegistry`, qui
renvoie un **reçu fictif** (hash de transaction aléatoire). Un job `RECORD_DONATION` ou
`RECORD_PAYOUT` passe donc à `DONE` **sans qu'aucune écriture n'ait eu lieu sur une chaîne
publique**. Un garde-fou existant (`OnchainMockGuard`) tolère explicitement ce mode en production
jusqu'au déploiement (`TODO(onchain)` dans le code).

**Conséquences à ne pas minimiser** :
- **Aucun don et aucune dépense n'est aujourd'hui inscrit sur un registre public**, en production
  comme ailleurs. Côté donateur, l'étape « inscrit » du parcours d'un don lit le job d'enregistrement
  (qui est simulé) ; côté association, la puce « ✓ on-chain » de la liste des donateurs ne reflète que
  la confirmation du paiement (`confirmedAt`), pas une écriture sur chaîne.
- Le décaissement bancaire et le passage à `CONFIRMED` sont **indépendants** de l'issue du job
  on-chain (un job en échec n'affecte ni le virement ni l'état du paiement).

**Ordre d'inscription (câblé dans le code le 20 octobre 2026)** : une association est inscrite quand
elle devient apte à collecter (Mollie COMPLETED) ; une campagne à sa première mise en ligne, avec le
hash de son budget ; chaque don, décaissement ou mise à jour de budget n'est envoyé qu'une fois
l'inscription de sa campagne enregistrée (dépendance entre jobs). Une mise à jour de budget n'est
envoyée que si le hash du budget diffère du dernier hash déclaré pour la campagne (une republication à
budget inchangé n'envoie rien), et chaque mise à jour attend la précédente, de sorte que le dernier
budget déclaré est toujours celui inscrit en dernier (complément du 22 octobre 2026). Avant d'envoyer une
inscription, le service relit le registre : une inscription déjà présente avec les mêmes données est
considérée comme faite, sans nouvelle écriture ; une campagne déjà inscrite avec un autre budget reçoit le
budget attendu par une mise à jour ; une inscription présente avec un autre SIREN ou une autre association
est mise en échec immédiatement, sans nouvel essai (complément du 23 octobre 2026). Aucun rejeu de l'historique n'est
prévu : au passage sur réseau réel, seuls les faits postérieurs seront enregistrés ; les dons et
décaissements antérieurs restent uniquement dans la base CommonLink. Rien de tout cela n'est
déployé : le mode reste simulé.

## 4. Ce que le donateur voit (dashboard donateur)

La page « bilan de campagne » de l'espace donateur affiche un bandeau parlant du registre public. Le
texte a été arbitré (**décision produit D2 du 28/09/2026**) pour ne jamais suggérer une symétrie
inexistante entre dons et dépenses. Il doit être utilisé **mot pour mot**, sur l'écran comme dans
l'export PDF du bilan, sans paraphrase :

> « Les dons sont inscrits dans un registre public. Les dépenses sont tracées et vérifiées ; leur
> inscription au registre public est en cours de déploiement. »

« Tracées et vérifiées » renvoie à la traçabilité applicative (allocation FIFO don → dépense,
confirmation bancaire) — pas à une inscription on-chain.

⚠️ **Écart entre ce texte et l'état réel (à arbitrer par la commission juridique et le produit)** :
tant que `onchain.mock: true` en production (point 3), **la première phrase est inexacte** — les
dons ne sont pas inscrits dans un registre public. De même, les libellés donateur « Inscrit au
registre public » et « Ce don est inscrit sur un registre public infalsifiable. » (`app/messages`)
décrivent un contrôle plus complet que le code déployé. Cette fiche ne modifie pas ces textes : elle
signale que leur exactitude dépend du déploiement réel du contrat (sprints B3 et B8 du chantier
d'intégration on-chain) et qu'ils doivent être revus avant ou avec lui.

### 4.1 Second lecteur : le feed d'engagement donateur

Le feed « Depuis votre dernière visite » (`DonorEngagementService`) affiche un événement à chaque
`Payout` `CONFIRMED` sur une association soutenue par le donateur. Chaque item de ce type utilise,
sous une forme adaptée à une ligne de flux, la même formulation validée D2 — jamais « inscrit(e) au
registre public » pour une dépense :

> « [Association] a confirmé une nouvelle dépense pour [campagne]. Les dépenses sont tracées et
> vérifiées ; leur inscription au registre public est en cours de déploiement. »

Ce texte décrit un décaissement bancaire réel, jamais une inscription on-chain. Le feed compose ce
texte par code — voir `DonorEngagementService.getFeed` : toute évolution de ce code doit repasser par
la formulation exacte ci-dessus.

## 5. Ce que ce document ne couvre pas

- **Le déploiement du contrat** sur un réseau public (Gnosis Chiado puis Gnosis), la vérification de
  son code source sur les explorateurs, la bascule `onchain.mock: false` en production et le rejeu
  de l'historique : chantier distinct (sprints B2, B3 et B8 de `.tasks/blockchain-integration-sprint.md`).
  Cette fiche sera mise à jour — et les libellés du point 4 revus — une fois ce chantier achevé et
  **confirmé par un humain**.
- **L'audit externe du contrat** : prévu avant la mise en production réelle (sprint B8), non réalisé.
- **La conformité du dispositif LCB-FT dans son ensemble** — sans objet ici, ce mécanisme n'en fait
  pas partie (point 1).

**Suivi des travaux restants.**

| Travail restant | Lien de suivi |
|---|---|
| Déploiement du contrat sur Chiado, vérification explorateur, recette staging non simulée | `.tasks/blockchain-integration-sprint.md`, sprint B3 |
| Retrait du droit de reprise de la clé serveur une fois l'administrateur passé en multisignature | `.tasks/blockchain-integration-sprint.md`, décision n°31 révisée, sprint B8 |
| Audit externe (sprint C5), gouvernance multisig, bascule `mock=false` en production, réactivation du garde-fou `OnchainMockGuard` | `.tasks/blockchain-integration-sprint.md`, sprints C5 et B8 |
| Revue des libellés donateur parlant d'inscription au registre public tant que la production est simulée | À arbitrer (commission juridique / produit) — voir point 4 |

## 6. Éléments de traçabilité

- `OnchainRegistryClient.kt` — `recordDonation` et `recordPayout` câblés au contrat (registre v2.1.0 : `recordPayout` porte la référence du virement).
- `MockOnchainRegistry.kt` — implémentations simulées des enregistrements (reçu fictif).
- `PayoutConfirmer.kt:210` (`finaliseSettled`) — enfile `RECORD_PAYOUT` dans la même transaction que le
  passage à `CONFIRMED`, idempotent.
- `OnchainJobWorker.kt` — KDoc du comportement de reprise (cinq tentatives puis `FAILED`) et du principe
  « notaire » : le worker ne modifie jamais un état métier à l'issue d'un job.
- `application.yml`, `application-staging.yml`, `application-prod.yml` — `onchain.mock: true` et
  `onchain.worker.enabled: true`.
- `blockchain/src/CommonLinkRegistry.sol` (v3.0.0-mvp) et `blockchain/README.md` §1.1–§1.3 et §2 — principe
  « notaire, inscrit une fois, sans cycle de vie », aucun fonds, rôles (écriture, arrêt d'urgence, administration).
- `blockchain/test/CommonLinkRegistry.governance.t.sol` — arrêt d'urgence, transfert d'administration, refus des fonds.

---

*Document établi le 29 septembre 2026, à l'occasion de l'introduction du premier écran côté
donateur mentionnant le registre public. Mis à jour le 30 septembre 2026 (sprint 4 : feed
d'engagement, §4.1), puis le 20 octobre 2026 (refonte du contrat, enregistrement des dépenses câblé,
état de déploiement rétabli : tout est simulé), le 23 octobre 2026 (registre v2.1) et le 26 octobre 2026
(registre v3.0) et le 27 octobre 2026 (registre v3.1 : réactivation depuis l'écran conformité). À mettre à jour lors
du déploiement réel du contrat.*
