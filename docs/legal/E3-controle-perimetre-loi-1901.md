# Contrôle automatique du périmètre d'intervention de la plateforme (associations relevant de la loi de 1901)

**Fiche de contrôle — dispositif de lutte contre le blanchiment de capitaux et le financement du terrorisme (LCB-FT)**

| | |
|---|---|
| **Entité** | CommonLink |
| **Nature du document** | Fiche descriptive d'un contrôle mis en place, destinée à la commission juridique |
| **Responsabilité concernée** | Entrée en relation d'affaires et vérification de l'identité du client *(responsabilité n° 3 du dispositif interne, épique E3)* |
| **Référence de suivi interne** | Tâche Asana n° `1216210976716070` — « Contrôle automatique catégorie 9220 (loi 1901) » |
| **Lien de suivi** | https://app.asana.com/1/1213718564226627/project/1213723193546726/task/1216210976716070 |
| **Priorité assignée** | P1 |
| **Date d'établissement** | 10 août 2026 — état décrit arrêté au 13 août 2026 |
| **État** | Réalisé et vérifié par contrôles automatisés. Non encore déployé — la mise en production de la plateforme est conditionnée à l'achèvement de l'ensemble du dispositif. |
| **Rédacteur** | Équipe technique CommonLink |

---

## 1. Avertissement sur la portée de ce document

Ce document décrit **un seul contrôle**, portant sur un seul point du parcours d'une association
sur la plateforme.

Le dispositif LCB-FT de CommonLink est organisé en six responsabilités, dont celle-ci ne constitue
qu'un élément. Les autres sont à des stades d'avancement différents — certaines partiellement
réalisées, d'autres planifiées et non encore engagées ; leur état est rappelé au point 7.

Aucune conclusion sur la conformité globale du dispositif ne peut être tirée de la présente fiche.

## 2. Le risque constaté

La plateforme CommonLink est réservée aux associations relevant de la loi du 1er juillet 1901
relative au contrat d'association. Cette restriction tient au modèle de fonctionnement de la
plateforme et à son agrément en tant qu'intermédiaire en financement participatif (IFP) : les
règles applicables en matière de collecte de dons et de certification fiscale visent spécifiquement
cette catégorie d'organisations.

En l'absence de contrôle automatique, il était possible qu'une structure d'une autre nature
juridique — syndicat professionnel, congrégation religieuse, fondation, ou toute autre entité
disposant d'un numéro d'identification mais ne relevant pas de la loi de 1901 — franchisse
l'étape de vérification du dossier et accède à la plateforme.

Ce constat a été établi lors de l'examen du dispositif LCB-FT mené en août 2026, dans le cadre
de la préparation à l'exercice des activités d'IFP. Il n'a pas été signalé par un tiers et n'a
donné lieu à aucun incident : la plateforme n'encaisse aucun don réel à ce jour.

## 3. Pourquoi ce risque existait

Le dossier d'identification soumis par une association comprend des pièces justificatives (statuts,
récépissé de déclaration, justificatif de représentant légal) et des informations déclarées par
l'association elle-même. Ces éléments permettent au curateur de vérifier l'existence et
l'identité de l'entité, mais pas systématiquement de confirmer sa catégorie juridique exacte au
sens de la nomenclature des organismes utilisée par l'administration française.

La vérification de la catégorie juridique suppose d'interroger les registres publics, qui recensent
les entités immatriculées en France et leur attribuent un code de catégorie juridique. Cette
consultation était réalisée à la discrétion du curateur, sans qu'aucun résultat ne soit
automatiquement pris en compte pour autoriser ou bloquer une décision d'approbation.

## 4. Le contrôle mis en place

### 4.1 La captation de la catégorie juridique

La catégorie juridique attribuée par l'INSEE est captée lors de chaque consultation des registres
publics et enregistrée de façon permanente et inaltérable sur la ligne de contrôle. Les modalités de
cette captation — sources interrogées, identification d'une association par son seul numéro RNA,
traitement des sources indisponibles — relèvent de la fiche *E3 — Contrôle des registres publics
d'associations*.

