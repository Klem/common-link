# Enregistrement du niveau de risque des associations et des dons

**Fiche de contrôle — dispositif de lutte contre le blanchiment de capitaux et le financement du terrorisme (LCB-FT)**

| | |
|---|---|
| **Entité** | CommonLink |
| **Nature du document** | Fiche descriptive d'un livrable mis en place, destinée à la commission juridique |
| **Responsabilité concernée** | Classification des risques et procédures écrites *(responsabilité n° 2 du dispositif interne, épique E2)* |
| **Référence de suivi interne** | Tâche Asana n° `1216210976716065` — « Champ riskLevel sur AssociationProfile et Donation (Flyway) » |
| **Lien de suivi** | https://app.asana.com/1/1213718564226627/project/1213723193546726/task/1216210976716065 |
| **Priorité assignée** | P1 |
| **Date de livraison** | 7 août 2026 |
| **État** | Livré et vérifié par contrôles automatisés. Non encore déployé — la mise en production de la plateforme est conditionnée à l'achèvement de l'ensemble du dispositif. |
| **Rédacteur** | Équipe technique CommonLink |

---

## 1. Avertissement sur la portée de ce document

Ce document décrit **un seul livrable**, portant sur la capacité de la plateforme à enregistrer un niveau de risque sur les associations et sur les dons.

Le dispositif LCB-FT de CommonLink est organisé en six responsabilités, dont celle-ci ne constitue qu'un élément. Les cinq autres — désignation des responsables, vérification de l'identité du client, gel des avoirs, surveillance des opérations atypiques et déclaration, conservation des documents et rapport annuel — sont planifiées ou en cours de réalisation. Leur état d'avancement fait l'objet d'un suivi distinct.

**Ce livrable ne constitue pas une classification des risques en vigueur.** Il pose la fondation technique qui permettra d'en appliquer une, une fois le document de classification rédigé, daté et validé par le conseil. Ce point est développé aux sections 2 et 4. Aucune conclusion sur la conformité globale du dispositif ne peut être tirée de la présente fiche.

## 2. La lacune constatée

La plateforme ne disposait d'aucun moyen d'enregistrer le niveau de risque d'une association ou d'un don.

La réglementation LCB-FT impose à CommonLink de classer les relations d'affaires selon leur niveau de risque — faible, standard ou élevé — et d'adapter le niveau de vigilance en conséquence. En l'absence de tout champ dédié à cet effet, il était impossible de satisfaire cette obligation, même après adoption d'un document de classification.

Ce constat a été établi lors de la préparation du dispositif LCB-FT. Il n'a pas donné lieu à incident : la plateforme n'encaisse aucun don réel à ce jour.

## 3. Pourquoi cette lacune existait

La plateforme a été conçue dans un premier temps pour les fonctions de collecte et de comptabilité associative. Les champs de conformité réglementaire, dont le niveau de risque, n'avaient pas été intégrés au modèle de données initial.

La classification des risques s'appuie sur un document de politique interne, dont la rédaction et la validation par le conseil sont des étapes préalables aux développements correspondants. Ce séquencement délibéré — d'abord le document, ensuite le code — est la raison pour laquelle ce champ n'avait pas été ajouté plus tôt. Le prérequis non technique reste à ce jour non satisfait : le document de classification n'a pas encore été rédigé ni soumis au conseil.

## 4. Le contrôle mis en place

La plateforme dispose désormais, sur chaque association et sur chaque don, d'un **champ dédié à l'enregistrement du niveau de risque**.

Ce champ reconnaît trois niveaux : faible, standard et élevé. Toutes les associations et tous les dons existants se voient attribuer la valeur standard par défaut — cette valeur neutre n'est ni une évaluation ni une décision ; elle signifie simplement qu'aucune classification formelle n'a encore été effectuée.

Trois caractéristiques de ce livrable méritent d'être soulignées devant la commission.

**Il ne constitue pas une classification en vigueur.** L'enregistrement d'un niveau de risque autre que la valeur par défaut exigera un document de classification préalablement rédigé et validé. Tant que ce document n'existe pas, le champ conserve la valeur standard, non en vertu d'une décision mais faute d'instruction. Ce document reste à écrire et à valider — c'est le prérequis non technique signalé en tête du présent sprint de développement.

