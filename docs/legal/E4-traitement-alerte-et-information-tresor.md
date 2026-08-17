# Traitement des alertes de gel et information à la Direction générale du Trésor

**Fiche de contrôle — dispositif de lutte contre le blanchiment de capitaux et le financement du terrorisme (LCB-FT)**

| | |
|---|---|
| **Entité** | CommonLink |
| **Nature du document** | Fiche descriptive d'un contrôle mis en place, destinée à la commission juridique |
| **Responsabilité concernée** | Gel des avoirs — traitement des alertes et information des autorités *(responsabilité n° 4 du dispositif interne, épique E4)* |
| **Référence de suivi interne** | Tâche Asana n° `1216210853624509` — « Écran d'alerte de gel et notification à la DG Trésor » |
| **Lien de suivi** | https://app.asana.com/1/1213718564226627/project/1213723193546726/task/1216210853624509 |
| **Priorité assignée** | P1 |
| **Date de réalisation** | 11 août 2026 — complétée le 17 août 2026 par la notification de la fonction conformité (point 4.6) |
| **État** | Réalisé et vérifié par contrôles automatisés. Non encore déployé — la mise en production de la plateforme est conditionnée à l'achèvement de l'ensemble du dispositif. |
| **Rédacteur** | Équipe technique CommonLink |

---

## 1. Avertissement sur la portée de ce document

Ce document décrit **un seul contrôle**, portant sur le traitement opérationnel des alertes de
correspondance au registre de gel des avoirs et sur la traçabilité de l'information à la Direction
générale du Trésor.

Le dispositif LCB-FT de CommonLink est organisé en six responsabilités, dont celle-ci ne constitue
qu'un élément parmi d'autres au sein de la quatrième. Les autres responsabilités — désignation des
responsables, classification des risques, surveillance des opérations atypiques et déclaration,
conservation des documents et rapport annuel — sont **planifiées et non encore intégralement
réalisées** à la date de ce document. Leur état d'avancement fait l'objet d'un suivi distinct.

Aucune conclusion sur la conformité globale du dispositif ne peut être tirée de la présente fiche.

## 2. La situation prise en charge

