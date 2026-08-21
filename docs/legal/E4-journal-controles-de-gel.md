# Journal des contrôles de gel des avoirs

**Fiche de contrôle — dispositif de lutte contre le blanchiment de capitaux et le financement du terrorisme (LCB-FT)**

| | |
|---|---|
| **Entité** | CommonLink |
| **Nature du document** | Fiche descriptive d'un moyen de preuve mis en place, destinée à la commission juridique |
| **Responsabilité concernée** | Gel des avoirs *(responsabilité n° 4 du dispositif interne, épique E4)* |
| **Référence de suivi interne** | Tâche Asana n° `1216210853624508` — « Log immuable de chaque contrôle de gel » |
| **Lien de suivi** | https://app.asana.com/1/1213718564226627/project/1213723193546726/task/1216210853624508 |
| **Priorité assignée** | P0 (la plus élevée) |
| **Date de livraison** | 10 août 2026 |
| **État** | Livré et vérifié par contrôles automatisés. Non encore déployé — la mise en production de la plateforme est conditionnée à l'achèvement de l'ensemble du dispositif. |
| **Rédacteur** | Équipe technique CommonLink |

---

## 1. Avertissement sur la portée de ce document

Ce document décrit **un seul composant** du dispositif de gel des avoirs : le journal dans lequel
chaque contrôle de gel laisse une trace horodatée, inaltérable et enchaînée.

Ce composant est le **substrat de preuve** commun à tous les contrôles de gel. Il ne produit
pas lui-même les contrôles — ceux-ci font l'objet de fiches distinctes, à mesure de leur
réalisation. Le service de vérification qui consulte le registre national est décrit dans la
fiche E4 — « Service de contrôle des mesures de gel des avoirs ».

Le dispositif LCB-FT de CommonLink est organisé en six responsabilités. Cette fiche ne porte que
sur la responsabilité n° 4. Les cinq autres — désignation des responsables, classification des
risques, entrée en relation et vérification de l'association, surveillance des opérations atypiques
et déclaration, conservation des documents et rapport annuel — sont **planifiées ou en cours**.
Aucune conclusion sur la conformité globale du dispositif ne peut être tirée de la présente fiche.

## 2. L'obligation constatée

L'article L.562-2 du Code monétaire et financier impose que les contrôles de gel soient effectués
**sans délai**. Cette obligation de résultat suppose non seulement que les contrôles aient lieu,
mais aussi qu'il soit possible, à tout moment, d'en **apporter la preuve** : qui a été vérifié,
quand, contre quelle version du registre, et avec quel résultat.

CommonLink ne disposait, à la date de ce document, d'**aucun mécanisme de traçabilité** dédié
aux contrôles de gel. Un contrôle effectué sans trace n'est, au regard du régulateur,
juridiquement pas démontrable — même s'il a techniquement eu lieu.

Ce constat a été établi lors de l'audit de conformité LCB-FT mené en août 2026. Il a conduit à
définir et livrer le journal décrit dans la présente fiche, avant de brancher les contrôles de gel
sur les parcours métier.

## 3. Pourquoi ce journal n'existait pas

Lors des premiers développements de CommonLink, les fonctionnalités de traitement des dons ont été
prioritaires sur les fonctionnalités de conformité réglementaire. La traçabilité des contrôles de
gel n'avait pas encore été mise en place.

Par ailleurs, ce journal présente une caractéristique qui l'aurait distingué d'un simple fichier de
journalisation applicative : il doit être **inaltérable** et **vérifiable**. Une ligne de journal
ordinaire peut être modifiée ou supprimée sans laisser de trace. Ce n'est pas acceptable pour un
registre destiné à constituer une preuve au sens légal du terme — ce qui exige une architecture
spécifique, décrite à la section 4.

## 4. Le journal mis en place

### 4.1 Un registre partagé pour tous les contrôles de conformité

Le journal des contrôles de gel est intégré dans un **registre commun à l'ensemble des contrôles
de conformité** de la plateforme. Ce registre centralise toutes les preuves de contrôle — gel des
avoirs, refus d'ouverture de collecte, identification des bénéficiaires effectifs, etc. — dans
une seule table, plutôt que dans des journaux séparés par fonctionnalité.