Un point mérite d'être souligné : la catégorie juridique est accessible **y compris pour une
association dépourvue de numéro SIREN**, identifiée par son seul numéro RNA. Le présent verrou
s'applique donc à ces dossiers.

### 4.2 Le périmètre accepté

Le périmètre est défini par l'ensemble des **formes déclarées de la famille INSEE `92`** (« association
loi 1901 ou assimilé ») :

| Code | Libellé INSEE | Retenu |
|---|---|---|
| `9210` | Association non déclarée | Non — dépourvue de personnalité morale, elle ne peut ni être enregistrée au RNA ni détenir de fonds |
| `9220` | Association déclarée | Oui |
| `9221` | Association déclarée « entreprises d'insertion par l'économique » | Oui |
| `9222` | Association intermédiaire | Oui |
| `9223` | Groupement d'employeurs | Oui |
| `9230` | Association déclarée reconnue d'utilité publique | Oui |
| `9240` | Congrégation | Non — régime cultuel distinct |
| `9260` | Association de droit local (Alsace-Moselle) | Oui — régime de 1908, équivalent au régime déclaré |

Deux formes de la famille `92` sont ainsi exclues, pour des motifs distincts : l'association non
déclarée n'a pas d'existence juridique propre, et la congrégation relève d'un régime cultuel qui
n'est pas celui de la loi de 1901. Toute catégorie extérieure à la famille `92` — société
commerciale, fondation, syndicat, parti politique — est hors périmètre.

L'association reconnue d'utilité publique (`9230`) et l'association de droit local d'Alsace-Moselle
(`9260`) sont **dans** le périmètre : la première est une association de la loi de 1901 et constitue
précisément le public visé par la plateforme ; la seconde relève d'un régime équivalent au régime
déclaré.

### 4.3 Le verdict de périmètre et ses effets

À partir de la catégorie captée, la plateforme détermine un **verdict de périmètre**, qui peut
prendre l'une des trois valeurs suivantes.

**Dans le périmètre.** La catégorie juridique figure parmi les formes retenues au point 4.2.
L'opérateur peut approuver le dossier normalement.

**Hors périmètre.** La catégorie juridique est connue mais ne figure pas parmi les formes retenues.
L'approbation du dossier est alors **refusée** par la plateforme, avec un message explicite
indiquant le motif. Un événement est simultanément inscrit dans le journal d'audit inaltérable du
dispositif LCB-FT, avec la catégorie constatée. **Ce refus n'est pas un rejet du dossier** : la
décision de rejeter ou de maintenir le dossier reste entièrement à la main de l'opérateur humain.
La plateforme refuse une approbation ; elle ne prononce aucune sanction.

**Indéterminé.** La catégorie juridique n'a pas pu être obtenue, soit parce que le répertoire public
n'a pas répondu au moment de la consultation, soit parce que l'entité ne disposait pas des
identifiants nécessaires à son interrogation. Dans ce cas, **l'approbation n'est pas bloquée**.
Cette troisième issue est indispensable : confondre « hors périmètre » et « source indisponible »
rendrait la plateforme inaccessible à chaque panne d'un service public, ce qui est juridiquement et
pratiquement inacceptable.

Il est important de souligner que **le verdict indéterminé est visible par le curateur**. L'absence
de catégorie connue ne passe pas inaperçue ; elle est signalée et peut motiver une demande de
complément d'information ou une nouvelle consultation lorsque le service public sera à nouveau
disponible. La liste des formes juridiques acceptées est affichée à l'opérateur dans les cas hors
périmètre et indéterminé, afin que le motif du verdict soit intelligible sans recours à la présente
fiche.

**Ce verdict qualifie la forme juridique et rien d'autre.** L'adéquation de l'objet de l'association
à la plateforme reste entièrement du ressort de l'examen manuel par l'opérateur.

## 5. Les éléments de preuve