Le contrôle des avoirs gelés, mis en place par les fiches précédentes (E4 — vérification à
l'adhésion et E4 — vérification au don), génère des **alertes** dès qu'une correspondance est
détectée entre un nom contrôlé et une entrée du registre de gel. Sans écran de traitement dédié,
ces alertes restaient enregistrées en base de données sans possibilité pour le responsable de
conformité d'agir sur elles.

L'obligation légale exige plus qu'une détection : en cas de correspondance avérée, l'organisme doit
**geler les fonds** et en **informer sans délai la Direction générale du Trésor** (DG Trésor).
Cette obligation pèse sur une personne physique désignée — le responsable de conformité — et non
sur l'application.

La présente fiche porte sur le dispositif mis en place pour permettre à cette personne d'exercer
son rôle : consulter les alertes, les prendre en charge, prononcer une décision motivée, et
conserver la preuve de la notification à la DG Trésor lorsque celle-ci a été effectuée.

## 3. Contexte et cadre légal

Le gel des avoirs est une obligation immédiate et non discrétionnaire. Lorsqu'une plateforme
intermédiaire détecte une correspondance lors d'un contrôle, elle doit :

1. bloquer immédiatement l'opération ou le flux de fonds concerné ;
2. prendre une décision sur la nature de la correspondance (faux positif, situation légitime,
   correspondance avérée) ;
3. en cas de correspondance avérée, informer la DG Trésor sans délai par tout moyen laissant
   une trace (courriel sécurisé, courrier recommandé, portail dédié selon les instructions en
   vigueur) ;
4. conserver la preuve de cette information.

La plateforme assure les étapes 1 (blocage immédiat lors du contrôle) et 4 (conservation de la
preuve). Les étapes 2 et 3 sont des **gestes humains**, accomplis par le responsable de conformité
hors application. L'application fournit uniquement l'espace de travail pour les réaliser et la
trace numérique de leur réalisation.

**Procédure interne de notification :** à la date de ce document, CommonLink ne dispose pas de
procédure interne formalisée pour la notification à la DG Trésor. Cette situation a été
expressément qualifiée de « Non applicable — on part du cadre légal standard » lors de l'examen
des prérequis à ce développement. En l'absence d'un référentiel interne, le responsable de
conformité applique directement les instructions publiées par la DG Trésor. Ce point doit être
régularisé avant la mise en production.

## 4. Le contrôle mis en place

Un **espace de travail réservé** au responsable de conformité (rôle `COMPLIANCE_OFFICER`) a été
créé, distinct du tableau de bord de l'administrateur et des interfaces association et donateur. Il
n'est pas accessible aux rôles tiers.

### 4.1 Cycle de vie d'une alerte

Chaque alerte suit un cycle de vie irréversible à trois états :

| État | Signification |
|---|---|
| `PENDING` | Alerte créée, non encore examinée |
| `IN_REVIEW` | Prise en charge par un responsable identifié |
| `CLOSED` | Décision prononcée et enregistrée |

Le retour en arrière est impossible : une alerte clôturée ne peut pas être rouverte. Si une
nouvelle correspondance survient sur le même sujet après clôture, une nouvelle alerte est créée.

Le passage de `PENDING` à `IN_REVIEW` suppose que la fonction conformité ait connaissance de
l'alerte. Le point 4.6 décrit la notification qui l'en informe, et les limites de celle-ci.

Les seules transitions autorisées (`PENDING → IN_REVIEW`, `IN_REVIEW → CLOSED`) sont imposées
par le moteur de transition d'état côté serveur ; toute autre combinaison est rejetée avec une
erreur explicite.

### 4.2 Décision motivée obligatoire

La clôture d'une alerte exige :

- un **choix de décision** parmi trois valeurs prédéfinies :
  - `LEGITIMATE` — correspondance non avérée, situation légitime ;
  - `SUSPICIOUS` — correspondance avérée ;
  - `FALSE_POSITIVE` — faux positif, erreur d'identification ;
- une **motivation en texte libre**, obligatoire quelle que soit la décision. Une motivation vide
  ou constituée de blancs est refusée par le serveur.

### 4.3 Effet de la décision sur le dossier contrôlé

Le contrôle de gel est rejoué à chaque tentative d'approbation d'un dossier. Une décision qui ne
serait pas prise en compte par ce contrôle resterait sans effet : les mêmes entrées du registre
seraient retrouvées, le dossier refusé de nouveau, et une alerte en double ouverte à chaque essai.

Une clôture `FALSE_POSITIVE` **lève** donc les correspondances concernées :

- la levée porte sur les couples **(nom criblé, entrée du registre)** que l'écran de décision
  affichait — jamais sur la personne ou l'association en tant que telle ;
- elle est bornée dans le temps par l'enregistrement `ALERT_CLOSED` : les correspondances
  constatées après la décision n'en bénéficient pas ;
- une entrée du registre non couverte par la décision — mise à jour du registre national,
  dirigeant nouvellement déclaré — rebloque le dossier et ouvre une nouvelle alerte ;
- chaque mise à l'écart est enregistrée au journal (voir fiche
  `E4-journal-controles-de-gel.md` §4.4), jamais opérée en silence.

Les décisions `SUSPICIOUS` et `LEGITIMATE` ne lèvent rien. Une correspondance avérée interdit
l'entrée en relation d'affaires : la mesure de gel est d'application stricte (art. L.562-1 et s.
CMF) et aucune appréciation de légitimité ne peut y déroger.

**Seule la décision la plus récente sur un sujet fait foi.** Lorsqu'un sujet a fait l'objet de
plusieurs alertes successivement clôturées, la dernière en date — au sens du journal — détermine
seule si une levée s'applique. Ne pas lever et bloquer ne sont pas la même chose : si une décision
défavorable se contentait de ne rien lever, le faux positif qui l'a précédée continuerait d'écarter
une correspondance que le responsable de la conformité a depuis confirmée.

### 4.4 Traçabilité de la notification à la Direction générale du Trésor

**La notification à la DG Trésor est un geste humain accompli hors application. Aucune
transmission automatique n'existe ni n'est prévue.** L'application ne dispose d'aucun moyen de
contacter la DG Trésor et n'en aura pas.

