# Correspondance entre le niveau de risque et les mesures de vigilance

**Fiche de contrôle — dispositif de lutte contre le blanchiment de capitaux et le financement du terrorisme (LCB-FT)**

| | |
|---|---|
| **Entité** | CommonLink |
| **Nature du document** | Fiche descriptive d'un livrable mis en place, destinée à la commission juridique |
| **Responsabilité concernée** | Classification des risques et procédures écrites *(responsabilité n° 2 du dispositif interne, épique E2)* |
| **Référence de suivi interne** | Tâche Asana n° `1216210976716064` — « Table de config riskLevel → niveau de vigilance (versionnée) » |
| **Lien de suivi** | https://app.asana.com/1/1213718564226627/project/1213723193546726/task/1216210976716064 |
| **Priorité assignée** | P1 |
| **Date de livraison** | 7 août 2026 |
| **État** | Livré et vérifié par contrôles automatisés. Non encore déployé — la mise en production de la plateforme est conditionnée à l'achèvement de l'ensemble du dispositif. |
| **Rédacteur** | Équipe technique CommonLink |
| **Document de classification transcrit** | Version `2026-08-v1`, correspondance fondée sur les articles L.561-5, L.561-9, L.561-10 et R.561-5, R.561-14-1, R.561-20 du Code monétaire et financier, ainsi que les lignes directrices révisées de l'ACPR sur l'identification et la connaissance client (2022). Les fréquences de réexamen sont des choix de politique interne de CommonLink fondés sur le principe de proportionnalité — elles ne sont pas imposées mot pour mot par la réglementation mais restent justifiables devant l'ACPR. Une adoption formelle par le conseil est requise avant toute application effective à des associations réelles. |

---

## 1. Avertissement sur la portée de ce document

Ce document décrit **un seul livrable**, portant sur la capacité de la plateforme à appliquer de façon cohérente et traçable la correspondance entre le niveau de risque d'une association et les mesures de vigilance qui lui sont associées.

Le dispositif LCB-FT de CommonLink est organisé en six responsabilités, dont celle-ci ne constitue qu'un élément. Les cinq autres — désignation des responsables, vérification de l'identité du client, gel des avoirs, surveillance des opérations atypiques et déclaration, conservation des documents et rapport annuel — sont planifiées ou en cours de réalisation. Leur état d'avancement fait l'objet d'un suivi distinct.

**Ce livrable ne constitue pas une décision de classification en vigueur.** Il met en place le mécanisme qui garantit que la correspondance appliquée par le logiciel est identique à la correspondance écrite, datée et versionnée. Aucune conclusion sur la conformité globale du dispositif ne peut être tirée de la présente fiche.

## 2. La lacune constatée

La plateforme disposait, depuis le livrable précédent, de la capacité d'enregistrer un niveau de risque sur chaque association et chaque don. **Elle ne disposait d'aucun mécanisme garantissant que les mesures de vigilance effectivement appliquées par le logiciel correspondaient à des procédures écrites, datées et versionnées.**

En d'autres termes, un auditeur ou un agent de contrôle ne pouvait pas vérifier, à partir des seuls éléments disponibles, quelle correspondance avait été appliquée lors d'une évaluation passée : le code ne portait aucune référence à un document de politique interne identifiable, et aucun mécanisme ne permettait de retrouver les règles en vigueur à la date de l'évaluation.

Ce constat a été établi lors de la préparation du dispositif LCB-FT. Il n'a pas donné lieu à incident : la plateforme n'encaisse aucun don réel à ce jour.

## 3. Pourquoi cette lacune existait

La réglementation LCB-FT impose non seulement d'appliquer des mesures de vigilance proportionnées au niveau de risque, mais aussi de pouvoir **démontrer** que les mesures appliquées sont cohérentes avec les procédures écrites adoptées par l'établissement. Cette démonstration suppose que le lien entre le code et le document de politique interne soit explicite, vérifiable et traçable dans le temps.

La plateforme avait été conçue sans ce lien. Le niveau de risque pouvait être enregistré, mais rien n'indiquait, au moment d'une évaluation ou lors d'un contrôle ultérieur, à quelle version d'un document de politique interne se rapportait la correspondance appliquée. Si ce document venait à être révisé, les évaluations antérieures devenaient ininterprétables.

## 4. Le contrôle mis en place

La plateforme dispose désormais d'un **document de classification versionné**, chargé au démarrage et dont le contenu détermine entièrement les mesures de vigilance associées à chaque niveau de risque.