Ce choix permet d'appliquer les mêmes garanties d'inaltérabilité — décrites aux paragraphes
suivants — à l'ensemble des contrôles, sans devoir les réimplémenter pour chaque nouvelle
fonctionnalité. C'est ce registre commun qui constitue le substrat de preuve.

### 4.2 L'inaltérabilité : aucune modification, aucune suppression

Deux mécanismes indépendants protègent le registre contre toute modification ou suppression :

**Au niveau de la base de données.** Les droits de modification et de suppression ont été
révoqués sur la table du registre. Toute tentative de modifier ou supprimer une ligne est refusée
par la base de données elle-même, sans passer par la couche applicative. Un déclencheur
automatique renforce ce refus comme second garde-fou.

**Au niveau de l'application.** L'ensemble du code de la plateforme est organisé de sorte qu'il
n'existe qu'un seul point d'écriture dans ce registre. Aucune autre partie du logiciel ne peut y
écrire directement. Cette règle est documentée dans le logiciel et fait l'objet d'une vérification
continue.

### 4.3 La chaîne de hachage : toute altération est détectable

Chaque enregistrement contient une **empreinte numérique** (appelée « hash ») calculée à partir
de son propre contenu et de l'empreinte de l'enregistrement précédent. Ce mécanisme, comparable
à celui d'une blockchain, crée une **chaîne** dans laquelle chaque maillon dépend de tous ceux
qui le précèdent.

Conséquence : si un enregistrement était modifié, supprimé, ou si un enregistrement était inséré
entre deux enregistrements existants, la chaîne serait brisée — la vérification de cohérence le
détecterait immédiatement. Cette vérification peut être lancée à tout moment, à la demande.

L'ordre des enregistrements est garanti par un numéro de séquence strictement croissant, attribué
au moment de l'écriture et protégé contre les écritures simultanées. Deux contrôles effectués dans
la même milliseconde restent distinguables et correctement ordonnés.

### 4.4 Les quatre natures d'enregistrement

Chaque contrôle de gel produit un enregistrement de l'une des quatre natures suivantes :

**Contrôle effectué, aucune correspondance.** Le registre national a été consulté pour le sujet
concerné ; aucune entrée n'a atteint le seuil de rapprochement retenu. L'enregistrement indique
la date de publication du registre consulté, le seuil appliqué, et le nombre de correspondances
(zéro).

**Contrôle effectué, correspondance signalée.** Le registre national a été consulté et au moins
une entrée a atteint le seuil. L'enregistrement indique la date de publication du registre, le
seuil, le nombre de correspondances et le score le plus élevé. La correspondance est soumise à
revue humaine ; l'enregistrement constitue la preuve que cette vérification a eu lieu à la date
indiquée.

**Contrôle effectué, correspondance déjà écartée.** Le registre national a été consulté et les
mêmes entrées qu'un contrôle antérieur ont atteint le seuil, mais le responsable de la conformité
avait déjà statué sur ces correspondances en les qualifiant de faux positifs. L'enregistrement
indique la date de publication du registre, le seuil, le nombre de correspondances, le score le
plus élevé et **la ou les alertes dont la clôture fonde la mise à l'écart**, de sorte qu'un auditeur
puisse remonter de la correspondance écartée à la décision qui l'a écartée.

Cette nature est distincte de l'absence de correspondance : le registre a bien signalé des entrées.
Les confondre reviendrait à faire dire au journal qu'un contrôle n'a rien trouvé alors qu'il a
trouvé, et qu'une décision humaine est intervenue.

**Contrôle non effectué en raison d'une indisponibilité.** Le registre national n'a pu être
consulté — téléchargement impossible, registre temporairement indisponible, ou incident technique.
L'enregistrement indique la cause de l'échec.

**L'enregistrement des échecs est obligatoire.** Un journal qui ne montrerait que les contrôles
ayant abouti — c'est-à-dire qui serait silencieux en cas d'échec — serait trompeur : il ne
permettrait pas de distinguer une période sans contrôle d'une période de contrôles tous concluants.
Enregistrer chaque échec garantit que toute lacune dans les contrôles est visible dans le journal.

### 4.5 La protection de l'identité des personnes contrôlées

