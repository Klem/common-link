# Création du rôle de responsable de la conformité LCB-FT et cloisonnement de son accès

**Fiche de contrôle — dispositif de lutte contre le blanchiment de capitaux et le financement du terrorisme (LCB-FT)**

| | |
|---|---|
| **Entité** | CommonLink |
| **Nature du document** | Fiche descriptive d'un contrôle mis en place, destinée à la commission juridique |
| **Responsabilité concernée** | Désignation des responsables et cloisonnement des accès *(responsabilité n° 1 du dispositif interne, épique E1)* |
| **Référence de suivi interne** | Tâche Asana n° `1216210976716059` — « Ajouter le rôle RBAC 'compliance_officer' (backend) » |
| **Lien de suivi** | https://app.asana.com/1/1213718564226627/project/1213723193546726/task/1216210976716059 |
| **Priorité assignée** | P0 (la plus élevée) |
| **Date de réalisation** | 6 août 2026 |
| **État** | Réalisé et vérifié par contrôles automatisés, y compris l'espace de travail complet du responsable (tableau de bord, derniers contrôles des registres, journal d'audit récent). Non encore déployé — la mise en production de la plateforme est conditionnée à l'achèvement de l'ensemble du dispositif. |
| **Rédacteur** | Équipe technique CommonLink |

---

## 1. Avertissement sur la portée de ce document

Ce document décrit **un seul contrôle**, portant sur un seul point du dispositif interne LCB-FT.

Le dispositif de CommonLink est organisé en six responsabilités. Celle-ci concerne la première :
la désignation d'un responsable identifiable et le cloisonnement de son espace d'accès. Les cinq
autres — classification des risques, vérification de l'identité des associations, gel des avoirs,
surveillance des opérations atypiques et déclaration, conservation des documents et rapport annuel
— sont **planifiées et non encore réalisées** à la date de ce document.

**Ce document ne permet pas de conclure que le dispositif LCB-FT est complet ou opérationnel.**
Il constitue une pièce d'un dossier de preuve en cours de constitution.

## 2. L'objet de ce contrôle

Les obligations LCB-FT imposent aux plateformes de financement participatif de désigner un
responsable de la conformité disposant d'un accès dédié aux données sensibles : dossiers de
soupçon, alertes de surveillance, registre des déclarations. Cet accès doit être **strictement
réservé** à ce rôle — ni les associations bénéficiaires, ni les donateurs, ni les opérateurs de
modération ne doivent pouvoir y accéder.

Sans un tel cloisonnement technique, des données couvertes par le secret de la déclaration de
soupçon pourraient être atteintes par des utilisateurs non habilités, ce qui constituerait une
violation des obligations de confidentialité prévues par le code monétaire et financier.

## 3. La mesure mise en place

La plateforme reconnaît désormais un nouveau rôle interne : le **responsable de la conformité**.
Ce rôle est distinct des trois rôles existants (donateur, association, opérateur de modération).

Deux caractéristiques importantes de cette mise en œuvre :

**L'accès est exclusif et non cumulable.** Une association ou un donateur ne peut pas obtenir les
droits du responsable de la conformité — les deux périmètres sont cloisonnés par construction.
Un opérateur de modération ne peut pas non plus accéder à l'espace de conformité, et
réciproquement : le responsable de la conformité n'accède pas à l'espace de modération. Un
administrateur de plateforme ne peut pas non plus accéder à l'espace de conformité. Aucun des
rôles existants ne cumule les droits de conformité avec d'autres droits.

**Chaque accès laisse une trace.** Tout accès à l'espace de conformité produit automatiquement
une trace applicative contenant l'identifiant de l'utilisateur et la ressource consultée.
Cette trace n'enregistre pas le contenu des échanges (les dossiers de soupçon contenant des données
sensibles), mais uniquement le fait de l'accès et son auteur.

### 3.1 Complément du 20 août 2026 — une voie de contournement du cloisonnement, corrigée

Ce complément est porté à la connaissance de la commission parce qu'il contredit, pour la période
concernée, l'affirmation centrale de la présente fiche : que l'accès à l'espace de conformité est
réservé au seul responsable de la conformité.

