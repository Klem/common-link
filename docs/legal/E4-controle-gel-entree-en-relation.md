# Contrôle de gel des avoirs à l'entrée en relation d'affaires

**Fiche de contrôle — dispositif de lutte contre le blanchiment de capitaux et le financement du terrorisme (LCB-FT)**

| | |
|---|---|
| **Entité** | CommonLink |
| **Nature du document** | Fiche descriptive d'un contrôle mis en place, destinée à la commission juridique |
| **Responsabilité concernée** | Gel des avoirs — filtrage obligatoire à l'entrée en relation d'affaires *(responsabilité n° 4 du dispositif interne, épique E4)* |
| **Référence de suivi interne** | Tâche Asana n° `1216210853624505` — « Hook screening à l'onboarding de l'association » |
| **Lien de suivi** | https://app.asana.com/1/1213718564226627/project/1213723193546726/task/1216210853624505 |
| **Priorité assignée** | P0 (la plus élevée) |
| **Date d'établissement** | 11 août 2026 — état décrit arrêté au 13 août 2026 |
| **Base légale** | Art. L. 561-5, L. 562-2 et R. 561-3 du code monétaire et financier · décret n° 2024-720 du 5 juillet 2024 |
| **État** | Mis en place et vérifié par contrôles automatisés. Non encore déployé — la mise en production de la plateforme est conditionnée à l'achèvement de l'ensemble du dispositif. |
| **Rédacteur** | Équipe technique CommonLink |

---

## 1. Avertissement sur la portée de ce document

Ce document décrit **un seul contrôle**, portant sur un seul point du parcours d'une association
sur la plateforme : la vérification de l'absence de mesures de gel des avoirs au moment de
l'entrée en relation d'affaires. Il décrit également la constitution du périmètre de personnes
physiques sur lequel ce contrôle porte, sans laquelle le contrôle serait sans objet.

Le dispositif LCB-FT de CommonLink est organisé en six responsabilités, dont celle-ci ne constitue
qu'un élément. Les autres sont à des stades d'avancement différents ; leur état est rappelé au
point 9.

Aucune conclusion sur la conformité globale du dispositif ne peut être tirée de la présente fiche.

## 2. Le risque couvert

La réglementation (articles L. 561-5 et L. 562-2 du code monétaire et financier) impose à toute
entité assujettie de vérifier, **avant d'entrer en relation d'affaires**, que ni le client ni les
personnes qui le représentent ou le contrôlent ne font l'objet d'une mesure de gel des avoirs.

Cette obligation est dite **de résultat** : l'entité assujettie doit être en mesure de démontrer
que le contrôle a effectivement eu lieu, au moment requis, et qu'il a porté sur l'ensemble des
personnes concernées. L'absence de trace du contrôle ne se distinguerait pas juridiquement d'un
contrôle non effectué.

Sur la plateforme CommonLink, l'entrée en relation d'affaires avec une association intervient
au moment de la **validation de son dossier d'identification** par un opérateur de la plateforme.
C'est à ce point précis que le contrôle de gel doit être exécuté.

## 3. Pourquoi ce contrôle est automatique et bloquant

La plupart des vérifications réalisées par la plateforme résultent d'une **décision d'un opérateur**
(le curateur) : la consultation des registres publics pour vérifier l'existence juridique de
l'association, ou la collecte des informations relatives aux bénéficiaires effectifs, par exemple,
restent à l'initiative d'un opérateur humain.

**Le contrôle de gel est d'une nature différente.** Il résulte d'une obligation de résultat à
exécuter « sans délai » : le législateur n'a pas laissé à l'assujetti la faculté d'apprécier
l'opportunité ou le moment du contrôle. Un contrôle laissé à l'initiative d'un opérateur pourrait
être différé, oublié, ou exécuté après que la relation d'affaires est déjà engagée. Ces
défaillances sont précisément ce que la réglementation cherche à prévenir.

Pour cette raison, le contrôle de gel est :

- **automatique** — il est déclenché par le système lui-même, sans intervention ni décision d'un
  opérateur, dès qu'un opérateur souhaite valider un dossier d'identification ;
- **bloquant** — la validation du dossier est refusée si le contrôle révèle une correspondance,
  ou s'il ne peut pas être mené à son terme. L'opérateur ne dispose d'aucun moyen de contourner
  ce refus par un paramètre ou une option de configuration.

Cette distinction entre contrôles à déclenchement opérateur et contrôle automatique bloquant
est assumée et justifiée : elle traduit dans le fonctionnement de la plateforme la hiérarchie
établie par la réglementation entre diligences facultatives et obligations de résultat.

