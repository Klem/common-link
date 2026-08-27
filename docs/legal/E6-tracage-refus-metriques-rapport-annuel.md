# Traçage des décisions d'ouverture de collecte — métriques du rapport annuel ACPR

**Fiche de contrôle — dispositif de lutte contre le blanchiment de capitaux et le financement du terrorisme (LCB-FT)**

| | |
|---|---|
| **Entité** | CommonLink |
| **Nature du document** | Fiche descriptive d'un contrôle mis en place, destinée à la commission juridique |
| **Responsabilité concernée** | Conservation des documents et rapport annuel *(responsabilité n° 6 du dispositif interne, épique E6)* |
| **Référence de suivi interne** | Tâche Asana n° `1217152626184665` (IC-37, sous-tâche du générateur de rapport annuel IFP) |
| **Lien de suivi** | https://app.asana.com/1/1213718564226627/project/1213723193546726/task/1217152626184665 |
| **Base légale** | Art. R.548-4 II du code monétaire et financier, applicable aux intermédiaires en financement participatif ne présentant que des appels aux dons via l'art. R.548-10 II (texte vérifié Légifrance le 27 août 2026 — voir § 1.1) |
| **État** | Corrigé et vérifié par contrôles automatisés. Non encore déployé — la mise en production de la plateforme est conditionnée à l'achèvement de l'ensemble du dispositif. |
| **Date de correction** | 27 août 2026 |
| **Rédacteur** | Équipe technique CommonLink |

---

## 1. Avertissement sur la portée de ce document

Ce document décrit **un seul contrôle** : l'enregistrement, sur le journal de conformité, de
chaque décision prise à l'ouverture d'une collecte (retenue ou refusée, et pour quel motif).

Le dispositif LCB-FT de CommonLink est organisé en six responsabilités, dont celle-ci n'est
qu'une sous-composante d'une seule. Le **générateur du rapport annuel lui-même** — qui doit
calculer les six métriques exigées par le texte et produire le document publié — **n'est pas
construit** et fait l'objet d'une tâche de suivi distincte (`1216210853624520`, § 6).

Aucune conclusion sur la conformité globale du dispositif, ni sur l'existence d'un rapport annuel
publiable, ne peut être tirée de la présente fiche.

### 1.1 Base légale — texte vérifié

Art. **R.548-4 II** CMF, rendu applicable aux intermédiaires en financement participatif ne
présentant que des appels aux dons par l'art. **R.548-10 II**, qui vise « l'article R. 548-4 »
sans restriction de paragraphe (à la différence des art. R.548-5 et R.548-7, cités eux avec des
alinéas précis) :

> « L'intermédiaire en financement participatif publie sur son site internet, avant le 30 juin,
> un rapport d'activité de l'année civile précédente présentant son dispositif de gouvernance et
> indiquant le nombre et le montant total des projets reçus et retenus dans l'année, le nombre des
> projets effectivement financés, le montant total des financements sous forme de crédits, prêts à
> titre gratuit et dons, le nombre total de prêteurs, le nombre moyen de prêteurs par projet, le
> montant moyen des crédits, prêts à titre gratuit et dons par prêteur et les indicateurs de
> défaillance définis au b du 3° de l'article R. 548-5. »