**Ce qui a été constaté.** Un audit de sécurité conduit le 20 août 2026 a établi que le rôle
demandé lors de la création d'un compte n'était contraint par aucune règle. Les trois voies
publiques de création de compte — inscription par mot de passe, lien de connexion par courriel,
inscription par compte Google — acceptaient indifféremment n'importe lequel des rôles reconnus par
la plateforme, y compris celui de responsable de la conformité et celui d'opérateur de modération.
Une personne non authentifiée, disposant d'une simple adresse de courriel valide, pouvait donc
s'attribuer elle-même le rôle de responsable de la conformité et accéder en lecture à l'intégralité
de l'espace de conformité : alertes de gel des avoirs, identités des donateurs criblés, résultats
des consultations de registres et journal d'audit LCB-FT.

Le cloisonnement décrit au point 3 n'était pas en défaut : les règles de refus par rôle
fonctionnaient et sont vérifiées par les contrôles automatisés du point 4. La faille était en amont,
au moment de l'attribution du rôle — le contrôle vérifiait qui portait le rôle, non comment ce rôle
avait été obtenu.

**Ce qui limite la portée du constat.** La plateforme n'est pas déployée en production et
n'héberge aucune donnée réelle : aucun don n'a été encaissé, aucun dossier d'association réel n'a
été instruit, aucune alerte de gel réelle n'a été émise. Il n'existait donc, sur la période, aucune
donnée de conformité à exposer. Aucune exploitation n'a par ailleurs été constatée.

**Ce qui a été corrigé.** L'ensemble des rôles est désormais scindé en deux catégories, et seuls
les rôles de donateur et d'association peuvent être demandés depuis une voie publique de création
de compte. Le refus est appliqué en deux endroits indépendants — validation du formulaire reçu et
garde dans le service d'authentification — conformément au principe interne selon lequel toute
action de l'utilisateur doit être considérée comme rejouable en contournant l'interface. Les comptes
de responsable de la conformité et d'opérateur de modération ne peuvent être créés que par la
procédure d'amorçage réservée à l'exploitation, hors de toute requête publique.

**Une limite subsiste sur la remédiation.** Les jetons d'accès déjà émis ne portent pas
d'identifiant unitaire et la plateforme ne dispose d'aucun mécanisme de révocation d'un jeton
individuel. Si un compte illégitime devait être découvert, sa suppression ne suffirait pas :
la remédiation complète suppose de supprimer le compte, de révoquer les jetons de renouvellement
et de faire tourner le secret de signature. Ce point est un constat d'audit distinct, non corrigé à
ce jour.

Le constat, son chemin d'exploitation détaillé et l'état de sa remédiation figurent au rapport
`docs/security/audit-2026-08-20.md` (constat C1), qui fait autorité sur ce point. La présente fiche
n'en reprend que ce qui touche au cloisonnement du rôle de responsable de la conformité.

## 4. Les éléments de preuve

La mise en œuvre est accompagnée de **sept contrôles automatisés** ajoutés au patrimoine de tests
de la plateforme. Ces contrôles sont réexécutés à chaque modification du logiciel.

| Scénario vérifié | Résultat attendu | État |
|---|---|---|
| Le responsable de la conformité accède à l'espace de conformité | Accès accordé | Vérifié — contrôle automatisé |
| Un gestionnaire d'association tente d'accéder à l'espace de conformité | Accès refusé | Vérifié — contrôle automatisé |
| Un donateur tente d'accéder à l'espace de conformité | Accès refusé | Vérifié — contrôle automatisé |
| Un opérateur de modération tente d'accéder à l'espace de conformité | Accès refusé | Vérifié — contrôle automatisé |
| Un administrateur de plateforme tente d'accéder à l'espace de conformité | Accès refusé | Vérifié — contrôle automatisé |
| Le responsable de la conformité accède à l'espace de conformité | Une trace est enregistrée, comportant l'identifiant de l'utilisateur et la ressource consultée, en un seul exemplaire par accès | Vérifié — contrôle automatisé |
| Le responsable de la conformité tente d'accéder à l'espace de modération | Accès refusé | Vérifié — contrôle automatisé |
| Une voie publique de création de compte demande le rôle de responsable de la conformité | Demande refusée | Vérifié — contrôle automatisé *(ajouté le 20 août 2026)* |
| Une voie publique de création de compte demande le rôle d'opérateur de modération | Demande refusée | Vérifié — contrôle automatisé *(ajouté le 20 août 2026)* |