**Il préserve l'interprétabilité des évaluations dans le temps.** Aux côtés du niveau de risque, deux champs complémentaires sont introduits : la date de la dernière évaluation formelle, et l'identifiant de la version du document de classification en vigueur à ce moment. Sans cette référence de version, une évaluation passée deviendrait ininterprétable dès que le document de classification serait révisé — on ne saurait plus à quelles règles elle se rapportait.

**Il s'applique à la fois aux associations et aux dons.** Le niveau de risque peut évoluer dans le temps pour une association. Certains dons peuvent avoir été enregistrés sous un niveau différent de celui qui prévaut au moment d'une réévaluation ultérieure. Conserver un instantané du niveau de risque sur le don lui-même permet de rendre compte de l'état du risque au moment précis où le don a été effectué, indépendamment des réévaluations qui interviendront ensuite.

## 5. Les éléments de preuve

### Contrôles automatisés

| Scénario vérifié | Résultat |
|---|---|
| La suite de tests complète s'exécute sans régression après l'ajout des champs | Réussi — `./gradlew test` BUILD SUCCESSFUL |
| L'entité association persiste le niveau de risque STANDARD par défaut et les champs d'évaluation à null ; le niveau HIGH fait l'aller-retour correctement (flush + clear + rechargement) | Réussi — `RiskLevelPersistenceTest` (2 tests) |
| L'entité don persiste le niveau de risque STANDARD par défaut ; le niveau HIGH fait l'aller-retour en tant qu'instantané immuable | Réussi — `RiskLevelPersistenceTest` (2 tests) |
| Aucun objet de transfert de données (`dto/`) ne contient le champ de niveau de risque | Réussi — recherche exhaustive dans le répertoire `dto/` : zéro occurrence |
| Aucun contrôleur ne retourne une entité brute `AssociationProfile` ou `Donation` — tous retournent des objets DTO dédiés | Réussi — vérification sur les 3 contrôleurs concernés : tous les types de retour sont des DTO |

Note : les tests de persistance (`RiskLevelPersistenceTest`) valident le mapping JPA sur le schéma généré par Hibernate (create-drop). Ils ne valident pas la migration Flyway V53 elle-même — celle-ci a été vérifiée manuellement (voir ci-dessous).

### Vérification opérationnelle manuelle

La migration de base de données a été appliquée manuellement sur la base de données locale de développement, en présence de lignes existantes dans les deux tables concernées.

Résultats observés :
- toutes les associations et tous les dons existants affichent la valeur standard après la migration — aucune ligne n'a été affectée d'un niveau différent ;
- la date d'évaluation et la référence de version du document de classification sont nulles pour toutes les lignes existantes, ce qui reflète l'absence de toute évaluation formelle à ce stade ;
- le script de retour arrière a ensuite été exécuté avec succès sur la même base : les colonnes ont été supprimées sans erreur et les tables se retrouvent dans leur état d'origine.

## 6. Ce que ce contrôle ne couvre pas

Cette précision est apportée pour éviter toute lecture extensive de la présente fiche.

Ce livrable introduit la capacité technique d'enregistrer un niveau de risque. **Il ne met pas en place une classification des risques effective, et n'implique aucune mesure de vigilance particulière à ce stade.** En particulier, il ne couvre pas :

- la **rédaction et l'adoption du document de classification des risques** — c'est le prérequis immédiat, non technique, signalé comme non encore satisfait ; aucune évaluation ne peut être effectuée avant que ce document existe et soit validé par le conseil ;
- l'**évaluation effective du niveau de risque** de chaque association et de chaque don — cette évaluation suppose l'adoption préalable du document de classification ; le mécanisme qui applique les règles correspondantes est, lui, livré *(voir la fiche E2 — Correspondance entre le niveau de risque et les mesures de vigilance)* ;
- les **mesures de vigilance différenciées** selon le niveau de risque — diligences simplifiées, standard ou renforcées : *la transcription versionnée de cette correspondance est traitée depuis par la fiche E2 — Correspondance entre le niveau de risque et les mesures de vigilance* ; la collecte et la vérification effectives des pièces qu'elle énumère restent à réaliser ;
- le **contrôle des mesures de gel des avoirs**, ni sur les associations, ni sur leurs représentants, ni sur les donateurs — *traité depuis par les fiches de l'épique E4* ;
- la **surveillance des opérations atypiques** et la déclaration de soupçon ;
- la **conservation** des pièces justificatives et le **rapport annuel** d'activité.

