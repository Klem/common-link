# Identification des bénéficiaires effectifs d'une association

**Fiche de contrôle — dispositif de lutte contre le blanchiment de capitaux et le financement du terrorisme (LCB-FT)**

| | |
|---|---|
| **Entité** | CommonLink |
| **Nature du document** | Fiche descriptive d'un contrôle mis en place, destinée à la commission juridique |
| **Responsabilité concernée** | Entrée en relation d'affaires et vérification de l'identité du client *(responsabilité n° 3 du dispositif interne, épique E3)* |
| **Référence de suivi interne** | Tâche Asana n° `1216210976716068` — « Workflow d'identification du bénéficiaire effectif » |
| **Lien de suivi** | https://app.asana.com/1/1213718564226627/project/1213723193546726/task/1216210976716068 |
| **Priorité assignée** | P0 (la plus élevée) |
| **Date de réalisation** | 10 août 2026 |
| **État** | Réalisé et vérifié par contrôles automatisés. Non encore déployé — la mise en production de la plateforme est conditionnée à l'achèvement de l'ensemble du dispositif. |
| **Rédacteur** | Équipe technique CommonLink |

---

## 1. Avertissement sur la portée de ce document

Ce document décrit **un seul contrôle**, portant sur l'identification des bénéficiaires effectifs
dans le cadre de l'entrée en relation d'affaires.

Le dispositif LCB-FT de CommonLink est organisé en six responsabilités, dont celle-ci ne constitue
qu'un élément. Les cinq autres — désignation des responsables, classification des risques,
gel des avoirs, surveillance des opérations atypiques et déclaration, conservation des documents
et rapport annuel — sont **planifiées et non encore réalisées** à la date de ce document.
Leur état d'avancement fait l'objet d'un suivi distinct.

Aucune conclusion sur la conformité globale du dispositif ne peut être tirée de la présente fiche.

## 2. L'obligation couverte

La réglementation LCB-FT impose à CommonLink, en tant qu'organisme collectant des dons,
d'**identifier les bénéficiaires effectifs** de toute association avec laquelle elle entre en relation
avant d'autoriser l'ouverture d'une collecte.

Sans identification préalable des bénéficiaires effectifs, le dossier de vérification KYC d'une
association ne peut être approuvé. Ce contrôle ferme la dernière voie d'entrée en relation non
identifiée.

## 3. La règle de détermination appliquée

La règle appliquée est la définition réglementaire, reproduite ici en toutes lettres :

> **Les bénéficiaires effectifs d'une association sont les membres de l'organe d'administration,
> de direction et de surveillance. À défaut d'organe identifiable, ce sont les représentants
> légaux.**

Cette règle est appliquée sans aménagement.

## 4. Le processus d'identification

### 4.1 Source des informations

La plateforme dispose déjà d'un mécanisme de consultation des registres publics français
(registres nationaux des entreprises, Journal officiel des associations, base DJEPVA), qui fournit
automatiquement les noms des représentants d'une association au moment d'un contrôle.

Ces noms sont proposés au curateur à titre d'information, comme point de départ. Ils ne sont
**jamais retenus automatiquement** comme bénéficiaires effectifs.

### 4.2 Rôle du curateur

Le curateur — agent de CommonLink désigné pour instruire les dossiers — dispose d'un espace de
travail dédié, inaccessible à l'association elle-même, dans lequel il :

1. **consulte** les noms proposés issus des registres, ainsi que les statuts et procès-verbaux
   de nomination déposés par l'association ;