Quatre caractéristiques de ce livrable méritent d'être soulignées devant la commission.

**La correspondance est entièrement définie par écrit, hors du code.** Les mesures de vigilance — nature des vérifications, fréquence de réexamen, pièces exigées — pour les trois niveaux de risque reconnus (faible, standard, élevé) sont décrites dans un document distinct du code source. Ce document porte un identifiant de version et une date d'adoption. Il n'est pas possible d'appliquer une correspondance qui ne soit pas décrite dans ce document.

**Le logiciel refuse de démarrer si la correspondance est incomplète.** Au lancement, le logiciel vérifie que le document de classification est présent, qu'il porte un identifiant de version non vide, et que chaque niveau de risque reconnu dispose d'une entrée. Si l'une de ces trois conditions n'est pas satisfaite, le démarrage est interrompu avec un message explicite. Il n'existe pas de mode dégradé dans lequel un niveau de risque serait sans procédure définie.

**Chaque évaluation est horodatée et estampillée avec la version en vigueur.** Lorsqu'une association est évaluée, la version du document de classification chargé au démarrage est inscrite aux côtés du niveau de risque attribué. Cette estampille permet de retrouver, pour toute évaluation passée, la version exacte du document qui était en vigueur et donc les procédures qui s'appliquaient à ce moment.

**Toute modification laisse une trace permanente et non falsifiable.** Le document de classification est géré dans l'historique des modifications du logiciel. Chaque changement y est enregistré avec l'identité de son auteur, la date et heure exactes, et la description des modifications apportées. Il n'existe intentionnellement aucun moyen de modifier la correspondance sans laisser cette trace, ni aucun écran d'administration permettant de le faire à chaud — cette contrainte a été actée comme décision architecturale du dispositif.

## 5. Les éléments de preuve

La correction est accompagnée de **quatre contrôles automatisés** ajoutés au patrimoine de tests de la plateforme. Ces contrôles sont réexécutés à chaque modification du logiciel : si une évolution future venait à rétablir une lacune, elle serait signalée en échec avant d'être intégrée.

| Scénario vérifié | Résultat |
|---|---|
| Le logiciel refuse de démarrer si l'identifiant de version est vide | Refusé avec message explicite mentionnant le champ `version` — vérifié par contrôles automatisés |
| Le logiciel refuse de démarrer si le niveau de risque élevé n'a pas de mesures définies | Refusé avec message explicite mentionnant le niveau concerné — vérifié par contrôles automatisés |
| Le logiciel refuse de démarrer si le niveau de risque faible n'a pas de mesures définies | Refusé avec message explicite mentionnant le niveau concerné — vérifié par contrôles automatisés |
| Le logiciel démarre correctement et rend la version accessible au code appelant lorsque la configuration est complète | Démarrage réussi, version lisible et conforme au document chargé — vérifié par contrôles automatisés |

Le quatrième scénario établit le critère d'acceptation principal : le code qui évalue un risque peut effectivement lire la version en vigueur pour l'inscrire sur l'évaluation. Sans cette vérification, la traçabilité vers les procédures écrites serait déclarée mais non démontrée.

Le comportement du logiciel face à l'absence totale du document de classification est assuré par le mécanisme d'importation de configuration de Spring Boot : si le fichier est absent du classpath au démarrage, l'application s'arrête avec un message explicite avant même que les contrôles de l'application ne s'exécutent. Ce comportement est garanti par l'infrastructure et ne nécessite pas de test applicatif distinct.

## 6. Ce que ce contrôle ne couvre pas

Cette précision est apportée pour éviter toute lecture extensive de la présente fiche.

Ce livrable garantit la cohérence entre la correspondance écrite et le comportement du logiciel, et l'impossibilité de modifier cette correspondance sans laisser de trace. **Il ne met pas en place une évaluation effective des associations, et n'implique aucune décision de vigilance à ce stade.** En particulier, il ne couvre pas :

- l'**évaluation effective du niveau de risque** de chaque association — cette évaluation suppose l'adoption formelle du document de classification par le conseil, qui constitue encore un prérequis non satisfait à la date de ce document ;
- la **mise en œuvre concrète des mesures de vigilance** — les pièces listées dans la correspondance ne sont pas encore collectées ni vérifiées automatiquement ;
- le **contrôle des mesures de gel des avoirs**, ni sur les associations, ni sur leurs représentants, ni sur les donateurs — *traité depuis par les fiches de l'épique E4* ;
- la **surveillance des opérations atypiques** et la déclaration de soupçon ;
- la **conservation** des pièces justificatives et le **rapport annuel** d'activité.