Pour les enregistrements de type « aucune correspondance » et « correspondance signalée », la
structure des champs est définie de façon à **exclure structurellement toute identité en clair** :
aucun paramètre de nom ou de prénom n'est prévu. Seule une référence interne (un identifiant
technique de l'association ou du don concerné) y figure ; le nom, le prénom et toute autre donnée
nominative ne sont pas conservés dans ces enregistrements.

Pour les enregistrements de type « contrôle non effectué », un champ de description libre permet
d'indiquer la cause de l'échec. Par convention documentée dans le logiciel, ce champ est limité à
des éléments exclusivement techniques — nature de l'erreur, code de réponse HTTP, délai dépassé —
et ne doit pas contenir de données nominatives.

Ce choix réduit la conservation de données personnelles au strict nécessaire pour la démonstration
de conformité : il suffit de savoir qu'un contrôle a eu lieu sur ce sujet, quand, et avec quel
résultat — l'identité complète est déjà conservée dans les dossiers d'identification auxquels la
référence interne renvoie.

## 5. Les éléments de preuve

Le journal est accompagné d'une suite de **contrôles automatisés** réexécutés à chaque
modification du logiciel. Ces contrôles couvrent :

**Les trois natures d'enregistrement** :

| Scénario | Résultat attendu | État |
|---|---|---|
| Contrôle effectué, aucune correspondance | Enregistrement de nature « aucune correspondance » créé | Vérifié par contrôles automatisés |
| Contrôle effectué, correspondance signalée (nombre et score) | Enregistrement de nature « correspondance signalée » créé avec les bons indicateurs | Vérifié par contrôles automatisés |
| Contrôle non effectué — registre indisponible | Enregistrement de nature « contrôle non effectué » créé avec la cause | Vérifié par contrôles automatisés |

**L'absence d'identité dans les enregistrements** :

| Scénario | Résultat attendu | État |
|---|---|---|
| Vérification du contenu de chaque type d'enregistrement | Le contenu ne contient que les champs attendus (date du registre, seuil, résultat, nature, ou cause d'échec) — aucune identité en clair | Vérifié par contrôles automatisés (assertion sur le jeu exact de champs) |

**La reconstituabilité de l'historique** :

| Scénario | Résultat attendu | État |
|---|---|---|
| Deux contrôles effectués sur un sujet A, un contrôle sur un sujet B | L'interrogation de l'historique du sujet A retourne exactement deux enregistrements, tous deux pour ce sujet | Vérifié par contrôles automatisés |
| Un enregistrement de contrôle de gel et un enregistrement d'une autre nature pour le même sujet | L'historique de gel ne retourne que les enregistrements de contrôle de gel | Vérifié par contrôles automatisés |

**L'intégrité de la chaîne** :

| Scénario | Résultat attendu | État |
|---|---|---|
| Série d'enregistrements de contrôle de gel | La vérification d'intégrité de la chaîne conclut à l'absence d'altération | Vérifié par contrôles automatisés |
| Altération directe d'un enregistrement par contournement de l'application | La vérification d'intégrité détecte l'altération et identifie l'enregistrement concerné | Vérifié par contrôles automatisés |

**Le refus de modification et de suppression** :

| Scénario | Résultat attendu | État |
|---|---|---|
| Tentative de modification directe d'un enregistrement | Refus par la base de données | Contrôles automatisés écrits — désactivés dans cet environnement de développement *(voir note)* |
| Tentative de suppression directe d'un enregistrement | Refus par la base de données | Contrôles automatisés écrits — désactivés dans cet environnement de développement *(voir note)* |

