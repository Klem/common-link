# Référentiel des personnes habilitées à déclarer un soupçon auprès de TRACFIN

**Fiche de contrôle — dispositif de lutte contre le blanchiment de capitaux et le financement du terrorisme (LCB-FT)**

| | |
|---|---|
| **Entité** | CommonLink |
| **Nature du document** | Fiche descriptive d'un contrôle mis en place, destinée à la commission juridique |
| **Responsabilité concernée** | Désignation des responsables et cloisonnement des accès *(responsabilité n° 1 du dispositif interne, épique E1)* |
| **Référence de suivi interne** | Tâche Asana n° `1216210976716058` — « Inscrire le déclarant sur ERMES et obtenir le n° de télédéclarant » |
| **Lien de suivi** | https://app.asana.com/1/1213718564226627/project/1213723193546726/task/1216210976716058 |
| **Priorité assignée** | P0 (la plus élevée) |
| **Date de réalisation** | 6 août 2026 |
| **État** | Contrôle mis en place et vérifié par contrôles automatisés. Non encore déployé — la mise en production de la plateforme est conditionnée à l'achèvement de l'ensemble du dispositif. Le prérequis non-technique associé — inscription sur le portail ERMES de TRACFIN et obtention du numéro de télédéclarant — n'est pas encore réalisé. La tâche Asana correspondante ne peut être considérée comme accomplie avant que ce prérequis soit levé. |
| **Rédacteur** | Équipe technique CommonLink |

---

## 1. Avertissement sur la portée de ce document

Ce document décrit **un seul contrôle**, portant sur le stockage sécurisé des informations relatives
aux personnes habilitées à transmettre des déclarations de soupçon à TRACFIN.

Le dispositif LCB-FT de CommonLink est organisé en six responsabilités, dont celle-ci ne constitue
qu'un élément. Les cinq autres — classification des risques, vérification de l'entrée en relation,
gel des avoirs, surveillance des opérations atypiques et déclaration, conservation des documents
et rapport annuel — sont **planifiées et non encore réalisées** à la date de ce document. Leur
état d'avancement fait l'objet d'un suivi distinct.

Aucune conclusion sur la conformité globale du dispositif ne peut être tirée de la présente fiche.

## 2. Le risque constaté

La réglementation LCB-FT impose à CommonLink de désigner une ou plusieurs personnes habilitées à
déposer des **déclarations de soupçon** auprès de TRACFIN via le portail ERMES. Chaque personne
ainsi désignée reçoit un numéro de télédéclarant propre à cet usage.

L'absence d'un registre structuré pour ces informations aurait créé deux risques cumulés.

D'une part, **l'absence de traçabilité** : sans registre horodaté et inaltérable, il serait
impossible de répondre a posteriori à la question « qui était habilité à déclarer à telle date ? ».
TRACFIN est en droit de poser cette question, notamment en cas de contrôle.

D'autre part, **l'exposition de données sensibles** : le numéro de télédéclarant, associé à
l'identité complète de la personne désignée, permet d'agir auprès d'un organisme d'État. Stocké
en clair dans la base de données, il serait lisible par toute personne disposant d'un accès en
lecture à cette base — sous-traitant, prestataire d'hébergement, ou éventuel attaquant.

Ce constat a été établi le 6 août 2026 dans le cadre de la préparation du dispositif LCB-FT.
La plateforme n'encaisse aucun don réel à ce jour.

## 3. Pourquoi ce risque existait

La plateforme disposait déjà d'une primitive de chiffrement applicatif, documentée dans une fiche
distincte et constituant le socle technique de ce registre. Aucun enregistrement n'en faisait usage
jusqu'à présent : les données sensibles des fonctionnalités existantes étaient soit protégées par
d'autres mécanismes, soit hors périmètre LCB-FT.

L'entrée dans le périmètre des obligations TRACFIN crée le besoin d'un registre dédié, inexistant
à ce jour. Sans ce registre, le numéro de télédéclarant aurait dû être conservé en dehors de la
plateforme — dans un document ou une configuration accessible à l'administrateur — sans
historique des révocations ni lien avec le journal d'audit.