Lorsque la décision est `SUSPICIOUS`, trois champs supplémentaires deviennent **obligatoires** et
doivent être renseignés par le responsable de conformité après avoir effectué la notification hors
application :

| Champ | Contenu attendu |
|---|---|
| Date de notification | Horodatage de l'envoi à la DG Trésor |
| Moyen utilisé | Mode de transmission (courriel sécurisé, courrier recommandé…) |
| Référence de la notification | Identifiant attribué à l'envoi (numéro de message, accusé de réception…) |

Ces champs sont refusés vides ou absents pour une décision `SUSPICIOUS`. Pour les décisions
`LEGITIMATE` et `FALSE_POSITIVE`, ils restent facultatifs — aucune obligation d'informer la DG
Trésor ne s'applique dans ces cas.

### 4.5 Journal d'audit

Chaque changement d'état d'une alerte donne lieu à une entrée dans le **journal d'audit
horodaté** de la plateforme (`compliance_audit_log`), identifiant :

- l'événement (`ALERT_OPENED`, `ALERT_IN_REVIEW`, `ALERT_CLOSED`) ;
- l'alerte concernée ;
- l'identifiant de l'acteur pour les transitions humaines (prise en charge et clôture) ; l'ouverture est générée automatiquement par le système et ne comporte pas d'acteur ;
- pour la clôture : la décision retenue et, si applicable, les informations de notification DG Trésor.

Ce journal est protégé par une chaîne de hachage continue ; toute altération rétroactive est
détectable. Il constitue la trace opposable des décisions prises.

### 4.6 Notification de la fonction conformité à l'ouverture d'une alerte

*Contrôle complémentaire livré le 17 août 2026, postérieurement au reste de la présente fiche.*

**La situation corrigée.** Le cycle de vie décrit au point 4.1 supposait un lecteur : une alerte
créée reste `PENDING` jusqu'à ce qu'un responsable ouvre l'espace de travail. Pour une alerte née à
l'adhésion, cette hypothèse est vérifiée — un opérateur instruit le dossier au moment même où le
criblage s'exécute et voit le refus immédiatement. Pour une alerte née d'un **don**, elle ne l'est
pas : le donateur est refusé par un message neutre, l'association n'est pas informée, et personne
n'est devant un écran. L'alerte pouvait donc rester en attente aussi longtemps que nul n'ouvrait le
back-office de sa propre initiative.

**Le mécanisme.** À la création d'une alerte de gel portant sur un donateur, l'application adresse
un courriel à la **boîte de la fonction conformité** (`compliance@common-link.org`). Le message
indique la référence de l'alerte, sa sévérité, et un lien direct vers l'écran de traitement.

**Boîte de fonction et non personne nommée.** L'adresse destinataire est celle d'une fonction, non
d'un titulaire. Une alerte de gel doit continuer de parvenir à la fonction conformité indépendamment
des changements d'effectif, sans qu'une reconfiguration technique soit nécessaire.

**Aucune donnée d'identité dans le message.** Le courriel ne comporte ni le nom criblé, ni l'entrée
du registre correspondante, ni le score de rapprochement — ni dans son corps, ni dans son objet. Un
objet de courriel apparaît dans les bandeaux de notification, les prévisualisations et les
sauvegardes de messagerie, qui ne sont pas des canaux à accès contrôlé. Le détail de la
correspondance reste consultable uniquement sur l'écran d'alerte, après authentification, selon les
restrictions du point 5.2. Cette règle est la même que celle appliquée aux journaux applicatifs par
les services de criblage.

**Exactitude du message.** Le courriel affirme que le don a été refusé et qu'aucun paiement n'a été
créé. Cette affirmation est exacte pour l'alerte de gel au don : le refus intervient avant tout
appel au prestataire de paiement. Elle ne le serait pas pour une alerte de nature différente — une
règle d'atypicité, par construction, se déclenche sur un don déjà encaissé. L'extension de ce
mécanisme à d'autres natures d'alerte exige donc un texte propre à chacune, et non le seul
élargissement du filtre.

