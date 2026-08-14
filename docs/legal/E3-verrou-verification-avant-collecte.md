# Vérification obligatoire de l'association avant l'ouverture d'une collecte de dons

**Fiche de contrôle — dispositif de lutte contre le blanchiment de capitaux et le financement du terrorisme (LCB-FT)**

| | |
|---|---|
| **Entité** | CommonLink |
| **Nature du document** | Fiche descriptive d'un contrôle mis en place, destinée à la commission juridique |
| **Responsabilité concernée** | Entrée en relation d'affaires et vérification de l'identité du client *(responsabilité n° 3 du dispositif interne, épique E3)* |
| **Référence de suivi interne** | Tâche Asana n° `1216210976716069` — « Durcir le verrou verified=true avant publishCampaign » |
| **Lien de suivi** | https://app.asana.com/1/1213718564226627/project/1213723193546726/task/1216210976716069 |
| **Priorité assignée** | P0 (la plus élevée) |
| **Date de correction** | 5 août 2026 |
| **État** | Corrigé et vérifié par contrôles automatisés. Non encore déployé — la mise en production de la plateforme est conditionnée à l'achèvement de l'ensemble du dispositif. |
| **Rédacteur** | Équipe technique CommonLink |

---

## 1. Avertissement sur la portée de ce document

Ce document décrit **un seul contrôle**, portant sur un seul point du parcours d'une association
sur la plateforme.

Le dispositif LCB-FT de CommonLink est organisé en six responsabilités, dont celle-ci ne constitue
qu'un élément. Les cinq autres — désignation des responsables, classification des risques,
gel des avoirs, surveillance des opérations atypiques et déclaration, conservation des documents
et rapport annuel — sont **planifiées et non encore réalisées** à la date de ce document. Leur
état d'avancement fait l'objet d'un suivi distinct.

Aucune conclusion sur la conformité globale du dispositif ne peut être tirée de la présente fiche.

## 2. Le risque constaté

Une association dont le dossier d'identification n'avait pas été validé par CommonLink pouvait
**ouvrir une collecte de dons auprès du public**.

Autrement dit, la vérification de l'identité et de l'existence juridique de l'association, qui
conditionne l'entrée en relation d'affaires, pouvait se trouver contournée au moment précis où
l'association commençait à solliciter des fonds.

Ce constat a été établi le 5 août 2026 lors d'un examen du parcours d'ouverture de collecte, mené
dans le cadre de la préparation du dispositif LCB-FT. Il n'a pas été signalé par un tiers et n'a
donné lieu à aucun incident : la plateforme n'encaisse aucun don réel à ce jour.

## 3. Pourquoi ce risque existait

Le parcours d'une association sur la plateforme comporte plusieurs étapes successives, chacune
conditionnée par la précédente :

1. dépôt du dossier d'identification et validation par CommonLink ;
2. signature du mandat fiscal, qui exige que le dossier soit validé ;
3. mise en place du moyen d'encaissement bancaire, qui exige que le mandat soit signé ;
4. ouverture d'une collecte de dons.

À la dernière étape, la plateforme vérifiait uniquement que le moyen d'encaissement bancaire était
en place. Ce contrôle **semblait suffisant** : puisque le moyen d'encaissement suppose le mandat,
et le mandat suppose la validation du dossier, une association arrivée à cette étape avait
nécessairement été validée.

Ce raisonnement comportait une faille. Chaque condition n'était vérifiée **qu'au moment où
l'étape correspondante était franchie**, et jamais réexaminée ensuite. Une association dont la
validation était retirée après coup — parce qu'une pièce se révélait irrégulière, parce que le
dossier était réexaminé, ou parce qu'une information nouvelle remettait en cause la décision
initiale — conservait donc la possibilité d'ouvrir une collecte, sur la seule base d'étapes
franchies antérieurement.

En résumé : la vérification était exigée **en amont du parcours**, mais plus **au moment de l'acte**
qui expose le public.

## 4. Le contrôle mis en place

L'ouverture d'une collecte est désormais **refusée** tant que le dossier d'identification de
l'association n'est pas validé. Le refus intervient quel que soit l'état des étapes antérieures,
et notamment quel que soit l'état du moyen d'encaissement bancaire.