Les scénarios suivants sont couverts par des contrôles automatisés, réexécutés à chaque modification
du logiciel : si une évolution future venait à désactiver ou contourner le contrôle de périmètre, la
modification serait signalée en échec avant d'être intégrée.

Ils distinguent la captation de la catégorie lors de la consultation des registres et le comportement
lors de la décision d'approbation.

| Situation | Catégorie captée | Verdict de périmètre | Approbation par l'opérateur | Événement au journal |
|---|---|---|---|---|
| Association déclarée (`9220`) | Catégorie retenue — vérifié | Dans le périmètre — vérifié | Autorisée — vérifié | — |
| Association reconnue d'utilité publique (`9230`) | Catégorie retenue — vérifié | Dans le périmètre — vérifié | Autorisée | — |
| Association de droit local d'Alsace-Moselle (`9260`) | Catégorie retenue — vérifié | Dans le périmètre — vérifié | Autorisée | — |
| Entité d'une catégorie hors du périmètre accepté (société commerciale, fondation, syndicat, congrégation) | Catégorie exclue — vérifié | Hors périmètre — vérifié | **Refusée — vérifié** | **Inscrit (demande de journalisation) — vérifié** |
| Répertoire public indisponible lors de la consultation | Non renseignée — vérifié | Indéterminé — vérifié | Autorisée — vérifié | — |
| Aucune consultation effectuée préalablement | — | Indéterminé | Autorisée — vérifié | — |

Les colonnes de résultat sont vérifiées **indépendamment** les unes des autres. En particulier, le
scénario de répertoire indisponible constitue la preuve que la plateforme ne bloque pas une
approbation en cas de panne d'un service public : c'est le risque le plus significatif à documenter,
car il n'est pas intuitif.

Les scénarios `9230` et `9260` sont les seconds en importance : ils démontrent qu'une association
parfaitement éligible n'est pas écartée par un périmètre trop étroit — le blocage d'une association
reconnue d'utilité publique serait aussi préjudiciable que l'admission d'une entité hors périmètre.

L'ensemble des contrôles automatisés de la plateforme a été exécuté, sans régression sur les
fonctionnements existants.

## 6. Ce que ce contrôle ne couvre pas

Cette précision est apportée pour éviter toute lecture extensive de la présente fiche.

Ce contrôle garantit que la catégorie juridique d'une association est vérifiée automatiquement
avant que son dossier puisse être approuvé, et que le résultat est enregistré de façon
inaltérable. **Il ne porte ni sur la vérification approfondie de l'identité des représentants,
ni sur les autres obligations du dispositif.** En particulier, il ne couvre pas :

- la **vérification documentaire** du dossier d'identification, qui reste à la charge du curateur ;
- l'**identification des bénéficiaires effectifs** de l'association, qui relève d'une fiche
  distincte — *E3 — Identification des bénéficiaires effectifs d'une association, livrée* ;
- le **contrôle des mesures de gel des avoirs**, ni sur l'association elle-même, ni sur ses
  représentants, ni sur les donateurs — il relève des fiches de l'épique E4, **livrées** ;
- la **réévaluation du verdict de périmètre** après l'entrée en relation — le verdict est établi
  à partir de la dernière consultation des registres, elle-même déclenchée par un opérateur ;
  aucune reconsultation programmée n'est en place ;
- la **surveillance des opérations atypiques** et la déclaration de soupçon ;
- la **conservation** des pièces justificatives et le **rapport annuel** d'activité.

Ce contrôle ne dispense pas non plus le curateur d'un examen attentif du dossier : le verdict
« dans le périmètre » signifie que la catégorie juridique est conforme, pas que l'ensemble du
dossier est irréprochable.

**Suivi des travaux restants.** Les éléments ci-dessus qui restent à réaliser sont suivis dans le
référentiel interne de gestion de projet aux références suivantes.

