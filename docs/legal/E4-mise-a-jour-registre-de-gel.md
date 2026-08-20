# Mise à jour automatique du registre national des mesures de gel des avoirs

**Fiche de contrôle — dispositif de lutte contre le blanchiment de capitaux et le financement du terrorisme (LCB-FT)**

| | |
|---|---|
| **Entité** | CommonLink |
| **Nature du document** | Fiche descriptive d'un contrôle mis en place, destinée à la commission juridique |
| **Responsabilité concernée** | Gel des avoirs — maintien à jour du registre de référence *(responsabilité n° 4 du dispositif interne, épique E4)* |
| **Référence de suivi interne** | Tâche Asana n° `1216210853624507` — « Mise à jour automatique des listes de gel » |
| **Lien de suivi** | https://app.asana.com/1/1213718564226627/project/1213723193546726/task/1216210853624507 |
| **Priorité assignée** | P0 (la plus élevée) |
| **Date de mise en place** | 10 août 2026 |
| **État** | Vérifié par contrôles automatisés. Non encore déployé — la mise en production de la plateforme est conditionnée à l'achèvement de l'ensemble du dispositif. |
| **Rédacteur** | Équipe technique CommonLink |

---

## 1. Avertissement sur la portée de ce document

Ce document décrit **un seul contrôle**, portant sur la mise à jour régulière du registre national des mesures de gel des avoirs.

Le dispositif LCB-FT de CommonLink est organisé en six responsabilités. La présente fiche concerne exclusivement un mécanisme technique au sein de la responsabilité n° 4 (gel des avoirs). Les cinq autres responsabilités — désignation des responsables, classification des risques, entrée en relation et vérification de l'association, surveillance des opérations atypiques et déclaration, conservation des documents et rapport annuel — ainsi que les autres composantes de la responsabilité sur le gel des avoirs **sont planifiées et non encore entièrement réalisées** à la date de ce document.

Aucune conclusion sur la conformité globale du dispositif ne peut être tirée de la présente fiche.

## 2. Le risque constaté

Le registre national des mesures de gel des avoirs, publié par la Direction générale du Trésor, évolue dans le temps : des mesures nouvelles y sont inscrites, d'autres en sont retirées. Un registre figé à une date ancienne est un registre **incomplet au regard de l'état réel des mesures en vigueur**.

Si CommonLink consultait un registre non actualisé pour vérifier qu'une personne ou une entité n'est pas soumise à une mesure de gel :

- une mesure inscrite **après** la dernière mise à jour serait invisible, et la personne concernée pourrait recevoir ou effectuer des paiements sans être signalée ;
- une mesure levée **après** la dernière mise à jour resterait visible, sans incidence sur la sécurité des contrôles mais au détriment de l'exactitude du registre.

Le premier cas constitue une **défaillance latente** : la plateforme croit avoir contrôlé, mais elle a contrôlé sur une base périmée. Ce risque est d'autant plus grave que la défaillance n'est pas détectable par les équipes sans une vérification explicite de la date du registre utilisé.

## 3. Pourquoi ce risque existait

Le registre est téléchargé depuis un service public de la Direction générale du Trésor et stocké localement par CommonLink. Sans mécanisme de mise à jour automatique, ce téléchargement devait être déclenché manuellement — c'est-à-dire qu'il dépendait d'une intervention humaine à intervalles variables et non garantis.

Une tâche manuelle, même bien documentée, est par nature non fiable sur le long terme : elle peut être omise en cas d'absence, de charge opérationnelle élevée ou simplement d'oubli. La conformité LCB-FT ne peut pas reposer sur une vigilance humaine ponctuelle pour un mécanisme aussi fondamental.

La correction consiste à rendre cette mise à jour **entièrement automatique**, à une fréquence garantie et configurable, sans aucune intervention humaine nécessaire.

## 4. Le contrôle mis en place

CommonLink procède désormais à une **mise à jour automatique quotidienne** du registre national des mesures de gel. La fréquence retenue — au moins une fois par jour — est un paramètre d'exploitation qui peut être abaissé (augmenter la fréquence) mais pas relevé sans décision documentée, car une liste périmée constitue un manquement latent.

Quatre caractéristiques de ce contrôle méritent d'être soulignées devant la commission.

**La mise à jour est complète à chaque exécution.** Chaque cycle de mise à jour télécharge l'intégralité de la liste publiée par la Direction générale du Trésor — mesures nationales françaises, mesures européennes et mesures onusiennes applicables en France — et réconcilie le registre local avec cette version de référence. Les mesures nouvellement inscrites sont ajoutées, les mesures levées sont retirées, les informations modifiées sont mises à jour.

**La fréquence est un paramètre d'exploitation, pas une constante.** La valeur de vingt-quatre heures est documentée dans les fichiers de configuration de la plateforme, pour chaque environnement d'exploitation. Elle peut être modifiée par une opération de déploiement, sans modification du logiciel lui-même. Cette flexibilité permet d'adapter la fréquence si la DG Trésor venait à publier des mises à jour plus fréquentes en période de crise.