> **Note sur les contrôles de refus de modification et de suppression.** Ces contrôles existent et
> sont écrits dans la suite de tests. Ils nécessitent une connexion à une véritable base de données
> PostgreSQL, avec exécution effective des scripts de migration. Ils sont **désactivés**, et le
> refus au niveau de la base de données n'est donc **établi par aucun contrôle automatisé** à ce
> jour. La protection par chaîne de hachage — qui détecte toute altération même en l'absence du
> refus au niveau de la base — est, elle, active et vérifiée automatiquement.
>
> **Mise à jour du 21 août 2026 — ce qui a changé, et ce qui n'a pas changé.** Depuis le
> 19 août 2026, la suite de tests comprend une vérification qui démarre l'application contre une base
> PostgreSQL vide et y exécute la totalité des scripts de migration dans l'ordre, en n'aboutissant
> que si le schéma produit correspond exactement au mapping des entités. Il en résulte deux
> conséquences distinctes, qu'il importe de ne pas confondre :
>
> - **ce qui est désormais établi** — la migration qui révoque les droits de modification et installe
>   le déclencheur d'immuabilité *s'applique* sans erreur sur une base vierge, de même que les
>   migrations relatives aux catégories de personnes physiques et à la table de preuve. Ces scripts
>   ne sont plus seulement vérifiés à la main ;
> - **ce qui reste non établi** — qu'une tentative de modification ou de suppression soit
>   *effectivement refusée*. Aucun contrôle automatisé n'exécute une telle tentative pour en constater
>   le rejet : c'est précisément l'objet des deux contrôles désactivés ci-dessus. Appliquer un script
>   et vérifier l'effet du garde-fou qu'il installe sont deux choses différentes.
>
> Le motif d'environnement invoqué à l'origine pour justifier la désactivation — impossibilité de
> joindre le moteur de conteneurs depuis cette machine — n'est plus exact, puisque la vérification
> décrite ci-dessus s'exécute dans ce même environnement contre une base PostgreSQL réelle. La
> réactivation de ces deux contrôles est donc à reprendre ; elle reste à faire et son résultat n'est
> pas anticipé ici.
>
> **Une réserve subsiste sur la portée du contrôle automatique préalable au déploiement, et elle
> dépasse la seule vérification des migrations.** Depuis le 19 août 2026, la réussite des tests
> conditionne le déploiement de la plateforme. Mais cette porte s'exécute sur un environnement
> dépourvu de moteur de conteneurs et écarte **l'intégralité des contrôles qui en requièrent un** —
> c'est-à-dire tous ceux qui s'exécutent contre une base de données réelle. La vérification des
> migrations en fait partie, comme en font partie plusieurs des contrôles automatisés énumérés au
> présent point 5. Ceux-ci sont exécutés par la suite de tests complète lancée localement, non avant
> chaque déploiement. Les contrôles ne touchant pas la base de données — dont la vérification de la
> chaîne de hachage et la détection d'altération — sont, eux, exécutés par la porte de déploiement.

Les contrôles automatisés décrits ci-dessus ont été exécutés avec succès. Les vérifications
automatisées portant sur le journal de conformité existant et sur le service de contrôle du
registre des avoirs gelés ont également été exécutées séparément, sans régression constatée.

## 6. Ce que ce contrôle ne couvre pas

Cette précision est apportée pour éviter toute lecture extensive de la présente fiche.

**Ce que ce composant ne couvre pas à ce stade :**

- Le **déclenchement des contrôles de gel** sur les parcours métier — ce journal enregistre les
  contrôles qu'on lui soumet, mais ne les déclenche pas lui-même. *Traité depuis par les fiches
  E4 — Contrôle de gel des avoirs à l'entrée en relation d'affaires et E4 — Contrôle de gel des
  avoirs à la création d'un don.*

- La **procédure à suivre en cas de correspondance** — lorsqu'une correspondance est signalée dans
  le journal, la conduite à tenir appartient aux procédures métier. *L'espace de traitement des
  alertes est traité depuis par les fiches E4 — Traitement des alertes de gel et information à la
  Direction générale du Trésor et E4 — Preuve de correspondance au registre de gel.* La procédure
  interne écrite qui encadre ces décisions reste, elle, à rédiger.

- La **fréquence de mise à jour du registre national** — *traitée depuis par la fiche E4 — Mise à
  jour automatique du registre national des mesures de gel des avoirs.*

- La **preuve au niveau de la base de données du refus de modification et de suppression** — les
  contrôles automatisés correspondants existent mais demeurent désactivés, pour le motif exposé
  dans la note du point 5. Ils doivent être réactivés et exécutés avant tout déploiement ; à
  défaut, la garantie repose sur la seule chaîne de hachage, qui détecte l'altération sans
  l'empêcher.

- La **purge à l'échéance de la durée de conservation** — le journal est conçu pour n'être ni
  modifié ni supprimé ; l'articulation entre cette immuabilité et l'effacement exigé au terme du
  délai légal n'est pas réalisée et relève de la responsabilité n° 6.

- La **surveillance des opérations atypiques** et la déclaration de soupçon.