| Travail restant | Lien de suivi |
|---|---|
| Surveillance des opérations atypiques et déclaration de soupçon *(épique E5)* | [1216210853624511](https://app.asana.com/1/1213718564226627/project/1213723193546726/task/1216210853624511) · [1216210853624512](https://app.asana.com/1/1213718564226627/project/1213723193546726/task/1216210853624512) · [1216210853624513](https://app.asana.com/1/1213718564226627/project/1213723193546726/task/1216210853624513) · [1216210853624514](https://app.asana.com/1/1213718564226627/project/1213723193546726/task/1216210853624514) |
| Conservation des pièces justificatives et rapport annuel *(épique E6)* | [1216210853624518](https://app.asana.com/1/1213718564226627/project/1213723193546726/task/1216210853624518) · [1216210853624517](https://app.asana.com/1/1213718564226627/project/1213723193546726/task/1216210853624517) · [1216210853624520](https://app.asana.com/1/1213718564226627/project/1213723193546726/task/1216210853624520) |
| Reconsultation périodique programmée des registres, qui conditionne la réévaluation du verdict | *Aucune tâche de suivi identifiée dans le référentiel de projet* |

## 7. Situation de ce contrôle dans le dispositif d'ensemble

| Responsabilité du dispositif | État au 13 août 2026 |
|---|---|
| 1 — Désignation des responsables et cloisonnement des accès | Partiellement réalisé — rôle et espace de travail du responsable de la conformité livrés ; désignation formelle et référentiel de déclaration TRACFIN, actes hors plateforme, en cours |
| 2 — Classification des risques et procédures écrites | Partiellement réalisé — infrastructure livrée ; document de classification à approuver par l'organe compétent |
| **3 — Entrée en relation et vérification de l'association** | **Plusieurs contrôles réalisés : verrou de vérification avant ouverture de collecte, contrôle des registres publics, présent contrôle de périmètre, identification des bénéficiaires effectifs. La surveillance périodique et la révision des dossiers existants restent à compléter.** |
| 4 — Gel des avoirs | Substantiellement réalisé — registre, contrôles à l'entrée en relation et au don, journal immuable, alertes et traitement livrés ; re-filtrage périodique automatique, formalisation de la procédure de notification à la DG Trésor et purge à cinq ans restent à livrer |
| 5 — Surveillance des opérations atypiques et déclaration | Planifié |
| 6 — Conservation des documents et rapport annuel | Planifié |

Une partie des travaux restants est **conditionnée à la remise de documents non techniques** : la
désignation signée des responsables, la classification des risques validée et les procédures
écrites. Les développements correspondants ne peuvent être engagés avant, sous peine de transcrire
dans le logiciel des règles qui n'auraient pas été arrêtées.

## 8. Éléments de traçabilité

Le contrôle est enregistré dans l'historique des modifications du logiciel, sur la ligne de
développement dédiée au dispositif LCB-FT. Il comprend la migration de base de données conservant la
catégorie juridique sur la ligne de contrôle (**V55**), la logique de verdict en trois états, le
blocage de l'approbation en cas de verdict défavorable, l'inscription automatique au journal d'audit
inaltérable, et les contrôles automatisés décrits au point 5. La liste des formes juridiques
acceptées est applicative et n'a requis aucune migration.

Le choix des trois états — dans le périmètre, hors périmètre, indéterminé — et les raisons pour
lesquelles l'état indéterminé ne bloque pas l'approbation sont documentés directement dans le
logiciel, à l'endroit où la décision s'applique. L'objectif est qu'une évolution ultérieure ne
simplifie pas ce contrôle à deux états en pensant qu'il s'agit d'un cas non prévu : c'est un
choix délibéré, dont la suppression aurait des conséquences juridiques directes.

La liste des formes acceptées est elle aussi documentée à l'endroit où elle s'applique, avec le motif
d'exclusion de chacune des deux formes écartées de la famille `92`.

---

*Document établi le 10 août 2026, état arrêté au 13 août 2026. Une fiche de même nature est produite
pour chaque contrôle du dispositif LCB-FT au fur et à mesure de sa réalisation.*