**Un échec de mise à jour ne rend pas les contrôles indisponibles.** En cas d'échec — réseau momentanément indisponible, service DG Trésor en maintenance, problème de connectivité — les contrôles de gel continuent de s'appuyer sur la **dernière version connue du registre**. Ce choix de conception est délibéré et motivé : arrêter les contrôles de gel en cas d'échec de mise à jour reviendrait à rendre la plateforme inopérante pour une durée indéterminée à chaque incident de réseau. Un registre légèrement périmé est préférable à l'absence totale de contrôle, à condition que la péremption soit courte, détectée et signalée — ce que le mécanisme garantit.

Chaque contrôle de gel effectué par la plateforme enregistre dans le journal d'audit la date de publication de la version du registre qu'il a consultée. Si un contrôle a été effectué sur un registre périmé, cette information est tracée et lisible par les équipes de conformité et les auditeurs.

**Les échecs sont tracés et signalés.** Chaque tentative de mise à jour — réussie ou échouée — est horodatée. En cas d'échec, la plateforme :

- enregistre un événement d'échec dans le journal d'audit de conformité, avec la date de la dernière mise à jour réussie, afin que les équipes puissent évaluer l'ancienneté du registre en service ;
- remonte une alerte au niveau d'urgence maximal dans les journaux techniques, avec un message indiquant explicitement l'action requise ;
- ne modifie pas la date de la dernière mise à jour réussie, qui reste la référence jusqu'à ce qu'une mise à jour complète soit effectuée.

Un mécanisme d'alerte opérationnelle vers les équipes de permanence est prévu et documenté dans le code comme point d'extension ; il sera branché lors de la mise en place du système d'alertes général de la plateforme.

**Deux instances simultanées ne peuvent pas effectuer deux mises à jour concurrentes.** La plateforme est conçue pour fonctionner sur plusieurs serveurs en parallèle. Un mécanisme de verrou au niveau de la base de données garantit qu'une seule instance effectue la mise à jour à la fois. Si une deuxième instance tente de démarrer une mise à jour pendant qu'une autre est en cours, elle attend la fin de la première sans déclencher de mise à jour redondante.

## 5. Les éléments de preuve

La mise en place de ce mécanisme est accompagnée de **quatre contrôles automatisés** ajoutés au patrimoine de tests de la plateforme. Ces contrôles sont réexécutés à chaque modification du logiciel : si une évolution future venait à régresser ce mécanisme, l'échec serait détecté avant intégration.

| Scénario testé | Comportement attendu | Résultat |
|---|---|---|
| Mise à jour réussie sur registre de test | La date de dernière mise à jour réussie est enregistrée ; la date de publication du registre est mémorisée | Vérifié par contrôles automatisés |
| Deuxième mise à jour consécutive | La date de dernière mise à jour réussie est mise à jour ; elle ne régresse jamais | Vérifié par contrôles automatisés |
| **Échec de mise à jour** (registre simulé indisponible) | La date de dernière mise à jour réussie reste inchangée ; la tentative est horodatée ; un événement d'échec est inscrit dans le journal d'audit | **Vérifié par contrôles automatisés** |
| Échec de mise à jour — la plateforme ne s'arrête pas | L'appel au mécanisme de mise à jour ne produit pas d'exception visible pour les autres services | Vérifié par contrôles automatisés |

Le troisième scénario est le plus significatif : il vérifie que la date de dernière réussite n'est pas altérée par un échec, que la tentative est bien enregistrée malgré cet échec, et qu'un événement d'audit est effectivement écrit. Il constitue la preuve que le choix de conception — continuer les contrôles sur la dernière version connue plutôt qu'arrêter la plateforme — fonctionne comme décrit.

L'ensemble des contrôles automatisés de la plateforme a été exécuté après la mise en place de ce mécanisme, sans régression sur les fonctionnements existants.

## 6. Ce que ce contrôle ne couvre pas

Cette précision est apportée pour éviter toute lecture extensive de la présente fiche.

Ce contrôle garantit que le registre des mesures de gel utilisé par la plateforme est mis à jour automatiquement selon une fréquence définie. **Il ne porte ni sur la qualité de ce registre, ni sur l'usage qui en est fait dans les contrôles.**

En particulier, il ne couvre pas :