Chacun de ces points fait l'objet de travaux planifiés et ordonnancés, avec des dépendances identifiées entre eux.

**Suivi des travaux restants.** Les éléments ci-dessus qui restent à réaliser sont suivis dans le référentiel interne de gestion de projet aux références suivantes.

| Travail restant | Lien de suivi |
|---|---|
| Adoption formelle du document de classification des risques par l'organe compétent *(prérequis non technique, bloque l'évaluation effective)* | [1216210976716062](https://app.asana.com/1/1213718564226627/project/1213723193546726/task/1216210976716062) |
| Rédaction des procédures LCB-FT écrites *(prérequis non technique)* | [1216210976716063](https://app.asana.com/1/1213718564226627/project/1213723193546726/task/1216210976716063) |
| Formation de l'équipe aux procédures LCB-FT *(prérequis non technique)* | [1216210976716055](https://app.asana.com/1/1213718564226627/project/1213723193546726/task/1216210976716055) |
| Surveillance des opérations atypiques et déclaration de soupçon *(épique E5)* | [1216210853624511](https://app.asana.com/1/1213718564226627/project/1213723193546726/task/1216210853624511) · [1216210853624512](https://app.asana.com/1/1213718564226627/project/1213723193546726/task/1216210853624512) · [1216210853624513](https://app.asana.com/1/1213718564226627/project/1213723193546726/task/1216210853624513) · [1216210853624514](https://app.asana.com/1/1213718564226627/project/1213723193546726/task/1216210853624514) |
| Conservation des pièces justificatives et rapport annuel *(épique E6)* | [1216210853624518](https://app.asana.com/1/1213718564226627/project/1213723193546726/task/1216210853624518) · [1216210853624517](https://app.asana.com/1/1213718564226627/project/1213723193546726/task/1216210853624517) · [1216210853624520](https://app.asana.com/1/1213718564226627/project/1213723193546726/task/1216210853624520) |
| Collecte et vérification automatiques des pièces listées dans la correspondance | *Aucune tâche de suivi identifiée dans le référentiel de projet* |

## 7. Situation de ce contrôle dans le dispositif d'ensemble

| Responsabilité du dispositif | État au 7 août 2026 |
|---|---|
| 1 — Désignation des responsables et cloisonnement des accès | Réalisé en partie (accès cloisonné au responsable de conformité livré). La désignation signée reste planifiée. |
| **2 — Classification des risques et procédures écrites** | **La capacité d'enregistrer un niveau de risque est livrée (livrable précédent). La cohérence entre la correspondance écrite et le comportement du logiciel est désormais garantie et traçable (ce livrable). L'adoption formelle du document de classification par le conseil, et l'évaluation effective des associations, restent à réaliser.** |
| 3 — Entrée en relation et vérification de l'association | Le verrou de vérification préalable à la collecte est réalisé. Les autres composantes sont planifiées. |
| 4 — Gel des avoirs | Planifié — chantier prioritaire suivant |
| 5 — Surveillance des opérations atypiques et déclaration | Planifié |
| 6 — Conservation des documents et rapport annuel | Planifié |

Il est rappelé qu'une partie des travaux planifiés est **conditionnée à la remise de documents non techniques** : l'adoption formelle du document de classification par le conseil, les procédures écrites correspondantes, et le contrat éventuel encadrant le recours aux diligences d'un prestataire tiers. Les développements ne peuvent être engagés avant, sous peine de transcrire dans le logiciel des règles qui n'auraient pas encore été arrêtées.

## 8. Éléments de traçabilité

Les modifications sont enregistrées dans l'historique des modifications du logiciel, sur la ligne de développement dédiée au dispositif LCB-FT. Elles comprennent l'ajout du document de classification versionné, le mécanisme de chargement et de validation au démarrage, et les quatre contrôles automatisés décrits au point 5.

Le document de classification lui-même fait partie du logiciel et est soumis aux mêmes règles de traçabilité : toute modification est enregistrée dans l'historique avec son auteur, sa date et sa description. Ce choix architectural — document dans le code plutôt que dans une base de données ou un outil d'administration — est précisément ce qui rend la traçabilité non falsifiable sans accès à l'infrastructure technique.

---

*Document établi le 7 août 2026. Une fiche de même nature sera produite pour chaque livrable du dispositif LCB-FT au fur et à mesure de sa réalisation.*
