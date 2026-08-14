# Contrôle de gel des avoirs à la création d'un don

**Fiche de contrôle — dispositif de lutte contre le blanchiment de capitaux et le financement du terrorisme (LCB-FT)**

| | |
|---|---|
| **Entité** | CommonLink |
| **Nature du document** | Fiche descriptive d'un contrôle mis en place, destinée à la commission juridique |
| **Responsabilité concernée** | Gel des avoirs — filtrage obligatoire avant tout encaissement d'un don *(responsabilité n° 4 du dispositif interne, épique E4)* |
| **Référence de suivi interne** | Tâche Asana n° `1216210853624506` — « Contrôle de gel à la création du don (état `PENDING_FIAT`) » |
| **Lien de suivi** | https://app.asana.com/1/1213718564226627/project/1213723193546726/task/1216210853624506 |
| **Priorité assignée** | P0 (la plus élevée) |
| **Date de mise en place** | 11 août 2026 |
| **État** | Mis en place et vérifié par contrôles automatisés. Non encore déployé — la mise en production de la plateforme est conditionnée à l'achèvement de l'ensemble du dispositif. |
| **Rédacteur** | Équipe technique CommonLink |

---

## 1. Avertissement sur la portée de ce document

Ce document décrit **un seul contrôle**, portant sur un seul point du parcours d'un donateur sur
la plateforme : la vérification de l'absence de mesures de gel des avoirs au moment de la création
d'un don, préalablement à tout encaissement.

Le dispositif LCB-FT de CommonLink est organisé en six responsabilités, dont celle-ci ne constitue
qu'un élément. Les cinq autres — désignation des responsables, classification des risques,
vérification de l'identité, surveillance des opérations atypiques et déclaration, conservation
des documents et rapport annuel — font l'objet de travaux distincts dont l'état d'avancement
est suivi séparément.

Aucune conclusion sur la conformité globale du dispositif ne peut être tirée de la présente fiche.

## 2. Le risque couvert

La réglementation (articles L. 561-5 et L. 562-2 du code monétaire et financier) impose à toute
entité assujettie de vérifier, avant d'entrer en relation d'affaires et avant tout encaissement,
que le client ne fait pas l'objet d'une mesure de gel des avoirs.

Cette obligation est dite **de résultat** : l'entité assujettie doit être en mesure de démontrer
que le contrôle a effectivement eu lieu, au moment requis. L'absence de trace du contrôle ne se
distinguerait pas juridiquement d'un contrôle non effectué.

Sur la plateforme CommonLink, un don est créé à l'initiative d'un donateur qui fournit ses
informations d'identité via le formulaire de don. C'est à ce point précis — **avant que tout
mouvement de fonds ne soit initié** — que le contrôle de gel doit être exécuté.

## 3. Pourquoi ce contrôle est automatique et bloquant

Le contrôle de gel d'un donateur est d'une nature différente de diligences laissées à l'appréciation
d'un opérateur. Il résulte d'une obligation de résultat à exécuter sans délai, avant tout
encaissement. Un contrôle différé ou réalisé après l'initiation du paiement viderait l'obligation
de son contenu.

**Ce contrôle est :**

- **automatique** — il est déclenché par le système lui-même dès qu'un donateur soumet le formulaire
  de don, sans intervention ni décision d'un opérateur ;
- **bloquant** — aucun paiement n'est initié si le contrôle révèle une correspondance avec le
  registre, ou s'il ne peut pas être mené à son terme. Le donateur ne dispose d'aucun moyen de
  contourner ce refus ;
- **préalable à l'encaissement** — le contrôle précède l'initiation du paiement auprès du
  prestataire de services de paiement ; aucun paiement n'est créé avant que le résultat du contrôle
  soit connu. Cette séquence évite d'avoir à procéder à un remboursement après encaissement d'un
  don qui n'aurait pas dû être accepté.

## 4. Le périmètre du contrôle

### 4.1 La cible du filtrage

Le nom complet du donateur, tel qu'il est fourni lors de la soumission du formulaire de don, est
filtré contre le registre national des mesures de gel des avoirs, publié par la Direction générale
du Trésor. Ce registre consolide les mesures françaises, européennes et onusiennes applicables en
France.

### 4.2 Absence de seuil de montant

Le contrôle est effectué pour **chaque don, quel que soit son montant**. Aucun seuil minimal ne
conditionne l'exécution du contrôle. Un don de faible montant provenant d'une personne faisant
l'objet d'une mesure de gel est refusé au même titre qu'un don de montant élevé.

### 4.3 Utilisation de la date de naissance pour réduire les faux positifs