## 4. Le contrôle mis en place

Un registre dédié est désormais intégré à la plateforme pour tenir à jour la liste des personnes
habilitées à déposer des déclarations de soupçon auprès de TRACFIN.

Trois caractéristiques méritent d'être soulignées devant la commission.

**Les informations sensibles sont protégées au sein même du stockage.** Le numéro de télédéclarant
et l'identité de la personne désignée sont **chiffrés** avant d'être enregistrés dans la base de
données, en utilisant un algorithme de chiffrement symétrique reconnu (AES-256-GCM) avec une
valeur d'initialisation aléatoire à chaque écriture. Un administrateur disposant d'un accès
direct à la base ne peut pas lire ces informations en clair. Seule l'application, qui détient la
clé de chiffrement, est en mesure de les restituer.

**La révocation ne supprime pas la désignation.** Lorsqu'une personne cesse d'être habilitée à
déposer des déclarations, sa désignation n'est pas effacée du registre : elle est **révoquée à une
date précise**. Cette approche garantit qu'il sera toujours possible de répondre à la question
« qui était habilité à telle date ? », y compris après plusieurs changements successifs de
déclarant. Une suppression physique aurait rendu cette question définitivement sans réponse.

**Chaque modification est retranscrite dans le journal d'audit.** Toute désignation et toute
révocation produit une entrée dans le journal chaîné et inaltérable de la plateforme. Ces entrées
ne contiennent pas le numéro de télédéclarant — elles référencent uniquement l'identifiant interne
de la désignation — ce qui préserve la confidentialité de l'information tout en conservant la
traçabilité de chaque décision.

## 5. Les éléments de preuve

Le registre est accompagné de **quatre contrôles automatisés** portant chacun sur un aspect
distinct de son comportement attendu. Ces contrôles sont réexécutés à chaque modification du
logiciel : si une évolution future altérait l'un de ces comportements, elle serait signalée en
échec avant d'être intégrée.

| Comportement vérifié | Résultat |
|---|---|
| Une désignation enregistrée peut être relue de manière identique via le registre | Vérifié |
| La colonne stockée en base contient une valeur chiffrée, jamais le numéro en clair | Vérifié |
| Après révocation, la désignation n'apparaît plus dans la liste des personnes actives, mais la ligne est conservée en base | Vérifié |
| Chaque écriture (désignation et révocation) produit une entrée dans le journal d'audit, et aucune entrée ne contient le numéro de télédéclarant en clair | Vérifié |

L'ensemble des contrôles automatisés de la plateforme a été exécuté après l'ajout de ce registre,
sans régression sur les fonctionnements existants.

## 6. Ce que ce contrôle ne couvre pas

Cette précision est apportée pour éviter toute lecture extensive de la présente fiche.

Ce contrôle garantit que la plateforme peut **stocker de manière sécurisée** les informations
relatives aux déclarants désignés. Il ne couvre pas :

- **l'inscription administrative** sur le portail ERMES de TRACFIN et l'obtention du numéro de
  télédéclarant — prérequis non-technique, hors périmètre du développement logiciel, à la charge
  des responsables de l'entité ;
- **la vérification que la personne désignée** remplit effectivement les conditions requises
  (qualification, désignation signée, lettre de mission) — ce contrôle documentaire relève de la
  procédure interne, elle-même à rédiger ;
- **l'interface de gestion** des désignations à destination du responsable de conformité —
  planifiée dans une étape ultérieure de développement ;
- **le cloisonnement des dossiers de soupçon** par déclarant — prévu et conditionné à la
  réalisation du back-office de revue des alertes ;
- **la constitution et le dépôt** des dossiers de soupçon, qui est un acte manuel effectué sur
  ERMES par la personne habilitée — la plateforme ne dispose pas d'interface directe avec TRACFIN.
  Un modèle de dossier pré-remplissant les champs attendus par ERMES reste à construire ; le dépôt
  lui-même demeurera manuel, aucune interface applicative n'étant publiée par TRACFIN.

**Suivi des travaux restants.** Les éléments ci-dessus qui restent à réaliser sont suivis dans le
référentiel interne de gestion de projet aux références suivantes.