2. **retient** les personnes qu'il identifie comme bénéficiaires effectifs au sens de la règle
   ci-dessus, en précisant pour chacune le rôle, la date de naissance si elle est connue, et
   l'origine de l'information (registres publics, statuts, ou déclaration de l'association) ;
3. peut également **écarter** une personne précédemment retenue, si les pièces justificatives
   contredisent ou nuancent l'information initiale.

Chaque retenue et chaque écart est une **décision humaine, horodatée au moment où elle est prise,
et conservée dans le journal d'audit**. Aucune opération n'est effectuée automatiquement.

### 4.3 Conservation des données

L'identité (nom et date de naissance) de chaque bénéficiaire effectif est conservée sous
**forme chiffrée** dans la base de données de la plateforme. Le chiffrement est de type AES-256-GCM,
avec une clé dédiée aux données LCB-FT, distincte des autres clés de chiffrement de la plateforme.
En production, la présence de cette clé est vérifiée au démarrage de la plateforme — son absence
bloque le lancement. Une lecture directe de la base de données ne permet donc pas d'accéder au nom
ou à la date de naissance en clair dans un environnement de production correctement configuré.

Cette conservation repose sur une **obligation légale** : les éléments d'identification recueillis
dans le cadre des obligations LCB-FT doivent être conservés pendant une durée fixée par la
réglementation à compter de la fin de la relation d'affaires. La durée précise et les modalités de
purge feront l'objet d'une fiche distincte lors de la réalisation du contrôle de conservation des
documents.

Une personne écartée par le curateur n'est pas supprimée de la base : sa fiche est marquée comme
écartée et conservée, afin de maintenir un audit trail complet des décisions prises.

## 5. Le contrôle sur l'approbation KYC

L'approbation du dossier de vérification d'une association est **refusée** si aucune personne
physique n'a été retenue au dossier en qualité de **représentant légal**. Ce contrôle est appliqué
au même endroit que le contrôle de périmètre (vérification que l'association est bien régie par la
loi du 1er juillet 1901), décrit dans la fiche relative au contrôle de périmètre des associations.

L'article R. 561-3 du code monétaire et financier, dans sa rédaction issue du décret n° 2024-720 du
5 juillet 2024, assimile aux bénéficiaires effectifs les personnes physiques exerçant au sein d'une
association des fonctions d'administrateur, de membre de l'organe de surveillance ou de dirigeant.
Le verrou d'approbation porte en conséquence sur cette catégorie. **L'absence de bénéficiaire
effectif au sens strict — c'est-à-dire hors fonctions dirigeantes — ne bloque pas, à elle seule,
l'approbation.** La place de ce verrou dans la suite des conditions d'approbation est décrite par la
fiche *E4 — Contrôle de gel des avoirs à l'entrée en relation d'affaires*.

Une personne écartée ne compte pour aucun de ces contrôles : seules les personnes **retenues et non
écartées** sont prises en compte, et toutes sont filtrées contre le registre national des mesures de
gel, sans distinction de catégorie.

Ce refus est journalisé dans le journal d'audit de la plateforme, afin de conserver la trace de
toute tentative d'approbation sans identification préalable.

## 6. Les éléments de preuve

Les scénarios suivants sont couverts par des contrôles automatisés, réexécutés à chaque modification
du logiciel.

Le verrou décrit au point 5 porte une condition nécessaire et non suffisante : l'approbation exige
en outre un verdict de périmètre favorable et un résultat de filtrage sans correspondance ni
impossibilité sur l'association et sur chacune des personnes physiques retenues. Ces conditions
relèvent respectivement de la fiche de périmètre et de la fiche *E4 — Contrôle de gel des avoirs à
l'entrée en relation d'affaires*.

| Scénario vérifié | Résultat attendu | Vérifié par |
|---|---|---|
| Approbation d'un dossier sans représentant légal retenu | Refus, avec journalisation de l'événement | Contrôle automatisé — vérifié |
| Approbation d'un dossier avec un représentant légal retenu mais aucun bénéficiaire effectif au sens strict | L'approbation n'est pas bloquée de ce fait | Contrôle automatisé — vérifié |
| Ajout d'un bénéficiaire par le curateur | Journalisation de l'événement « bénéficiaire retenu » | Contrôle automatisé — vérifié |
| Écart d'un bénéficiaire par le curateur | Journalisation de l'événement « bénéficiaire écarté » | Contrôle automatisé — vérifié |
| Nom stocké en base : absence de valeur en clair | La valeur enregistrée est chiffrée et ne contient pas le nom saisi | Contrôle automatisé — vérifié |
| Date de naissance stockée en base : absence de valeur en clair | La valeur enregistrée est chiffrée et ne contient pas la date saisie | Contrôle automatisé — vérifié |
| Tentative d'accès aux fonctions de gestion des bénéficiaires par une association | Refus d'accès (ces fonctions sont réservées au curateur) — vérifié sur les trois fonctions concernées | Contrôle automatisé — vérifié |

Les deux vérifications portant sur le chiffrement en base sont exécutées dans un contexte de test
réaliste : une clé de chiffrement de longueur conforme (256 bits) est configurée, une entrée est
créée en base, et la valeur brute est lue directement par une requête SQL native pour vérifier
qu'elle ne contient pas le nom en clair. Ces vérifications prouvent que le chiffrement est actif de
bout en bout, et non seulement au niveau du code.

## 7. Ce que ce contrôle ne couvre pas

Cette précision est apportée pour éviter toute lecture extensive de la présente fiche.

- **L'analyse automatique du contenu des pièces déposées.** Aucune lecture automatisée des
  statuts, des procès-verbaux ou de tout autre document déposé par l'association n'est effectuée
  pour en extraire des noms. L'identification repose exclusivement sur la décision du curateur,
  éclairée par les registres publics et les pièces déposées qu'il examine lui-même.

- **Le contrôle des mesures de gel des avoirs.** Le gel des avoirs — vérification que les
  bénéficiaires effectifs identifiés ne figurent pas sur une liste de personnes ou d'entités
  faisant l'objet d'une mesure de gel — n'est **pas** couvert par ce contrôle. Il fait l'objet
  d'un chantier distinct, *traité depuis par les fiches de l'épique E4*.

- **La surveillance des opérations atypiques et la déclaration de soupçon.**

- **La conservation des pièces justificatives et le rapport annuel d'activité.**

- **La révision périodique des bénéficiaires effectifs déjà retenus.** L'identification est
  conduite à l'entrée en relation. Aucun réexamen programmé ne signale qu'un dirigeant a changé
  depuis l'approbation du dossier ; le dossier n'est repris que sur intervention d'un opérateur.

- **La vérification de l'identité des bénéficiaires effectifs par des pièces justificatives
  dédiées.** Ce contrôle enregistre les bénéficiaires effectifs identifiés, mais ne prévoit
  pas, dans cette version, de collecter une pièce d'identité propre à chaque bénéficiaire
  effectif. Cette extension est conditionnée aux décisions de la commission sur le niveau de
  diligence à appliquer selon la classification des risques.

Chacun de ces points fait l'objet de travaux planifiés.

**Suivi des travaux restants.** Les éléments ci-dessus qui restent à réaliser sont suivis dans le
référentiel interne de gestion de projet aux références suivantes.

| Travail restant | Lien de suivi |
|---|---|
| Adoption du document de classification des risques, dont dépend le niveau de diligence exigible sur les pièces d'identité *(prérequis non technique)* | [1216210976716062](https://app.asana.com/1/1213718564226627/project/1213723193546726/task/1216210976716062) |
| Rédaction des procédures LCB-FT écrites *(prérequis non technique)* | [1216210976716063](https://app.asana.com/1/1213718564226627/project/1213723193546726/task/1216210976716063) |
| Surveillance des opérations atypiques et déclaration de soupçon *(épique E5)* | [1216210853624511](https://app.asana.com/1/1213718564226627/project/1213723193546726/task/1216210853624511) · [1216210853624512](https://app.asana.com/1/1213718564226627/project/1213723193546726/task/1216210853624512) · [1216210853624513](https://app.asana.com/1/1213718564226627/project/1213723193546726/task/1216210853624513) · [1216210853624514](https://app.asana.com/1/1213718564226627/project/1213723193546726/task/1216210853624514) |
| Conservation des pièces justificatives et rapport annuel *(épique E6)* | [1216210853624518](https://app.asana.com/1/1213718564226627/project/1213723193546726/task/1216210853624518) · [1216210853624517](https://app.asana.com/1/1213718564226627/project/1213723193546726/task/1216210853624517) · [1216210853624520](https://app.asana.com/1/1213718564226627/project/1213723193546726/task/1216210853624520) |
| Collecte d'une pièce d'identité propre à chaque bénéficiaire effectif | *Aucune tâche de suivi identifiée dans le référentiel de projet* |
| Révision périodique programmée des bénéficiaires effectifs retenus | *Aucune tâche de suivi identifiée dans le référentiel de projet* |

## 8. Situation de ce contrôle dans le dispositif d'ensemble

| Responsabilité du dispositif | État au 10 août 2026 |
|---|---|
| 1 — Désignation des responsables et cloisonnement des accès | Planifié |
| 2 — Classification des risques et procédures écrites | Planifié — la rédaction des documents conditionne les développements correspondants |
| **3 — Entrée en relation et vérification de l'association** | **Plusieurs contrôles réalisés (vérification avant collecte, contrôle de périmètre loi 1901, identification des bénéficiaires effectifs). D'autres composantes de cette responsabilité restent planifiées.** |
| 4 — Gel des avoirs | Planifié — chantier prioritaire suivant |
| 5 — Surveillance des opérations atypiques et déclaration | Planifié |
| 6 — Conservation des documents et rapport annuel | Planifié |

## 9. Éléments de traçabilité

La réalisation de ce contrôle est enregistrée dans l'historique des modifications du logiciel,
sur la ligne de développement dédiée au dispositif LCB-FT.

Le journal d'audit de la plateforme (mis en place à l'occasion d'une étape antérieure du dispositif)
est utilisé pour tracer chaque retenue et chaque écart de bénéficiaire effectif, ainsi que chaque
refus d'approbation pour absence de représentant légal. Ce journal est protégé contre toute
modification ou suppression au niveau de la base de données.

La règle de détermination des bénéficiaires effectifs est documentée au sein même du logiciel, à
l'endroit où le contrôle est appliqué, afin qu'une évolution future ne puisse pas la modifier sans
que la commission en soit informée.

---

*Document établi le 10 août 2026. Une fiche de même nature est produite pour chaque contrôle du
dispositif LCB-FT au fur et à mesure de sa réalisation.*