**Envoi au mieux, alerte enregistrée d'abord.** Le courriel part après enregistrement définitif de
l'alerte : aucun message ne peut annoncer une alerte qui n'aurait finalement pas été enregistrée.
Réciproquement, une défaillance du serveur de messagerie n'empêche ni le refus du don, ni la création
de l'alerte, ni son inscription au journal — elle est enregistrée comme incident. L'information n'est
donc jamais perdue ; seule sa diffusion peut l'être, et cet écart est repris au point 6.

**Envoi unique par alerte ouverte.** Le message est émis à la création de l'alerte, jamais lors des
tentatives ultérieures portant sur le même sujet tant que l'alerte reste ouverte : la fonction
conformité n'est pas sollicitée deux fois pour une même correspondance en attente de décision.

## 5. Les éléments de preuve

### 5.1 Contrôles automatisés côté serveur

Trois contrôles automatisés ont été ajoutés au patrimoine de tests, à l'occasion de ce développement :

| Scénario | Résultat attendu | Vérifié |
|---|---|---|
| Clôture d'une alerte avec une motivation vide | Refus (erreur de validation) | Oui |
| Clôture `SUSPICIOUS` sans champs DG Trésor | Refus (erreur de validation) | Oui |
| Clôture `SUSPICIOUS` avec tous les champs DG Trésor renseignés | Accepté — les trois champs DG Trésor sont correctement assignés à l'alerte avant transmission à la couche de persistance | Oui |

Ces contrôles sont exécutés à chaque modification du logiciel. Ils vérifient les règles au niveau
du service métier, indépendamment de l'interface.

Six contrôles supplémentaires couvrent la notification décrite au point 4.6 :

| Scénario | Résultat attendu | Vérifié |
|---|---|---|
| Alerte de gel sur un donateur | Un courriel unique est adressé à la boîte de la fonction conformité, portant un lien exploitable vers l'écran de traitement | Oui |
| Adresse de la plateforme comportant une barre oblique finale | Le lien transmis reste valide (pas de double séparateur) | Oui |
| Alerte de gel née de l'adhésion | Aucun courriel — un opérateur est déjà devant le dossier (point 4.6) | Oui |
| Alerte de criblage impossible et alerte d'échec de synchronisation | Aucun courriel — ces natures d'alerte ne relèvent pas de ce mécanisme | Oui |
| Boîte destinataire non configurée | Aucun envoi, incident enregistré, aucune interruption du traitement | Oui |
| Défaillance du serveur de messagerie | L'exception est absorbée : ni le refus du don, ni l'alerte, ni le journal ne sont affectés | Oui |

Ces six contrôles vérifient le comportement du composant de notification isolément, sans serveur de
messagerie réel. **Ils n'établissent pas que le courriel est effectivement délivré** — voir point 6.

### 5.2 Restrictions d'accès

L'espace de travail est réservé au rôle `COMPLIANCE_OFFICER` par la configuration de sécurité de
l'application (`SecurityConfig`). Les requêtes non authentifiées vers ces routes produisent une
réponse 401 — comportement vérifié par le test `SecurityConfigTest` existant. La restriction par
rôle (rejet d'un `CURATOR` ou d'un autre rôle non habilité) est une propriété architecturale de
la configuration : les routes `/api/compliance/**` sont configurées pour exiger
`ROLE_COMPLIANCE_OFFICER` exclusivement. **Ce point n'est pas couvert par un contrôle automatisé
de niveau contrôleur dans la suite de tests actuelle** — l'infrastructure de test de la plateforme
ne prend pas en charge les vérifications d'autorisation par rôle à ce niveau. La garantie repose
sur la configuration.

### 5.3 Traçabilité du journal

Le journal d'audit est alimenté pour chaque transition d'état. L'identifiant de l'acteur est
enregistré pour les transitions humaines (prise en charge et clôture) ; l'entrée générée à
l'ouverture porte un acteur nul, l'ouverture étant automatique. La présence de cette entrée
automatique est vérifiée par les tests de service.

**Affichage de l'historique de contrôle :** l'interface de détail présente l'historique des
événements de criblage qui ont généré l'alerte (consultation de liste des avoirs gelés). La colonne
événement affiche l'identifiant technique de l'événement (`FREEZE_SCREENING_HIT`, etc.) sans
traduction, conformément à la convention d'affichage interne des outils destinés au responsable
de conformité.

## 6. Ce que ce contrôle ne couvre pas