**Suivi des travaux restants.** Les éléments ci-dessus qui restent à réaliser sont suivis dans le référentiel interne de gestion de projet aux références suivantes.

| Travail restant | Lien de suivi |
|---|---|
| Rédaction et adoption du document de classification des risques *(prérequis non technique, bloque l'évaluation effective)* | [1216210976716062](https://app.asana.com/1/1213718564226627/project/1213723193546726/task/1216210976716062) |
| Rédaction des procédures LCB-FT écrites *(prérequis non technique)* | [1216210976716063](https://app.asana.com/1/1213718564226627/project/1213723193546726/task/1216210976716063) |
| Surveillance des opérations atypiques et déclaration de soupçon *(épique E5)* | [1216210853624511](https://app.asana.com/1/1213718564226627/project/1213723193546726/task/1216210853624511) · [1216210853624512](https://app.asana.com/1/1213718564226627/project/1213723193546726/task/1216210853624512) · [1216210853624513](https://app.asana.com/1/1213718564226627/project/1213723193546726/task/1216210853624513) · [1216210853624514](https://app.asana.com/1/1213718564226627/project/1213723193546726/task/1216210853624514) |
| Conservation des pièces justificatives et rapport annuel *(épique E6)* | [1216210853624518](https://app.asana.com/1/1213718564226627/project/1213723193546726/task/1216210853624518) · [1216210853624517](https://app.asana.com/1/1213718564226627/project/1213723193546726/task/1216210853624517) · [1216210853624520](https://app.asana.com/1/1213718564226627/project/1213723193546726/task/1216210853624520) |
| Collecte et vérification effectives des pièces exigées par niveau de risque | *Aucune tâche de suivi identifiée dans le référentiel de projet* |

## 7. Situation de ce contrôle dans le dispositif d'ensemble

| Responsabilité du dispositif | État au 7 août 2026 |
|---|---|
| 1 — Désignation des responsables et cloisonnement des accès | Réalisé en partie (accès cloisonné au responsable de conformité livré). Les autres composantes — désignation signée, procédures écrites — sont planifiées. |
| **2 — Classification des risques et procédures écrites** | **Ce livrable introduit la capacité d'enregistrer un niveau de risque. Le document de classification lui-même reste à rédiger et à valider par le conseil — sans lui, aucune évaluation effective ne peut être réalisée.** |
| 3 — Entrée en relation et vérification de l'association | Le verrou de vérification préalable à la collecte est réalisé. Les autres composantes sont planifiées. |
| 4 — Gel des avoirs | Planifié — chantier prioritaire suivant |
| 5 — Surveillance des opérations atypiques et déclaration | Planifié |
| 6 — Conservation des documents et rapport annuel | Planifié |

Il est rappelé qu'une partie des travaux planifiés est **conditionnée à la remise de documents non techniques** : le document de classification des risques validé par le conseil, les procédures écrites correspondantes, et le contrat encadrant le recours aux diligences d'un prestataire tiers. Les développements ne peuvent être engagés avant, sous peine de transcrire dans le logiciel des règles qui n'auraient pas encore été arrêtées.

## 8. Éléments de traçabilité

Les modifications sont enregistrées dans l'historique des modifications du logiciel, sur la ligne de développement dédiée au dispositif LCB-FT. Elles comprennent l'ajout des champs sur les entités association et don, la migration de base de données permettant d'appliquer ces ajouts sur un schéma existant avec des lignes préexistantes, le script de retour arrière correspondant, et la mise à jour du schéma consolidé de référence. Aucune interface exposée aux associations ou aux donateurs n'a été modifiée : le niveau de risque est une donnée interne de conformité.

---

*Document établi le 7 août 2026. Une fiche de même nature sera produite pour chaque livrable du dispositif LCB-FT au fur et à mesure de sa réalisation.*