La correspondance de noms est effectuée par similarité — une méthode qui tolère les variations
orthographiques courantes (accents, inversions de prénom et nom, particules omises). Cette
tolérance peut produire des correspondances entre des personnes portant des noms similaires sans
être la même personne (homonymes).

Pour limiter ces faux positifs, lorsque le registre comporte une date de naissance pour l'entrée
correspondante, **la date de naissance du donateur est comparée à celle figurant dans le registre**.
Une correspondance de nom est écartée si les années de naissance diffèrent, permettant de distinguer
deux personnes homonymes. Lorsque le registre ne comporte pas de date de naissance, la correspondance
est conservée et transmise à un examen humain — cette prudence est préférable au risque d'un faux
négatif.

### 4.4 Résultats possibles du contrôle

Le contrôle produit l'un des résultats suivants :

- **Aucune correspondance** — le donateur ne figure pas dans le registre. Le don peut être initié,
  sous réserve des autres contrôles en place.
- **Correspondance détectée** — le donateur présente une correspondance dans le registre. Le don
  est refusé. Une alerte est transmise au responsable de la conformité.
- **Contrôle impossible** — le service de filtrage est indisponible. Voir section 5.

## 5. Traitement de l'indisponibilité du service de filtrage

Un **contrôle impossible n'est pas un contrôle favorable.**

Si le service de filtrage est indisponible au moment de la création du don — que le registre n'ait
pas encore été chargé ou qu'une erreur technique soit survenue — le don est **refusé**, et
l'impossibilité d'effectuer le contrôle est enregistrée dans le journal des contrôles de conformité.

Cette règle découle directement de l'obligation de résultat : ne pas pouvoir démontrer qu'un
contrôle a été effectué équivaut, pour le régulateur, à ne pas avoir effectué le contrôle.

### Information du donateur

Le donateur reçoit un message neutre et générique, identique qu'une correspondance ait été détectée
ou que le service soit indisponible. **Le donateur n'est pas informé de la raison réelle du refus.**
Cette règle répond à une obligation légale : révéler à une personne faisant l'objet d'une mesure de
gel qu'elle a été identifiée comme telle constituerait une divulgation susceptible de nuire aux
intérêts de l'État. Le message affiché est indiscernable d'un message d'indisponibilité générique.

## 6. Les éléments de preuve

La mise en place du contrôle est accompagnée de **contrôles automatisés** couvrant l'ensemble
des scénarios réglementairement pertinents. Ces contrôles sont réexécutés à chaque modification
du logiciel : toute évolution qui affaiblirait ou supprimerait le contrôle de gel serait signalée
en échec avant d'être intégrée.

| Scénario | Décision sur le don | Journalisé |
|---|---|---|
| Aucune correspondance | Don possible | Oui — résultat sans correspondance — vérifié |
| Correspondance détectée | Don refusé | Oui — correspondance journalisée, alerte transmise — vérifié |
| Don de faible montant, correspondance détectée | Don refusé — le refus est indépendant du montant | Oui — vérifié (le chemin de refus ne tient pas compte du montant) |
| Homonyme avec année de naissance différente | Don possible | Oui — résultat sans correspondance après exclusion par date de naissance — vérifié |
| Service de filtrage indisponible | Don refusé | Oui — indisponibilité journalisée — vérifié |
| Correspondance détectée et service indisponible | Message identique au donateur dans les deux cas | Oui — indiscernabilité vérifiée par assertion explicite — vérifié |

Le quatrième scénario est particulièrement significatif : il démontre que la date de naissance est
utilisée pour écarter un homonyme, réduisant ainsi le nombre de faux positifs transmis à l'examen
humain, sans jamais créer de faux négatif (une personne sans date de naissance dans le registre
est toujours retenue pour examen).

Le sixième scénario démontre que le message reçu par le donateur est identique qu'une correspondance
ait été détectée ou que le service soit indisponible — conformément à l'obligation légale de
non-divulgation.

Chaque résultat de filtrage — correspondance, absence de correspondance ou impossibilité — est
inscrit dans le journal de contrôle de conformité protégé par une chaîne de hachage. Les noms
des donateurs ne figurent jamais dans les journaux applicatifs.

## 7. Ce que ce contrôle ne couvre pas

Cette précision est apportée pour éviter toute lecture extensive de la présente fiche.

Le présent contrôle garantit qu'aucun don ne peut être initié pour un donateur dont le nom figure
dans le registre de gel. **Il ne porte pas sur les obligations suivantes** :

- le **contrôle de gel des associations** et de leurs représentants ou bénéficiaires effectifs —
  ce contrôle, qui précède l'entrée en relation d'affaires avec l'association, fait l'objet d'une
  fiche distincte, *livrée* ;