## 4. Le périmètre du contrôle : trois parties obligatoirement filtrées

L'obligation réglementaire de contrôle de gel porte non seulement sur l'association elle-même,
mais aussi sur les personnes qui la représentent ou qui en exercent le contrôle effectif.
**Omettre l'une des trois cibles suivantes viderait le contrôle de son sens juridique.**

### 4.1 L'association elle-même

Le nom officiel de l'association est filtré contre le registre national des mesures de gel, tel
que publié par la Direction générale du Trésor. Ce registre consolide les mesures françaises,
européennes et onusiennes applicables en France.

### 4.2 Ses représentants légaux

Les représentants légaux de l'association sont filtrés individuellement : chacun d'eux peut faire
l'objet d'une mesure de gel indépendante de toute mesure visant l'association.

Le périmètre des représentants est constitué de **deux sources cumulées** :

- les **dirigeants publiés aux registres officiels**, tels qu'ils ressortent de la consultation des
  registres publics décrite par la fiche *E3 — Contrôle des registres publics d'associations* ;
- les **représentants légaux saisis au dossier par le curateur**, dans les conditions décrites au
  point 4.4.

Les deux ensembles sont réunis avant filtrage. Le contrôle n'est déclaré impossible sur ce périmètre
que si les **deux** sources sont simultanément absentes — c'est-à-dire si aucun dirigeant ne ressort
des registres officiels **et** qu'aucun représentant n'a été saisi au dossier. Chaque résultat de
filtrage est journalisé séparément, en distinguant l'origine de la personne filtrée.

### 4.3 Chacun de ses bénéficiaires effectifs retenus

Les bénéficiaires effectifs sont filtrés individuellement. Chaque bénéficiaire effectif retenu dans
le dossier fait l'objet d'un filtrage distinct, et le résultat de chaque filtrage est journalisé
séparément.

### 4.4 Les deux catégories de personnes physiques au dossier

**Fondement juridique de l'assimilation.** Le décret n° 2024-720 du 5 juillet 2024, à l'article
R. 561-3 du code monétaire et financier, dispose que lorsque le client est une association, sont
considérées comme bénéficiaires effectifs « les personnes physiques qui exercent en son sein des
fonctions d'administrateur, de membre de l'organe de surveillance ou de dirigeant ». Cette
définition assimile les représentants légaux — président, trésorier, secrétaire, administrateurs —
aux bénéficiaires effectifs au sens de l'article R. 561-1. La distinction entre dirigeants publiés
et bénéficiaires effectifs déclarés n'a donc pas de portée juridique propre : les deux catégories
sont soumises aux mêmes obligations de filtrage.

**Deux catégories tenues au dossier.** Le dossier d'identification distingue néanmoins, pour des
raisons d'instruction et de traçabilité de la source :

- les **représentants légaux** — président, trésorier, secrétaire, administrateurs ;
- les **bénéficiaires effectifs** au sens strict.

Les deux catégories sont soumises aux mêmes règles de chiffrement, de journalisation et de
conservation, et toutes deux sont filtrées contre le registre des mesures de gel.

**Saisie des représentants légaux par le curateur.** La source officielle des dirigeants est la
liste publiée au Journal officiel des associations. En pratique, cette liste est vide pour la
majorité des associations — notamment celles dépourvues d'un numéro RNA actif ou dont les
publications ne sont pas à jour. Le curateur peut donc saisir les représentants légaux d'après les
pièces d'identification présentées par l'association.

**Cette saisie n'est pas une dispense de contrôle : elle en est la modalité** lorsque la source
officielle est indisponible. Elle est encadrée par un opérateur habilité, tracée, et constitue un
contrôle humain documenté conforme aux exigences de diligence raisonnable de l'article L. 561-5 du
code monétaire et financier. Sans elle, une association dont les dirigeants ne sont pas publiés
verrait son contrôle de gel déclaré impossible et son approbation bloquée **sans recours** — un
blocage qui ne protégerait personne et écarterait des associations régulièrement déclarées.

**Conditions d'approbation du dossier.** La validation d'un dossier d'identification exige
cumulativement :