Cette précision est apportée pour éviter toute lecture extensive de la présente fiche.

**Transmission à la DG Trésor.** L'application ne transmet rien à la DG Trésor. Elle enregistre
uniquement la preuve que le responsable de conformité a effectué cette transmission hors
application. La responsabilité de l'envoi effectif et de son délai appartient entièrement au
responsable de conformité désigné.

**Procédure interne de notification.** Aucune procédure interne formalisée ne définit à ce jour
les modalités de contact de la DG Trésor (canal à privilégier, interlocuteur, délai cible). Cette
lacune doit être comblée avant la mise en production, et une fiche dédiée doit en rendre compte.

**Déclaration TRACFIN.** L'obligation de déclaration de soupçon auprès de TRACFIN est distincte
de l'obligation d'information de la DG Trésor. Elle n'est pas couverte par ce contrôle.

**Gel effectif des fonds.** Le blocage des opérations est assuré par les contrôles décrits dans
les fiches E4 précédentes (adhésion, don). La présente fiche ne porte que sur le traitement de
l'alerte après détection.

**Vérification de l'identité de l'interlocuteur DG Trésor.** L'application ne vérifie pas que les
coordonnées DG Trésor saisies correspondent à un destinataire réel. Cette vérification incombe au
responsable de conformité.

**Cloisonnement des dossiers par déclarant.** L'espace de travail est réservé au rôle de
responsable de la conformité, sans distinction entre plusieurs titulaires. Lorsque plusieurs
déclarants coexisteront, chacun ne devra visualiser que ses propres dossiers ; cette exigence
n'est pas couverte et suppose que le back-office de revue des dossiers existe au préalable.

**Traçabilité de la simple consultation d'une alerte.** L'identité du responsable de la conformité
est enregistrée lors de la prise en charge et de la clôture, non lors de la consultation. Cet écart
est repris par la fiche *E4 — Preuve de correspondance au registre de gel*.

**Preuve de délivrance de la notification.** La notification du point 4.6 est un envoi au mieux.
L'application enregistre qu'elle a remis le message au serveur de messagerie, non que le message est
parvenu à destination, ni qu'il a été lu. Aucun accusé de réception n'est exigé ni conservé. La
notification est un confort opérationnel : la trace opposable de l'ouverture de l'alerte demeure
l'entrée `ALERT_OPENED` du journal d'audit, non le courriel.

**Absence de relance sur alerte non traitée.** Aucun mécanisme ne relance la fonction conformité, ni
n'escalade, lorsqu'une alerte demeure en `PENDING` au-delà d'un délai donné. Un courriel manqué —
défaillance de messagerie, filtrage en indésirable, boîte non surveillée — ramène donc la détection
de l'alerte à la consultation spontanée du back-office, c'est-à-dire à la situation antérieure au
point 4.6. Un délai maximal de traitement et sa relance restent à définir, ce qui relève de la
procédure interne avant toute traduction technique.

**Notification limitée aux alertes de gel sur un donateur.** Les autres natures d'alerte
n'engendrent aucun courriel, pour les raisons exposées au point 4.6. En particulier, une alerte de
criblage impossible — c'est-à-dire un contrôle qui n'a pas pu être mené à son terme — ne notifie
personne, alors qu'un contrôle impossible n'est pas un contrôle favorable. Cet écart est à combler.

**Suivi des travaux restants.** Les éléments ci-dessus qui restent à réaliser sont suivis dans le
référentiel interne de gestion de projet aux références suivantes.