- la **surveillance des opérations atypiques** réalisées par les associations ou les donateurs,
  et la déclaration de soupçon qui peut en découler. Ce point recouvre en particulier la
  **détection du fractionnement** — un même donateur multipliant les dons de faible montant :
  le présent contrôle filtre chaque don isolément et ne rapproche jamais deux dons entre eux ;
- la **vérification de l'identité du donateur** au-delà du filtrage de son nom contre le registre
  de gel — la plateforme ne dispose pas, à ce stade, d'un dispositif de vérification d'identité
  des donateurs (KYC donateur). Le nom filtré est celui que le donateur déclare, sans pièce
  justificative à l'appui ;
- le **re-criblage des dons déjà encaissés** après une mise à jour du registre — le contrôle est
  exécuté une fois, avant l'encaissement, et n'est pas rejoué ensuite ;
- la **conservation** des pièces justificatives au-delà de leur durée réglementaire et le
  **rapport annuel** d'activité de conformité.

Chacun de ces points fait l'objet de travaux planifiés et ordonnancés dans le cadre du dispositif
d'ensemble.

**Suivi des travaux restants.** Les éléments ci-dessus qui restent à réaliser sont suivis dans le
référentiel interne de gestion de projet aux références suivantes.

| Travail restant | Lien de suivi |
|---|---|
| Détection du fractionnement — même donateur, dons multiples de faible montant *(épique E5)* | [1216210853624511](https://app.asana.com/1/1213718564226627/project/1213723193546726/task/1216210853624511) |
| Moteur de règles d'atypie — montant, vélocité, géographie *(épique E5)* | [1216210853624512](https://app.asana.com/1/1213718564226627/project/1213723193546726/task/1216210853624512) |
| Back-office de revue des alertes *(épique E5)* | [1216210853624513](https://app.asana.com/1/1213718564226627/project/1213723193546726/task/1216210853624513) |
| Modèle de dossier de soupçon *(épique E5)* | [1216210853624514](https://app.asana.com/1/1213718564226627/project/1213723193546726/task/1216210853624514) |
| Conservation des pièces justificatives et rapport annuel *(épique E6)* | [1216210853624518](https://app.asana.com/1/1213718564226627/project/1213723193546726/task/1216210853624518) · [1216210853624517](https://app.asana.com/1/1213718564226627/project/1213723193546726/task/1216210853624517) · [1216210853624520](https://app.asana.com/1/1213718564226627/project/1213723193546726/task/1216210853624520) |
| Vérification d'identité du donateur (KYC donateur) | *Aucune tâche de suivi identifiée dans le référentiel de projet* |
| Re-criblage des dons déjà encaissés après mise à jour du registre | *Aucune tâche de suivi identifiée dans le référentiel de projet* |

> **Point à trancher avant la détection du fractionnement.** Les comptes de donateurs invités sont
> provisionnés automatiquement par le formulaire de don : deux dons d'une même personne peuvent
> porter deux profils distincts. Sans clé d'agrégation stable du donateur, une détection de
> fractionnement serait vide de sens. Cette question conditionne le livrable et doit être arbitrée
> avant qu'il soit spécifié.

## 8. Situation de ce contrôle dans le dispositif d'ensemble

| Responsabilité du dispositif | État au 11 août 2026 |
|---|---|
| 1 — Désignation des responsables et cloisonnement des accès | Planifié |
| 2 — Classification des risques et procédures écrites | Planifié |
| 3 — Entrée en relation et vérification de l'association | Partiellement réalisé — voir fiches E2 et E3 |
| **4 — Gel des avoirs** | **Filtrage à l'entrée en relation (association) : réalisé. Filtrage à la création du don (donateur) : réalisé (ce document). Filtrage périodique automatique : planifié.** |
| 5 — Surveillance des opérations atypiques et déclaration | Planifié |
| 6 — Conservation des documents et rapport annuel | Planifié |

## 9. Éléments de traçabilité

Le contrôle est enregistré dans l'historique des modifications du logiciel, sur une ligne de
développement dédiée au dispositif LCB-FT.

Chaque résultat de filtrage — qu'il s'agisse d'une correspondance, d'une absence de correspondance
ou d'une impossibilité de contrôler — est inscrit dans un journal de contrôle de conformité protégé
par une chaîne de hachage. Cette structure garantit qu'aucune entrée ne peut être modifiée ou
supprimée sans que l'intégrité de la chaîne soit compromise.

Les noms des donateurs ne figurent jamais dans les journaux applicatifs : seuls les journaux de
conformité, soumis à des règles d'accès distinctes, contiennent les informations relatives aux
résultats de filtrage.

---

*Document établi le 11 août 2026. Une fiche de même nature est produite pour chaque contrôle du
dispositif LCB-FT au fur et à mesure de sa réalisation.*