1. un verdict de périmètre qui ne soit pas « hors périmètre » (voir fiche *E3 — Contrôle automatique
   du périmètre d'intervention de la plateforme*) ;
2. au moins un **représentant légal** retenu au dossier ;
3. un résultat de filtrage sans correspondance et sans impossibilité sur les trois parties décrites
   au présent point 4.

Ces conditions sont bloquantes et sans contournement. La deuxième garantit que le filtrage porte
effectivement sur au moins une personne physique identifiée, et non sur un périmètre vide.

**L'absence de bénéficiaire effectif au sens strict ne bloque pas, à elle seule, l'approbation.**
L'article R. 561-3 du code monétaire et financier assimilant tout dirigeant d'association à un
bénéficiaire effectif, l'exigence d'au moins un représentant légal satisfait l'obligation
d'identifier une personne physique exerçant le contrôle. Les bénéficiaires effectifs au sens strict,
lorsqu'ils sont renseignés au dossier, sont filtrés au même titre.

L'écran d'examen présente les deux catégories dans deux sections distinctes, les représentants
légaux en tête, afin que l'opérateur constate immédiatement laquelle fait défaut.

### 4.5 Résultat du filtrage

Le contrôle produit l'un des résultats suivants :

- **Aucune correspondance** — aucune des trois cibles ne figure dans le registre. La validation
  du dossier peut être accordée, sous réserve des autres contrôles en place.
- **Correspondance détectée** — au moins une des trois cibles présente une correspondance dans le
  registre. La validation est refusée. Une alerte est transmise au responsable de la conformité.
- **Contrôle impossible** — le contrôle n'a pas pu être mené à son terme, notamment lorsque le
  registre n'est pas disponible ou qu'aucune personne physique n'est identifiable au dossier.
  La validation est refusée. Voir section 5.

## 5. Traitement de l'indisponibilité du service de filtrage

Un **contrôle impossible n'est pas un contrôle favorable.**

Si le service de filtrage est indisponible au moment de la validation — que le registre n'ait pas
encore été chargé, qu'une erreur technique soit survenue, ou qu'aucune des deux sources de
représentants légaux ne soit renseignée — la validation du dossier est **refusée**, et
l'impossibilité d'effectuer le contrôle est enregistrée dans le journal des contrôles de conformité.

Cette règle découle directement de l'obligation de résultat : ne pas pouvoir démontrer qu'un
contrôle a été effectué équivaut, pour le régulateur, à ne pas avoir effectué le contrôle. Une
ouverture par défaut en cas d'indisponibilité viderait l'obligation de son contenu.

Un contrôle déclaré impossible produit en outre une alerte propre, de gravité moyenne, à
destination du responsable de la conformité — distincte de l'alerte de correspondance signalée.
Ses modalités sont décrites par la fiche *E4 — Preuve de correspondance au registre de gel*.

## 6. Rejouabilité du contrôle

Le registre de gel évolue : des mesures sont régulièrement ajoutées ou levées par les autorités.
Une association dont le dossier a été validé à un moment où aucune correspondance n'existait peut
voir sa situation changer si une mesure est ultérieurement prise à l'encontre d'un de ses
représentants ou bénéficiaires effectifs.

Pour permettre de répondre à cette évolution, **le contrôle peut être déclenché à nouveau sur
une association déjà en relation d'affaires**. Ce déclenchement est explicite — sur action d'un
opérateur — et produit les mêmes effets qu'à l'entrée en relation : journalisation du résultat,
transmission d'une alerte au responsable de la conformité en cas de correspondance.

## 7. Les éléments de preuve

Les scénarios suivants sont couverts par des contrôles automatisés, réexécutés à chaque modification
du logiciel : toute évolution qui affaiblirait ou supprimerait le contrôle de gel serait signalée
en échec avant d'être intégrée.

| Scénario | Décision sur le dossier | Journalisé | État |
|---|---|---|---|
| Aucune correspondance (association, dirigeant, bénéficiaire effectif) | Validation possible | Oui — un résultat sans correspondance par cible | Vérifié |
| Correspondance sur l'association | Refus de validation | Oui — correspondance journalisée | Vérifié |
| Correspondance sur un dirigeant publié aux registres (association sans correspondance) | Refus de validation | Oui — absence de correspondance sur l'association puis correspondance sur le dirigeant | Vérifié |
| Plusieurs dirigeants publiés aux registres | Chacun est filtré individuellement | Oui — un résultat par dirigeant | Vérifié |
| Correspondance sur un bénéficiaire effectif (autres cibles sans correspondance) | Refus de validation | Oui — correspondance journalisée | Vérifié |
| Bénéficiaire effectif écarté du dossier | Exclu du filtrage | — | Vérifié |
| Aucune des deux sources de représentants renseignée | Refus de validation | Oui — impossibilité journalisée, avec alerte | Vérifié |
| Aucun représentant légal retenu au dossier | Approbation refusée avant tout filtrage, avec journalisation du refus | Oui | Vérifié |
| Représentant légal retenu mais aucun bénéficiaire effectif au sens strict | L'approbation n'est pas bloquée de ce fait | — | Vérifié |
| Registre de gel non chargé, ou erreur technique du service de filtrage | Refus de validation | Oui — indisponibilité journalisée | Vérifié |
| Correspondance sur un représentant légal saisi au dossier | Refus de validation | Oui — correspondance rattachée à l'association | Couvert par la mécanique commune de filtrage des personnes physiques ; non couvert par un scénario automatisé dédié |
| Dirigeants absents des registres, représentants saisis au dossier | Le filtrage porte sur les représentants saisis | Oui | Non couvert par un scénario automatisé dédié |

Les scénarios de correspondance sur une personne physique sont les plus significatifs. Ils
démontrent que le contrôle protège contre une mesure de gel ciblant une personne liée à
l'association, y compris lorsque l'association elle-même ne fait l'objet d'aucune mesure. C'est
précisément ce périmètre étendu que la réglementation impose.

Le scénario d'indisponibilité démontre que l'impossibilité du contrôle est traitée comme un refus, et
non comme une autorisation tacite.

**Deux scénarios ne sont pas couverts par un contrôle automatisé dédié**, et sont signalés comme
tels : le filtrage d'un représentant légal saisi au dossier, et le cas d'un dossier dont les
dirigeants ne ressortent pas des registres alors que des représentants ont été saisis. La réunion des
deux sources et le refus lorsque les deux sont absentes sont établis par le logiciel ; l'issue du
filtrage d'un représentant saisi emprunte la même mécanique que celle d'un dirigeant publié, déjà
couverte. **Cette lacune de couverture est portée à la connaissance de la commission plutôt que
présentée comme vérifiée.**

L'ensemble des contrôles automatisés de la plateforme a été exécuté, sans régression sur les
fonctionnements existants.

## 8. Ce que ce contrôle ne couvre pas

Cette précision est apportée pour éviter toute lecture extensive de la présente fiche.

Le présent contrôle garantit qu'aucune association dont un représentant ou bénéficiaire effectif
ferait l'objet d'une mesure de gel ne peut entrer en relation d'affaires avec CommonLink.
**Il ne porte pas sur les obligations suivantes** :

- le **re-contrôle périodique automatique** des associations déjà en relation d'affaires —
  si le présent contrôle peut être déclenché manuellement pour une association existante (voir
  section 6), **aucune automatisation périodique n'est en place** ; les associations
  en relation d'affaires ne sont pas re-filtrées de façon programmée à intervalles réguliers ;
- la **vérification de l'exactitude des pièces d'identification** présentées par l'association et
  d'après lesquelles le curateur saisit les représentants légaux — la saisie reporte fidèlement le
  contenu des pièces produites, elle n'en établit pas l'authenticité ;
- l'**exhaustivité de la liste des représentants** : le contrôle garantit qu'au moins un
  représentant est identifié et filtré, non que l'ensemble des personnes exerçant des fonctions
  dirigeantes figure au dossier ;
- la **couverture automatisée de deux scénarios de filtrage** — le filtrage d'un représentant légal
  saisi au dossier et le cas d'un dossier dont les dirigeants ne ressortent pas des registres :
  ces deux lacunes sont déclarées au point 7 et restent à combler ;
- la **surveillance des opérations atypiques** réalisées par les associations ou les donateurs,
  et la déclaration de soupçon qui peut en découler ;
- le **filtrage des donateurs** contre le registre de gel — le présent contrôle porte
  exclusivement sur l'association et les personnes qui la représentent ou la contrôlent.
  *Traité par la fiche E4 — Contrôle de gel des avoirs à la création d'un don, livrée* ;
- la **conservation** des pièces justificatives au-delà de leur durée réglementaire et le
  **rapport annuel** d'activité de conformité.

Chacun de ces points fait l'objet de travaux planifiés et ordonnancés dans le cadre du dispositif
d'ensemble.

**Suivi des travaux restants.** Les éléments ci-dessus qui restent à réaliser sont suivis dans le
référentiel interne de gestion de projet aux références suivantes.

| Travail restant | Lien de suivi |
|---|---|
| Rédaction des procédures LCB-FT écrites, dont l'examen des pièces d'identification par le curateur *(prérequis non technique)* | [1216210976716063](https://app.asana.com/1/1213718564226627/project/1213723193546726/task/1216210976716063) |
| Formation de l'équipe de curation à la LCB-FT *(prérequis non technique)* | [1216210976716055](https://app.asana.com/1/1213718564226627/project/1213723193546726/task/1216210976716055) |
| Surveillance des opérations atypiques et déclaration de soupçon *(épique E5)* | [1216210853624511](https://app.asana.com/1/1213718564226627/project/1213723193546726/task/1216210853624511) · [1216210853624512](https://app.asana.com/1/1213718564226627/project/1213723193546726/task/1216210853624512) · [1216210853624513](https://app.asana.com/1/1213718564226627/project/1213723193546726/task/1216210853624513) · [1216210853624514](https://app.asana.com/1/1213718564226627/project/1213723193546726/task/1216210853624514) |
| Conservation des pièces justificatives et rapport annuel *(épique E6)* | [1216210853624518](https://app.asana.com/1/1213718564226627/project/1213723193546726/task/1216210853624518) · [1216210853624517](https://app.asana.com/1/1213718564226627/project/1213723193546726/task/1216210853624517) · [1216210853624520](https://app.asana.com/1/1213718564226627/project/1213723193546726/task/1216210853624520) |
| Revue de sécurité du dispositif, dont la couverture des accès aux données LCB-FT *(épique E6)* | [1216210853624519](https://app.asana.com/1/1213718564226627/project/1213723193546726/task/1216210853624519) |
| Re-contrôle périodique automatique des associations déjà en relation d'affaires — **porte de mise en production** | *Aucune tâche de suivi identifiée dans le référentiel de projet* |
| Couverture automatisée des deux scénarios de filtrage déclarés au point 7 | *Aucune tâche de suivi identifiée dans le référentiel de projet* |
| Vérification de l'authenticité des pièces d'identification présentées | *Aucune tâche de suivi identifiée dans le référentiel de projet* |

## 9. Situation de ce contrôle dans le dispositif d'ensemble

| Responsabilité du dispositif | État au 13 août 2026 |
|---|---|
| 1 — Désignation des responsables et cloisonnement des accès | Partiellement réalisé — rôle et espace de travail du responsable de la conformité livrés ; désignation formelle et référentiel de déclaration TRACFIN, actes hors plateforme, en cours |
| 2 — Classification des risques et procédures écrites | Partiellement réalisé — infrastructure livrée ; document de classification à approuver par l'organe compétent |
| 3 — Entrée en relation et vérification de l'association | Partiellement réalisé — contrôles E3 livrés ; surveillance périodique à compléter |
| **4 — Gel des avoirs** | **Substantiellement réalisé — registre et sa mise à jour automatique, filtrage à l'entrée en relation (ce document) et au don, journal d'audit, alertes et traitement opérateur livrés. Filtrage périodique automatique non réalisé. Procédure interne de notification DG Trésor non formalisée. Purge à cinq ans non réalisée.** |
| 5 — Surveillance des opérations atypiques et déclaration | Planifié |
| 6 — Conservation des documents et rapport annuel | Planifié |

## 10. Éléments de traçabilité

Le contrôle est enregistré dans l'historique des modifications du logiciel, sur la ligne de
développement dédiée au dispositif LCB-FT (`app-legal-lcb-ft`). Il comprend les migrations de base
de données **V59** (filtrage à l'entrée en relation) et **V63** (catégories de personnes physiques au
dossier et extension des sujets journalisables, avec script de retour arrière), le déclenchement
automatique du filtrage à la validation d'un dossier, les conditions d'approbation décrites au
point 4.4, et les contrôles automatisés décrits au point 7.

Chaque résultat de filtrage — qu'il s'agisse d'une correspondance, d'une absence de correspondance
ou d'une impossibilité de contrôler — est inscrit dans un journal de contrôle de conformité
protégé par une chaîne de hachage. Cette structure garantit qu'aucune entrée ne peut être
modifiée ou supprimée sans que l'intégrité de la chaîne soit compromise. Pour chaque événement
journalisé, l'identifiant de la version du registre consultée est conservé, de sorte qu'un
auditeur puisse vérifier quelle liste était en vigueur au moment du contrôle. La nature du sujet
filtré — association, dirigeant publié, représentant légal saisi au dossier, bénéficiaire effectif —
est conservée avec le résultat.

Les noms des personnes filtrées ne figurent jamais dans les journaux applicatifs : seuls les
journaux de conformité, soumis à des règles d'accès distinctes, contiennent les informations
relatives aux résultats de filtrage.

---

*Document établi le 11 août 2026, état arrêté au 13 août 2026. Une fiche de même nature est produite
pour chaque contrôle du dispositif LCB-FT au fur et à mesure de sa réalisation.*