- La **conservation des pièces justificatives** et le rapport annuel d'activité.

Chacun de ces points fait l'objet de travaux planifiés et ordonnancés, avec des dépendances
identifiées entre eux.

**Suivi des travaux restants.** Les éléments ci-dessus qui restent à réaliser sont suivis dans le
référentiel interne de gestion de projet aux références suivantes.

| Travail restant | Lien de suivi |
|---|---|
| Rédaction des procédures LCB-FT écrites, dont la conduite à tenir en cas de correspondance *(prérequis non technique)* | [1216210976716063](https://app.asana.com/1/1213718564226627/project/1213723193546726/task/1216210976716063) |
| Réactivation des contrôles de refus au niveau base de données et exécution de la vérification d'intégrité de la chaîne *(revue de sécurité du dispositif)* | [1216210853624519](https://app.asana.com/1/1213718564226627/project/1213723193546726/task/1216210853624519) |
| Politique de rétention à cinq ans et purge *(épique E6)* | [1216210853624517](https://app.asana.com/1/1213718564226627/project/1213723193546726/task/1216210853624517) |
| Archivage immuable des pièces justificatives *(épique E6)* | [1216210853624518](https://app.asana.com/1/1213718564226627/project/1213723193546726/task/1216210853624518) |
| Surveillance des opérations atypiques et déclaration de soupçon *(épique E5)* | [1216210853624511](https://app.asana.com/1/1213718564226627/project/1213723193546726/task/1216210853624511) · [1216210853624512](https://app.asana.com/1/1213718564226627/project/1213723193546726/task/1216210853624512) · [1216210853624513](https://app.asana.com/1/1213718564226627/project/1213723193546726/task/1216210853624513) · [1216210853624514](https://app.asana.com/1/1213718564226627/project/1213723193546726/task/1216210853624514) |
| Rapport annuel d'activité *(épique E6)* | [1216210853624520](https://app.asana.com/1/1213718564226627/project/1213723193546726/task/1216210853624520) |

## 7. Situation de ce contrôle dans le dispositif d'ensemble

| Responsabilité du dispositif | État au 10 août 2026 |
|---|---|
| 1 — Désignation des responsables et cloisonnement des accès | Planifié |
| 2 — Classification des risques et procédures écrites | Planifié — la rédaction des documents conditionne les développements correspondants |
| 3 — Entrée en relation et vérification de l'association | Partiellement réalisé — le verrou de validation avant collecte est livré ; d'autres composantes sont planifiées |
| **4 — Gel des avoirs** | **Service de vérification livré (fiche E4-service) ; journal des contrôles livré (ce document). Non encore branchés sur les parcours.** |
| 5 — Surveillance des opérations atypiques et déclaration | Planifié |
| 6 — Conservation des documents et rapport annuel | Planifié |

Il est à noter qu'une partie des travaux planifiés est **conditionnée à la remise de documents
non techniques** : la désignation signée des responsables, la classification des risques validée,
et les procédures écrites. Les développements correspondants ne peuvent être engagés avant, sous
peine de transcrire dans le logiciel des règles qui n'auraient pas encore été arrêtées.

## 8. Éléments de traçabilité

La livraison est enregistrée dans l'historique des modifications du logiciel, sur la ligne de
développement dédiée au dispositif LCB-FT (`app-legal-lcb-ft`). Elle comprend :

- l'ajout, au registre de conformité commun existant, des trois natures d'enregistrement
  spécifiques aux contrôles de gel ;
- la méthode d'interrogation permettant de reconstituer l'historique des contrôles pour un sujet
  donné ;
- l'extension du dépôt du registre national interne pour exposer la date de publication du
  dernier instantané ingéré — information nécessaire à chaque enregistrement pour indiquer quelle
  version du registre a été consultée ;
- les contrôles automatisés décrits à la section 5.

Le registre de conformité commun, sa chaîne de hachage, et ses protections contre la modification
et la suppression ont été décrits et livrés lors d'une étape antérieure du dispositif. Ce composant
les réutilise sans les modifier.

---

*Document établi le 10 août 2026, complété le 21 août 2026 (point 5 — portée exacte de la couverture automatisée des migrations). Une fiche de même nature sera produite pour chaque contrôle du
dispositif LCB-FT au fur et à mesure de sa réalisation.*