- **le contenu du registre lui-même** : la Direction générale du Trésor est seule responsable de l'exhaustivité et de l'exactitude du registre qu'elle publie. CommonLink ne fait que l'utiliser tel quel ;
- **les contrôles de gel individuels** — le fait qu'une association, un représentant ou un donateur fasse l'objet d'une vérification contre le registre est documenté dans des fiches distinctes, *livrées* ;
- **la décision à prendre en cas de correspondance** : le registre identifie des personnes susceptibles d'être soumises à une mesure de gel ; la décision finale appartient toujours aux équipes de conformité et non au système automatique. *L'espace de traitement des alertes est livré ; la procédure interne écrite qui encadre ces décisions reste à rédiger* ;
- **la reprise des contrôles après une mise à jour du registre** : une mesure inscrite lors d'un cycle de synchronisation n'entraîne **aucun re-criblage** des associations ou des dons déjà contrôlés. Le registre est à jour, les contrôles passés ne sont pas rejoués ;
- **la surveillance des opérations atypiques** et la déclaration de soupçon, qui sont des mécanismes distincts ;
- **la conservation des pièces** liées aux contrôles effectués et le rapport annuel d'activité ;
- **le mécanisme d'alerte opérationnelle** : bien que documenté comme point d'extension dans le code, le branchement vers un système d'alerte en temps réel est prévu dans un chantier ultérieur du dispositif. En son absence, un échec de synchronisation prolongé n'est visible que dans les journaux techniques.

**Suivi des travaux restants.** Les éléments ci-dessus qui restent à réaliser sont suivis dans le référentiel interne de gestion de projet aux références suivantes.

| Travail restant | Lien de suivi |
|---|---|
| Rédaction des procédures LCB-FT écrites, dont la conduite à tenir en cas de correspondance *(prérequis non technique)* | [1216210976716063](https://app.asana.com/1/1213718564226627/project/1213723193546726/task/1216210976716063) |
| Surveillance des opérations atypiques et déclaration de soupçon *(épique E5)* | [1216210853624511](https://app.asana.com/1/1213718564226627/project/1213723193546726/task/1216210853624511) · [1216210853624512](https://app.asana.com/1/1213718564226627/project/1213723193546726/task/1216210853624512) · [1216210853624513](https://app.asana.com/1/1213718564226627/project/1213723193546726/task/1216210853624513) · [1216210853624514](https://app.asana.com/1/1213718564226627/project/1213723193546726/task/1216210853624514) |
| Conservation des pièces et rapport annuel *(épique E6)* | [1216210853624518](https://app.asana.com/1/1213718564226627/project/1213723193546726/task/1216210853624518) · [1216210853624517](https://app.asana.com/1/1213718564226627/project/1213723193546726/task/1216210853624517) · [1216210853624520](https://app.asana.com/1/1213718564226627/project/1213723193546726/task/1216210853624520) |
| Re-criblage des sujets déjà contrôlés après mise à jour du registre | *Aucune tâche de suivi identifiée dans le référentiel de projet* |
| Branchement du mécanisme d'alerte opérationnelle sur échec de synchronisation | *Aucune tâche de suivi identifiée dans le référentiel de projet* |
| Validation de la migration V58 sur un environnement exécutant les scripts de migration | *Aucune tâche de suivi identifiée dans le référentiel de projet* |

## 7. Situation de ce contrôle dans le dispositif d'ensemble

| Responsabilité du dispositif | État au 10 août 2026 |
|---|---|
| 1 — Désignation des responsables et cloisonnement des accès | Planifié |
| 2 — Classification des risques et procédures écrites | Planifié — la rédaction des documents conditionne les développements correspondants |
| 3 — Entrée en relation et vérification de l'association | Partiellement réalisé — voir fiches E3 |
| **4 — Gel des avoirs** | **Ce mécanisme de mise à jour automatique est réalisé. Le téléchargement du registre, son utilisation dans les contrôles individuels, le journal d'audit de ces contrôles, et l'enregistrement des situations de gel des avoirs sont également réalisés. Le mécanisme d'alerte opérationnelle est planifié.** |
| 5 — Surveillance des opérations atypiques et déclaration | Planifié |
| 6 — Conservation des documents et rapport annuel | Planifié |

## 8. Éléments de traçabilité

La mise en place de ce mécanisme est enregistrée dans l'historique des modifications du logiciel, sur la ligne de développement dédiée au dispositif LCB-FT. Elle comprend le mécanisme de téléchargement planifié, le verrou de non-concurrence, la gestion des états de succès et d'échec, l'écriture dans le journal d'audit, et les quatre contrôles automatisés décrits au point 5.

La fréquence de mise à jour (vingt-quatre heures) et le comportement en cas d'échec (continuation sur le dernier registre connu, enregistrement de l'échec, alerte) sont documentés dans les fichiers de configuration de la plateforme et dans le code lui-même. L'objectif est qu'une évolution future ne puisse pas modifier silencieusement ce comportement sans que les contrôles automatisés détectent la régression.

**Note sur la migration de base de données.** La migration V58 — qui crée la table d'état de synchronisation et étend la contrainte du journal d'audit — n'est pas exécutée par les contrôles automatisés : l'environnement de test utilise une base en mémoire régénérée à chaque exécution, sans exécution des scripts Flyway. La migration sera validée lors du premier déploiement sur l'environnement de qualification.

---

*Document établi le 10 août 2026. Une fiche de même nature sera produite pour chaque contrôle du dispositif LCB-FT au fur et à mesure de sa réalisation.*