Les quatre scénarios de refus (association, donateur, modérateur, administrateur) sont vérifiés
**séparément** : il n'est pas supposé qu'un refus pour l'un vaut refus pour les autres.
Le septième scénario — refus d'accès à l'espace de modération pour le responsable de la
conformité — constitue la preuve que les deux périmètres sont bien étanches.

L'ensemble des contrôles automatisés de la plateforme a été exécuté après la réalisation, sans
régression sur les fonctionnements existants.

## 5. Ce que ce contrôle ne couvre pas

Cette précision est apportée pour éviter toute lecture extensive de la présente fiche.

**La désignation d'une personne physique n'est pas encore effectuée.** Ce contrôle crée le
contenant technique — un rôle reconnu par la plateforme — mais ne désigne pas le titulaire. La
désignation formelle du responsable de la conformité, du déclarant et du correspondant auprès des
autorités est une démarche non technique distincte, actuellement en cours. L'espace de conformité
ne peut être utilisé qu'une fois cette désignation effectuée et le compte correspondant créé sur
la plateforme.

**Le contenu fonctionnel de l'espace de conformité relève d'autres fiches.** Le présent contrôle
porte sur le point d'entrée technique et son cloisonnement, non sur les écrans qui y sont logés.
Le tableau de bord, le traitement des alertes de gel et l'accès au journal d'audit ont été
construits ultérieurement — voir le point 7 de la présente fiche et les fiches de l'épique E4.

Ce contrôle ne couvre pas non plus :

- la **traçabilité immuable** des actions de conformité — journal d'audit à écriture unique ;
  *traitée depuis par la fiche* E4 — Journal des contrôles de gel des avoirs ;
- le **cloisonnement des dossiers par déclarant**, lorsque plusieurs déclarants coexistent — ce
  point reste à réaliser, et suppose que le back-office de revue des dossiers existe au préalable ;
- l'**interface de gestion des désignations de déclarants** à destination du responsable de la
  conformité — le registre de stockage existe (fiche E1 — Référentiel des personnes habilitées à
  déclarer), son écran de gestion reste à construire ;
- la **classification des risques** — *l'infrastructure est livrée depuis, par les fiches E2* ; le
  document de classification reste à faire adopter par l'organe compétent ;