Texte lu directement sur Légifrance (le PDF de la notice ACPR correspondante a répondu par une
erreur d'accès au moment de la vérification). Les **indicateurs de défaillance** (renvoi au b du
3° de l'art. R.548-5) ne sont **pas applicables** à CommonLink : l'art. R.548-10 II n'importe pour
les intermédiaires dons-only que le 1°, le a du 3° et le a du 4° de l'art. R.548-5 — pas le b du
3°. Cette ligne du rapport est donc sans objet ici.

## 2. Le risque constaté

Avant ce contrôle, **aucune trace ne subsistait** d'une tentative d'ouverture de collecte refusée.
Un refus se traduisait uniquement par une réponse HTTP 422 renvoyée à l'appelant, sans aucune
écriture durable. La seule trace persistante du parcours d'une campagne était son statut courant
(`DRAFT`, `LIVE`, …) — qui ne dit rien du nombre de tentatives refusées avant un éventuel succès,
ni du motif de ces refus.

Le rapport annuel (§ 1.1) exige de distinguer les projets **reçus** des projets **retenus** — une
métrique de sélection à l'entrée, qui suppose de compter aussi ce qui a été refusé, pas seulement
ce qui a été publié. Sans instrumentation, cette métrique n'était pas calculable.

**Ce constat conditionne la date de mise en production**, pas seulement la date du rapport : la
plateforme n'encaisse aucun don réel à ce jour, et l'exercice comptable du premier rapport
démarrera à la mise en production, non au 1ᵉʳ janvier civil. Une fois l'encaissement ouvert,
chaque tentative de publication doit être tracée dès le premier don — une lacune constatée après
coup ne peut pas être comblée rétroactivement (§ 6).

## 3. Pourquoi ce risque existait

`CampaignService.preparePublish()` est le point unique où toutes les conditions d'ouverture d'une
collecte sont évaluées (objectif, calendrier, budget prévisionnel équilibré, résultat attendu,
vérification KYB, moyen d'encaissement bancaire, acceptation des CGU). Chaque condition non
remplie lève une exception qui interrompt la transaction et remonte au client — sans jamais
traverser le journal de conformité (`compliance_audit_log`, WORM, déjà utilisé par les autres
contrôles LCB-FT de la plateforme).

Cette méthode a été enrichie à plusieurs reprises depuis sa création (verrou KYB le 5 août 2026,
budget et résultat attendu le 21 août, calendrier le 26 août) sans qu'aucune de ces évolutions
n'introduise de traçage — chacune répondait à un besoin métier distinct, aucune n'avait pour objet
la conformité du rapport annuel.

## 4. Le contrôle mis en place

Chacun des neuf gardes-fous de `preparePublish()` écrit désormais, immédiatement avant son
exception, un événement `CAMPAIGN_REVIEW_REFUSED` sur le journal de conformité — `subject_type =
CAMPAIGN`, `subject_id` = la campagne, motif typé. Lorsque toutes les conditions sont remplies, un
événement `CAMPAIGN_REVIEW_RETAINED` est écrit avant le retour de la méthode. Les deux types
d'événement réutilisent l'infrastructure WORM existante (`ComplianceAuditLogService`, chaînage de
hash, écriture en transaction indépendante) sans migration de base de données : `CAMPAIGN` figure
déjà dans la contrainte `CHECK` du type de sujet depuis la migration V63, et le type d'événement
est du texte libre par conception (voir le KDoc de `ComplianceAuditLog`).

### 4.1 Neuf motifs, pas six — écart avec l'esquisse initiale

Le sprint qui a posé la décision D10 (traçage des refus) envisageait six motifs (`GOAL_MISSING`,
`KYB_NOT_VERIFIED`, `BANK_NOT_READY`, `FREEZE_HIT`, `OUT_OF_SCOPE_9220`, `MANUAL_CURATOR`). Cette
liste a été écrite avant les durcissements des 21 et 26 août (§ 3) et ne correspond plus aux
gardes-fous réellement présents dans `preparePublish()`. Plutôt que de forcer les nouveaux
gardes-fous dans les motifs existants — ce qui aurait produit un motif inexact dans un document à
vocation réglementaire — l'énumération a été alignée sur le code réel :

| Motif enregistré | Garde-fou déclencheur |
|---|---|
| `GOAL_MISSING` | Objectif de collecte non renseigné ou nul |
| `SCHEDULE_MISSING` | Date de début ou de fin non renseignée |
| `BUDGET_UNBALANCED` | Budget prévisionnel absent ou déséquilibré (tolérance d'un euro) |
| `IMPACT_GOALS_MISSING` | Résultat attendu absent ou inférieur à vingt caractères |
| `KYB_NOT_VERIFIED` | Dossier d'identification de l'association non vérifié |
| `BANK_NOT_CONNECTED` | Aucun compte Mollie connecté |
| `BANK_CONNECTION_BROKEN` | Connexion Mollie rompue |
| `BANK_KYC_INCOMPLETE` | Vérification d'identité Mollie non aboutie |
| `CGU_NOT_ACCEPTED` | Conditions générales d'utilisation non acceptées (art. 1740 A CGI) |

**`FREEZE_HIT`, `OUT_OF_SCOPE_9220` et `MANUAL_CURATOR` ne figurent pas dans cette liste.**
`preparePublish()` ne vérifie que `verificationStatus == VERIFIED` — une valeur binaire au sens
utile ici (`UNVERIFIED`, `PENDING`, `VERIFIED`, `REJECTED`) qui ne porte aucune information sur la
raison d'un `REJECTED`. Cette raison, quand il y en a une, est déjà enregistrée séparément, au
niveau de l'association et non de la campagne, par `ComplianceAuditLogService
.appendOutOfScopeRefusal` et `.appendNoRepresentativeRefusal`, appelés depuis l'admission KYB
(`VerificationService.adminApprove`). À ce stade de publication, ces trois cas ne peuvent
apparaître que sous `KYB_NOT_VERIFIED` — les distinguer supposerait de faire remonter cette
information jusqu'à `preparePublish()`, ce qui n'a pas été fait ici : élargir la portée de ce
contrôle au-delà de ce qu'il peut observer directement aurait introduit une dépendance non
demandée.

### 4.2 Signalement confirmé et suspension — hors du compteur de refus

Une campagne signalée par le public puis suspendue **n'est pas comptée comme un refus**. Deux
raisons, l'une juridique, l'autre technique :

- l'art. R.548-4 II mesure une **sélection à l'entrée** — projets reçus contre projets retenus.
  Une campagne qui est passée `LIVE` et a collecté des dons est, par construction, un projet
  **retenu**, et un projet **financé** si un don a été encaissé. La compter en refus fausserait le
  rapport dans les deux sens ;
- **la suspension n'existe qu'au niveau de l'association**, jamais au niveau d'une campagne
  précise. `ComplianceAlertOrigin.CAMPAIGN_REPORT` porte, dans son propre commentaire de code :
  *« a report is not scoped to the single reported campaign, since AssociationProfile.status
  suspension applies to the whole association's portfolio »*. Aucun état « campagne suspendue »
  n'est disponible à faire figurer dans ce journal.

Un signalement confirmé fondé (`compliance_alert`, origin `CAMPAIGN_REPORT`, decision
`SUSPICIOUS`) et une éventuelle réactivation (`ASSOCIATION_REACTIVATED`) restent journalisés —
sans écriture nouvelle, l'infrastructure existante les couvre déjà. Leur exploitation relève du
volet narratif « dispositif de gouvernance » du rapport annuel, pas des six métriques chiffrées,
et n'est pas construite par ce contrôle (§ 6).

### 4.3 Clé d'agrégation donateur — point antérieurement ouvert, clos

Le rapport annuel exige un nombre total de prêteurs (donateurs, ici) et un montant moyen par
prêteur. Ces deux métriques supposent qu'un même donateur ne soit pas compté deux fois. Un
document de suivi antérieur (sprint LCB-FT du 19 août 2026) présentait ce point comme ouvert, au
motif que les comptes invités sont provisionnés automatiquement par le widget de don.

Vérification faite sur le code actuel : `GuestDonorService.findOrCreateGuestDonor` (livré sprint
16, **antérieur** au document qui rouvrait la question) résout le donateur par adresse e-mail
normalisée (`trim().lowercase()`, recherche par `findByEmailIgnoreCase`) et est explicitement
documenté et testé comme idempotent — un même e-mail produit toujours le même `DonorProfile`.
Seuls `GuestDonorService` et `AuthService` créent des `DonorProfile` dans le code actuel ; les deux
respectent la contrainte d'unicité `user_id`. Ce point peut être considéré clos : aucune
correction n'était nécessaire. Limite inhérente, non corrigible par le code : une même personne
utilisant deux adresses e-mail différentes reste comptée deux fois — hors périmètre technique.

## 5. Les éléments de preuve

Dix contrôles automatisés couvrent ce contrôle — un par motif de refus (neuf), plus un pour la
retenue — ajoutés à `CampaignServiceTest.kt` (tests d'intégration Testcontainers, base PostgreSQL
réelle). Chaque test reproduit la précondition d'un seul garde-fou, déclenche la tentative de
publication, puis vérifie l'événement inscrit sur le journal de conformité.

| Scénario | Événement attendu | Motif attendu | Résultat |
|---|---|---|---|
| Toutes les conditions réunies | `CAMPAIGN_REVIEW_RETAINED` | — | Vérifié |
| Objectif nul | `CAMPAIGN_REVIEW_REFUSED` | `GOAL_MISSING` | Vérifié |
| Calendrier absent | `CAMPAIGN_REVIEW_REFUSED` | `SCHEDULE_MISSING` | Vérifié |
| Budget prévisionnel absent | `CAMPAIGN_REVIEW_REFUSED` | `BUDGET_UNBALANCED` | Vérifié |
| Résultat attendu absent | `CAMPAIGN_REVIEW_REFUSED` | `IMPACT_GOALS_MISSING` | Vérifié |
| Dossier KYB non vérifié | `CAMPAIGN_REVIEW_REFUSED` | `KYB_NOT_VERIFIED` | Vérifié |
| Aucun compte Mollie connecté | `CAMPAIGN_REVIEW_REFUSED` | `BANK_NOT_CONNECTED` | Vérifié |
| Connexion Mollie rompue | `CAMPAIGN_REVIEW_REFUSED` | `BANK_CONNECTION_BROKEN` | Vérifié |
| KYC Mollie incomplet | `CAMPAIGN_REVIEW_REFUSED` | `BANK_KYC_INCOMPLETE` | Vérifié |
| CGU non acceptées | `CAMPAIGN_REVIEW_REFUSED` | `CGU_NOT_ACCEPTED` | Vérifié |

L'intégralité de la suite `CampaignServiceTest` (66 tests, dont les 56 préexistants) a été
réexécutée après la correction, sans régression. Les suites `ComplianceAuditLogServiceLogicTest`
et `CampaignReportServiceTest`, qui partagent l'infrastructure du journal de conformité, ont
également été réexécutées sans régression liée à ce contrôle. `CampaignReportServiceIntegrationTest`
échoue de façon reproductible, y compris exécutée seule et sans aucun rapport avec ce contrôle
(dédoublonnage d'alerte de signalement) — dette de test préexistante, hors périmètre de cette
fiche.

## 6. Ce que ce contrôle ne couvre pas

Ce contrôle garantit que chaque tentative d'ouverture de collecte, à compter de son déploiement,
laisse une trace exploitable — retenue ou refusée, et pour quel motif. **Il ne calcule, n'agrège
ni ne publie aucune métrique.** En particulier, il ne couvre pas :

- **l'immutabilité WORM du journal lui-même.** Ce contrôle vérifie que l'événement attendu est
  bien écrit ; il ne vérifie pas qu'il ne peut plus être modifié ensuite. Cette propriété dépend du
  verrou base de données (privilèges `UPDATE`/`DELETE` révoqués, trigger `BEFORE UPDATE OR DELETE`)
  posé par la migration V51, dont les contrôles automatisés sont désactivés (`@Disabled`) — constat
  déjà porté par `LCB-FT-compliance-overview.md` § 7.4 comme porte de mise en production distincte.
  Écrire ici un test d'immutabilité aurait échoué contre ce garde-fou désactivé et donné une fausse
  impression de couverture ; il n'a donc pas été écrit ;
- **la fenêtre d'écriture de l'événement retenu.** `CAMPAIGN_REVIEW_RETAINED` est écrit — et
  committé, en transaction indépendante — avant que `updateCampaign()` ne persiste effectivement le
  passage à `LIVE`. Si cette dernière écriture échouait pour une raison indépendante de
  `preparePublish()`, l'événement resterait un « projet retenu » enregistré pour une campagne restée
  `DRAFT`. Ce risque est accepté en l'état (fenêtre étroite : seul un échec de base de données entre
  les deux écritures peut le déclencher) plutôt que d'éclater le point d'écriture unique de
  `preparePublish()` en fractionnant les gardes-fous de refus et l'écriture de la retenue entre deux
  méthodes ;
- le **générateur du rapport annuel** lui-même — requêtes de comptage (reçus, retenus, financés),
  sommes et moyennes, mise en forme et publication avant le 30 juin *(tâche Asana
  `1216210853624520`, dépend de ce contrôle)* ;
- la définition opérationnelle de « projet effectivement financé » (retenue : au moins un don
  encaissé) n'est appliquée dans aucune requête — elle n'existe pour l'instant que comme décision
  écrite ;
- le **volet narratif « dispositif de gouvernance »** du rapport, qui doit exposer les
  signalements confirmés et les suspensions (§ 4.2) — les données existent déjà dans le journal,
  leur mise en forme n'est pas construite ;
- la **reconstitution rétroactive** : aucune donnée n'existe pour les tentatives de publication
  antérieures au déploiement de ce contrôle. Les transitions de statut de campagne ne sont pas
  elles-mêmes journalisées (`enqueueForTransition` n'a pas de branche DRAFT→LIVE, `updatedAt` est
  écrasé à chaque modification) — c'est pourquoi ce contrôle est une **porte de mise en
  production** et non une simple amélioration : sans lui, dès le premier don encaissé, le premier
  rapport annuel serait déjà incalculable pour la période écoulée avant sa livraison.

**Suivi des travaux restants.**

| Travail restant | Lien de suivi |
|---|---|
| Générateur du rapport annuel IFP — publication avant le 30 juin | [1216210853624520](https://app.asana.com/1/1213718564226627/project/1213723193546726/task/1216210853624520) |
| Archivage immuable des pièces justificatives et purge à cinq ans | [1216210853624518](https://app.asana.com/1/1213718564226627/project/1213723193546726/task/1216210853624518) · [1216210853624517](https://app.asana.com/1/1213718564226627/project/1213723193546726/task/1216210853624517) |

## 7. Situation de ce contrôle dans le dispositif d'ensemble

| Responsabilité du dispositif | État au 27 août 2026 |
|---|---|
| 1 — Désignation des responsables et cloisonnement des accès | Partiellement réalisé |
| 2 — Classification des risques et procédures écrites | Infrastructure livrée — prérequis non technique en attente |
| 3 — Entrée en relation et vérification de l'association | Partiellement réalisé |
| 4 — Gel des avoirs | Substantiellement réalisé |
| 5 — Surveillance des opérations atypiques et déclaration | Non engagé |
| **6 — Conservation des documents et rapport annuel** | **Ce contrôle (traçage des décisions) est réalisé. L'archivage immuable, la purge à cinq ans et le générateur du rapport lui-même restent à construire.** |

## 8. Éléments de traçabilité

La correction porte sur trois fichiers de production (`Enums.kt` — nouvel enum
`CampaignReviewRefusalReason`, `ComplianceAuditLogService.kt` — deux méthodes d'écriture et une de
lecture, `CampaignService.kt` — instrumentation de `preparePublish()`) et un fichier de test
(`CampaignServiceTest.kt` — dix tests, plus l'ajout du script de schéma partagé
`compliance_audit_log_test_schema.sql` déjà utilisé par d'autres suites de tests du même journal).
Aucune migration de base de données n'a été nécessaire.

Le raisonnement exposé au § 4.1 — pourquoi la liste de motifs a été alignée sur le code réel
plutôt que sur l'esquisse initiale — et celui du § 4.3 — pourquoi le point d'agrégation du
donateur est clos — sont documentés dans le code lui-même (KDoc de l'enum et du service), à
l'endroit où un lecteur futur les rencontrerait en cherchant à comprendre pourquoi ces motifs
diffèrent de ceux annoncés ailleurs dans le dépôt.

---

*Document établi le 27 août 2026. Une fiche de même nature sera produite pour chaque contrôle du
dispositif LCB-FT au fur et à mesure de sa réalisation.*