| Travail restant | Lien de suivi |
|---|---|
| Procédure interne de notification à la DG Trésor — **à régulariser avant la mise en production** | [1216210853624509](https://app.asana.com/1/1213718564226627/project/1213723193546726/task/1216210853624509) · [1216210976716063](https://app.asana.com/1/1213718564226627/project/1213723193546726/task/1216210976716063) |
| Désignation formelle du responsable de la conformité, sur qui pèse l'obligation de notifier *(prérequis non technique)* | [1216210976716057](https://app.asana.com/1/1213718564226627/project/1213723193546726/task/1216210976716057) |
| Déclaration de soupçon TRACFIN — inscription ERMES et numéro de télédéclarant *(prérequis non technique)* | [1216210976716058](https://app.asana.com/1/1213718564226627/project/1213723193546726/task/1216210976716058) |
| Modèle de dossier de soupçon pré-remplissant les champs ERMES *(épique E5)* | [1216210853624514](https://app.asana.com/1/1213718564226627/project/1213723193546726/task/1216210853624514) |
| Documentation de l'interdiction de divulgation et du devoir d'abstention *(prérequis non technique)* | [1216210853624515](https://app.asana.com/1/1213718564226627/project/1213723193546726/task/1216210853624515) |
| Back-office de revue des alertes, préalable au cloisonnement *(épique E5)* | [1216210853624513](https://app.asana.com/1/1213718564226627/project/1213723193546726/task/1216210853624513) |
| Cloisonnement des dossiers de soupçon par déclarant *(épique E1)* | [1216210976716060](https://app.asana.com/1/1213718564226627/project/1213723193546726/task/1216210976716060) |
| Revue de sécurité du dispositif, dont la couverture des accès par rôle non vérifiée au point 5.2 *(épique E6)* | [1216210853624519](https://app.asana.com/1/1213718564226627/project/1213723193546726/task/1216210853624519) |
| Traçabilité de la simple consultation d'une alerte | *Aucune tâche de suivi identifiée dans le référentiel de projet* |
| Délai maximal de traitement d'une alerte et relance sur alerte non traitée — **prérequis de procédure avant traduction technique** | *Aucune tâche de suivi identifiée dans le référentiel de projet* |
| Notification de la fonction conformité sur alerte de criblage impossible | *Aucune tâche de suivi identifiée dans le référentiel de projet* |
| Surveillance de la boîte de la fonction conformité et de son filtrage anti-indésirable *(prérequis non technique)* | *Aucune tâche de suivi identifiée dans le référentiel de projet* |

## 7. Situation de ce contrôle dans le dispositif d'ensemble

| Responsabilité du dispositif | État au 11 août 2026 |
|---|---|
| 1 — Désignation des responsables et cloisonnement des accès | Planifié |
| 2 — Classification des risques et procédures écrites | Planifié — la rédaction des documents conditionne les développements correspondants |
| 3 — Entrée en relation et vérification de l'association | Partiellement réalisé (contrôles E3 livrés) |
| **4 — Gel des avoirs** | **Partiellement réalisé — registre, contrôles à l'adhésion et au don, journal d'audit, alerte et traitement opérateur livrés. Procédure interne de notification DG Trésor non formalisée.** |
| 5 — Surveillance des opérations atypiques et déclaration | Planifié |
| 6 — Conservation des documents et rapport annuel | Planifié |

## 8. Éléments de traçabilité

Le développement est enregistré dans l'historique des modifications du logiciel, sur la branche
dédiée au dispositif LCB-FT (`app-legal-lcb-ft`). Il comprend :

- la migration de base de données ajoutant les colonnes de traçabilité DG Trésor à la table
  `compliance_alert` (migration V62) ;
- les routes API réservées au responsable de conformité (lecture des alertes, prise en charge,
  clôture avec décision et traçabilité) ;
- l'interface de traitement des alertes dans la section `/compliance` de la plateforme, accessible
  uniquement au responsable de conformité ;
- les contrôles automatisés décrits au point 5 ;
- pour la notification du point 4.6 : l'événement émis à la création d'une alerte, le composant
  d'envoi qui y souscrit, la méthode d'envoi ajoutée au service de messagerie et ses deux
  implémentations (envoi réel et substitut de développement), ainsi que le paramètre de
  configuration portant l'adresse de la boîte de la fonction conformité.

Les règles métier — motivation obligatoire, champs DG Trésor obligatoires en cas de correspondance
avérée, irréversibilité de la clôture, caractère humain et hors-application de la notification à
la DG Trésor, absence de donnée d'identité dans la notification et exactitude de son contenu au
regard de la nature de l'alerte — sont documentées directement dans le code source, à l'endroit où
elles s'appliquent.

---

*Document établi le 11 août 2026, complété le 17 août 2026. Une fiche de même nature est produite
pour chaque contrôle du dispositif LCB-FT au fur et à mesure de sa réalisation.*