- les **mesures de gel des avoirs** — *traitées depuis par les fiches de l'épique E4* ;
- la **surveillance des opérations atypiques** et le **rapport annuel** — non engagés.
- la **révocation d'un jeton d'accès individuel** — la plateforme n'en dispose pas ; l'exclusion
  immédiate d'un compte de conformité compromis suppose une rotation du secret de signature, qui
  invalide toutes les sessions en cours *(voir le point 3.1 et le rapport d'audit du 20 août 2026)*.

**Suivi des travaux restants.** Les éléments ci-dessus qui restent à réaliser sont suivis dans le
référentiel interne de gestion de projet aux références suivantes.

| Travail restant | Lien de suivi |
|---|---|
| Désignation du responsable LCB-FT, du déclarant et du correspondant TRACFIN *(prérequis non technique)* | [1216210976716057](https://app.asana.com/1/1213718564226627/project/1213723193546726/task/1216210976716057) |
| Cloisonnement des dossiers de soupçon par déclarant | [1216210976716060](https://app.asana.com/1/1213718564226627/project/1213723193546726/task/1216210976716060) |
| Adoption du document de classification des risques *(prérequis non technique)* | [1216210976716062](https://app.asana.com/1/1213718564226627/project/1213723193546726/task/1216210976716062) |
| Rédaction des procédures LCB-FT écrites *(prérequis non technique)* | [1216210976716063](https://app.asana.com/1/1213718564226627/project/1213723193546726/task/1216210976716063) |
| Surveillance des opérations atypiques et déclaration de soupçon *(épique E5)* | [1216210853624511](https://app.asana.com/1/1213718564226627/project/1213723193546726/task/1216210853624511) · [1216210853624512](https://app.asana.com/1/1213718564226627/project/1213723193546726/task/1216210853624512) · [1216210853624513](https://app.asana.com/1/1213718564226627/project/1213723193546726/task/1216210853624513) · [1216210853624514](https://app.asana.com/1/1213718564226627/project/1213723193546726/task/1216210853624514) |
| Conservation des documents et rapport annuel *(épique E6)* | [1216210853624518](https://app.asana.com/1/1213718564226627/project/1213723193546726/task/1216210853624518) · [1216210853624517](https://app.asana.com/1/1213718564226627/project/1213723193546726/task/1216210853624517) · [1216210853624520](https://app.asana.com/1/1213718564226627/project/1213723193546726/task/1216210853624520) |
| Interface de gestion des désignations de déclarants | *Aucune tâche de suivi identifiée dans le référentiel de projet* |
| Révocation unitaire d'un jeton d'accès *(constat d'audit du 20 août 2026, non corrigé)* | *Aucune tâche de suivi identifiée dans le référentiel de projet* |

## 6. Situation de ce contrôle dans le dispositif d'ensemble

| Responsabilité du dispositif | État au 6 août 2026 |
|---|---|
| **1 — Désignation des responsables et cloisonnement des accès** | **Ce contrôle est réalisé (cloisonnement technique). La désignation formelle de la personne est en cours — démarche non technique distincte.** |
| 2 — Classification des risques et procédures écrites | Planifié — la rédaction des documents conditionne les développements correspondants |
| 3 — Entrée en relation et vérification de l'association | Partiellement réalisé — le verrou de vérification avant ouverture de collecte est en place (fiche E3, 5 août 2026). Les autres composantes sont planifiées. |
| 4 — Gel des avoirs | Planifié — chantier prioritaire suivant |
| 5 — Surveillance des opérations atypiques et déclaration | Planifié |
| 6 — Conservation des documents et rapport annuel | Planifié |

## 7. Éléments de traçabilité

La réalisation est enregistrée dans l'historique des modifications du logiciel, sur la ligne de
développement dédiée au dispositif LCB-FT, ouverte le 5 août 2026.

Le cloisonnement mis en place repose sur une décision d'architecture documentée : un rôle unique,
sans cumul de droits possible, sans table de multi-rôles — ce qui simplifie les contrôles et
réduit les surfaces d'erreur. Cette décision a été arrêtée par le porteur du projet le
5 août 2026.

Le mécanisme de provisionnement du compte (injection de variables d'environnement au démarrage,
sans stockage du mot de passe en clair ni re-déploiement ultérieur) a été mis en place le
6 août 2026. Il utilise le même patron que le compte opérateur de modération existant.

**La procédure de provisionnement du compte est en place.** Une fois la désignation formelle
effectuée, le compte est créé par injection de deux variables d'environnement sur le serveur
(`APP_COMPLIANCE_OFFICER_EMAIL` et `APP_COMPLIANCE_OFFICER_PASSWORD`) : le compte est créé
automatiquement au démarrage de l'application, puis les variables sont retirées. Le compte persiste
en base ; aucun redéploiement avec les variables en clair n'est nécessaire une fois la création
effectuée. Ce mécanisme suit exactement le même modèle que le compte opérateur de modération,
déjà utilisé en production.

L'espace de travail du responsable de la conformité a été complété le 12 août 2026. Il comprend
désormais un tableau de bord centralisé, accessible exclusivement à ce rôle, donnant accès à un
résumé du nombre d'alertes en attente, à l'historique des derniers contrôles des registres pour
l'ensemble des associations de la plateforme, et aux vingt dernières entrées du journal d'audit
immuable. Les règles d'accès ont été étendues pour garantir l'isolation dans les deux sens : le
responsable de la conformité est automatiquement redirigé vers son espace lorsqu'il tente d'accéder
à l'espace de modération, et les autres rôles sont bloqués à l'entrée de l'espace de conformité.
Ces comportements font l'objet de contrôles automatisés supplémentaires vérifiés lors de chaque
modification du logiciel. Ni les écrans de modération ni les écrans destinés aux associations ou
aux donateurs ne donnent accès aux informations de conformité.

---

*Document établi le 6 août 2026, complété le 21 août 2026 (point 3.1 — faille de cloisonnement constatée et corrigée le 20 août 2026). Une fiche de même nature sera produite pour chaque contrôle du
dispositif LCB-FT au fur et à mesure de sa réalisation.*