| Travail restant | Lien de suivi |
|---|---|
| Inscription du déclarant sur ERMES et obtention du numéro de télédéclarant *(prérequis non technique)* | [1216210976716058](https://app.asana.com/1/1213718564226627/project/1213723193546726/task/1216210976716058) |
| Désignation du responsable LCB-FT, du déclarant et du correspondant TRACFIN *(prérequis non technique)* | [1216210976716057](https://app.asana.com/1/1213718564226627/project/1213723193546726/task/1216210976716057) |
| Rédaction des procédures LCB-FT écrites, dont le contrôle documentaire de la désignation *(prérequis non technique)* | [1216210976716063](https://app.asana.com/1/1213718564226627/project/1213723193546726/task/1216210976716063) |
| Cloisonnement des dossiers de soupçon par déclarant | [1216210976716060](https://app.asana.com/1/1213718564226627/project/1213723193546726/task/1216210976716060) |
| Back-office de revue des alertes, préalable au cloisonnement | [1216210853624513](https://app.asana.com/1/1213718564226627/project/1213723193546726/task/1216210853624513) |
| Modèle de dossier de soupçon pré-remplissant les champs ERMES | [1216210853624514](https://app.asana.com/1/1213718564226627/project/1213723193546726/task/1216210853624514) |
| Documentation de l'interdiction de divulgation et du devoir d'abstention *(prérequis non technique)* | [1216210853624515](https://app.asana.com/1/1213718564226627/project/1213723193546726/task/1216210853624515) |
| Interface de gestion des désignations de déclarants | *Aucune tâche de suivi identifiée dans le référentiel de projet* |

## 7. Situation de ce contrôle dans le dispositif d'ensemble

| Responsabilité du dispositif | État au 6 août 2026 |
|---|---|
| **1 — Désignation des responsables et cloisonnement des accès** | **Ce contrôle est réalisé, ainsi que la création du rôle de responsable de conformité (fiche distincte E1). L'interface de gestion des déclarants et le cloisonnement des dossiers restent à réaliser.** |
| 2 — Classification des risques et procédures écrites | Planifié — la rédaction des documents conditionne les développements correspondants |
| 3 — Entrée en relation et vérification de l'association | Partiellement réalisé — le verrou de vérification avant ouverture de collecte est en place (fiche E3). L'identification des bénéficiaires effectifs et l'automatisation du scan des registres restent à réaliser. |
| 4 — Gel des avoirs | Planifié — chantier prioritaire suivant |
| 5 — Surveillance des opérations atypiques et déclaration | Planifié |
| 6 — Conservation des documents et rapport annuel | Planifié |

Il est à noter qu'une partie des travaux planifiés est conditionnée à la remise de documents non
techniques : la désignation signée des responsables, la classification des risques validée, et les
procédures écrites. Les développements correspondants ne peuvent être engagés avant, sous peine de
transcrire dans le logiciel des règles qui n'auraient pas été arrêtées.

## 8. Éléments de traçabilité

Ce registre est enregistré dans l'historique des modifications du logiciel, sur la ligne de
développement dédiée au dispositif LCB-FT. La modification comprend la structure de stockage,
le mécanisme de chiffrement, la règle de révocation par datation, et les quatre contrôles
automatisés décrits au point 5.

La décision de chiffrer le numéro de télédéclarant et l'identité du déclarant — plutôt que de les
stocker en clair — est documentée au sein même du logiciel. L'objectif est qu'une évolution
ultérieure ne supprime pas ce chiffrement en le jugeant superflu, ou ne remplace pas la datation
de révocation par une suppression physique en croyant simplifier la base.

Le journal d'audit dans lequel chaque désignation et révocation est consignée est lui-même
inaltérable : ni l'application ni un administrateur ne peut modifier ou supprimer une entrée
existante. Cette inaltérabilité a fait l'objet d'une fiche distincte.

---

*Document établi le 6 août 2026. Une fiche de même nature sera produite pour chaque contrôle du
dispositif LCB-FT au fur et à mesure de sa réalisation.*