Trois caractéristiques de ce contrôle méritent d'être soulignées devant la commission.

**Il est réévalué à chaque tentative.** Le contrôle ne consulte pas une décision antérieure mais
vérifie l'état du dossier au moment même de la demande d'ouverture. Un retrait de validation prend
donc effet immédiatement sur la capacité à ouvrir une collecte, sans intervention manuelle.

**Il est appliqué en deux endroits indépendants.** Le bouton d'ouverture de collecte est désactivé
dans l'interface de l'association, et la demande est également refusée par le serveur si elle lui
parvient malgré tout. Cette double application répond à un principe interne selon lequel toute
action de l'utilisateur doit être considérée comme pouvant être rejouée en contournant l'interface.
Un utilisateur techniquement averti ne peut donc pas obtenir l'ouverture d'une collecte en
contournant l'affichage.

**Il est indépendant des autres conditions.** Le contrôle ne s'appuie sur aucun raisonnement
indirect. Il examine directement l'état de validation du dossier. C'est précisément l'absence
d'un tel examen direct qui avait créé la faille décrite au point 3.

L'interface a par ailleurs été corrigée sur un point connexe : l'état du dossier
d'identification y était présenté comme une information indicative, sans effet sur la possibilité
d'ouvrir une collecte. Il est désormais présenté comme une condition bloquante, ce qui reflète la
règle réellement appliquée.

## 5. Les éléments de preuve

La correction est accompagnée de **six contrôles automatisés** ajoutés au patrimoine de tests de
la plateforme — trois portant sur le refus par le serveur, trois sur la désactivation du bouton
dans l'interface. Ces contrôles sont réexécutés à chaque modification du logiciel : si une
évolution future venait à rétablir la faille, la modification serait signalée en échec avant
d'être intégrée.

Dans tous les scénarios, le moyen d'encaissement bancaire est en place et pleinement opérationnel.
C'est le point essentiel de la démonstration : la seule condition qui change d'une ligne à l'autre
est l'état du dossier d'identification.

| État du dossier d'identification | Refus par le serveur | Bouton dans l'interface |
|---|---|---|
| Jamais soumis | Refusé — vérifié | Désactivé — vérifié |
| En cours d'examen | Refusé — vérifié | Désactivé — vérifié |
| **Refusé après examen** | **Refusé — vérifié** | **Désactivé — vérifié** |
| Validé | Ouverture autorisée | Bouton actif |

Les deux colonnes sont vérifiées **indépendamment** l'une de l'autre. Cela établit que le refus ne
repose pas sur le seul affichage : une demande qui parviendrait au serveur en contournant
l'interface serait refusée de la même manière.

Le troisième scénario est le plus significatif. Il reproduit exactement la situation du dossier
retiré après coup — une association dont la validation a été retirée alors que toutes les étapes
antérieures du parcours sont franchies — c'est-à-dire la faille décrite au point 3. Il constitue
la preuve que le contrôle traite bien le cas qui motivait la correction, et non seulement le cas
simple du dossier jamais soumis.

La dernière ligne est vérifiée par des contrôles préexistants, non ajoutés à cette occasion : elle
établit que la correction n'empêche pas une association régulièrement validée d'ouvrir une
collecte.

L'ensemble des contrôles automatisés de la plateforme a été exécuté après la correction, sans
régression sur les fonctionnements existants.

## 6. Ce que ce contrôle ne couvre pas

Cette précision est apportée pour éviter toute lecture extensive de la présente fiche.

Ce contrôle garantit qu'une collecte ne peut s'ouvrir sans validation préalable du dossier de
l'association. **Il ne porte ni sur le contenu de cette validation, ni sur les autres obligations
du dispositif.** En particulier, il ne couvre pas :

- la **vérification automatique de l'existence juridique** de l'association auprès des registres
  publics — *l'extension de portée annoncée ici est traitée depuis par la fiche E3 — Contrôle des
  registres publics d'associations* ; le déclenchement reste à la main d'un opérateur, choix
  délibéré, et aucune reconsultation programmée n'est en place ;
- l'**identification des bénéficiaires effectifs** de l'association — *formalisée depuis par la
  fiche E3 — Identification des bénéficiaires effectifs d'une association* ;
- le **contrôle des mesures de gel des avoirs**, ni sur l'association, ni sur ses représentants,
  ni sur les donateurs — *traité depuis par les fiches de l'épique E4* ;
- la **surveillance des opérations atypiques** et la déclaration de soupçon ;
- la **conservation** des pièces justificatives et le **rapport annuel** d'activité.

Chacun de ces points fait l'objet de travaux planifiés et ordonnancés, avec des dépendances
identifiées entre eux.

**Suivi des travaux restants.** Les éléments ci-dessus qui restent à réaliser sont suivis dans le
référentiel interne de gestion de projet aux références suivantes.

| Travail restant | Lien de suivi |
|---|---|
| Surveillance des opérations atypiques et déclaration de soupçon *(épique E5)* | [1216210853624511](https://app.asana.com/1/1213718564226627/project/1213723193546726/task/1216210853624511) · [1216210853624512](https://app.asana.com/1/1213718564226627/project/1213723193546726/task/1216210853624512) · [1216210853624513](https://app.asana.com/1/1213718564226627/project/1213723193546726/task/1216210853624513) · [1216210853624514](https://app.asana.com/1/1213718564226627/project/1213723193546726/task/1216210853624514) |
| Conservation des pièces justificatives et rapport annuel *(épique E6)* | [1216210853624518](https://app.asana.com/1/1213718564226627/project/1213723193546726/task/1216210853624518) · [1216210853624517](https://app.asana.com/1/1213718564226627/project/1213723193546726/task/1216210853624517) · [1216210853624520](https://app.asana.com/1/1213718564226627/project/1213723193546726/task/1216210853624520) |
| Traçage des refus d'ouverture de collecte et métriques du rapport annuel — **porte de mise en production** | [1217152626184665](https://app.asana.com/1/1213718564226627/project/1213723193546726/task/1217152626184665) |
| Reconsultation périodique programmée des registres publics | *Aucune tâche de suivi identifiée dans le référentiel de projet* |

## 7. Situation de ce contrôle dans le dispositif d'ensemble

| Responsabilité du dispositif | État au 5 août 2026 |
|---|---|
| 1 — Désignation des responsables et cloisonnement des accès | Planifié |
| 2 — Classification des risques et procédures écrites | Planifié — la rédaction des documents conditionne les développements correspondants |
| **3 — Entrée en relation et vérification de l'association** | **Ce contrôle est réalisé. Les autres composantes de cette responsabilité sont planifiées.** |
| 4 — Gel des avoirs | Planifié — chantier prioritaire suivant |
| 5 — Surveillance des opérations atypiques et déclaration | Planifié |
| 6 — Conservation des documents et rapport annuel | Planifié |

Il est à noter qu'une partie des travaux planifiés est **conditionnée à la remise de documents
non techniques** : la désignation signée des responsables, la classification des risques validée,
les procédures écrites, et le contrat encadrant le recours aux diligences d'un prestataire tiers.
Les développements correspondants ne peuvent être engagés avant, sous peine de transcrire dans le
logiciel des règles qui n'auraient pas été arrêtées.

## 8. Éléments de traçabilité

La correction est enregistrée dans l'historique des modifications du logiciel, sur une ligne de
développement dédiée au dispositif LCB-FT. Elle comprend la modification du contrôle côté serveur,
celle de l'interface, et les six contrôles automatisés décrits au point 5.

Le raisonnement exposé au point 3 — pourquoi la vérification indirecte par les étapes antérieures
était insuffisante — a été documenté au sein même du logiciel, à l'endroit où le contrôle
s'applique. L'objectif est qu'une évolution ultérieure ne supprime pas ce contrôle en le jugeant
redondant avec les conditions déjà vérifiées en amont du parcours. C'est cette apparence de
redondance qui avait produit la faille.

---

*Document établi le 5 août 2026. Une fiche de même nature sera produite pour chaque contrôle du
dispositif LCB-FT au fur et à mesure de sa réalisation.*
