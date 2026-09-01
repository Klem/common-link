# Preuve de correspondance au registre de gel — éléments de décision du responsable de conformité

**Fiche de contrôle — dispositif de lutte contre le blanchiment de capitaux et le financement du terrorisme (LCB-FT)**

| | |
|---|---|
| **Entité** | CommonLink |
| **Nature du document** | Fiche descriptive d'un contrôle mis en place, destinée à la commission juridique |
| **Responsabilité concernée** | Gel des avoirs — motivation des décisions sur alerte *(responsabilité n° 4 du dispositif interne, épique E4)* |
| **Date d'établissement** | 13 août 2026 |
| **État** | Réalisé et vérifié par contrôles automatisés. Non encore déployé — la mise en production de la plateforme est conditionnée à l'achèvement de l'ensemble du dispositif. |
| **Rédacteur** | Équipe technique CommonLink |

---

## 1. Avertissement sur la portée de ce document

Ce document décrit **un seul contrôle**, portant sur les éléments mis à disposition du responsable
de conformité pour motiver une décision sur une alerte de gel.

Le dispositif LCB-FT de CommonLink est organisé en six responsabilités, dont celle-ci ne constitue
qu'un élément parmi d'autres au sein de la quatrième. Les autres responsabilités sont à des stades
d'avancement différents ; leur état est rappelé au point 7.

Aucune conclusion sur la conformité globale du dispositif ne peut être tirée de la présente fiche.

## 2. La situation prise en charge

La fiche *E4 — Traitement des alertes de gel et information à la Direction générale du Trésor* a
mis en place l'espace de travail permettant au responsable de conformité de prendre en charge une
alerte et de prononcer une décision motivée parmi trois valeurs : correspondance non avérée,
correspondance avérée, faux positif.

**Cette décision n'était pas motivable.** L'écran présentait l'origine de l'alerte, sa gravité,
son statut et son ancienneté, ainsi qu'un historique de criblage réduit au numéro de séquence, au
type d'événement et à l'horodatage. Le journal de conformité, seul dépositaire du résultat de
criblage, ne conserve pour une correspondance signalée que des données agrégées : date de
publication du registre consulté, seuil appliqué, nombre de correspondances, score le plus élevé.

Un responsable de conformité ouvrant une alerte lisait donc, en substance, « 3 correspondances,
score maximal 0,93 ». Il ne pouvait savoir **quelle entrée du registre** avait été rapprochée, ni
**sur quelle valeur** la comparaison avait porté. Aucun des trois choix de décision n'était
justifiable en l'état.

Un cas observé illustre la difficulté : une association nommée « TECHNO + » déclenche une alerte de
gravité haute. La valeur effectivement comparée est « TECHNO » — le criblage normalise les noms
avant comparaison — et elle atteint un score de 0,9333 contre l'entrée « TECHNOLAB » du registre.
Sans ces deux informations, ni le rapprochement ni le score ne sont intelligibles.

## 3. Contexte et cadre légal

L'obligation de gel est immédiate et non discrétionnaire, mais la qualification de la
correspondance est un **acte humain motivé**. Le responsable de conformité doit pouvoir établir,
et conserver la trace de, les éléments sur lesquels il s'est fondé — a fortiori lorsqu'il conclut
au faux positif et lève ainsi le blocage.

Deux exigences se rencontrent ici :

- **LCB-FT** — la décision doit être motivée et la motivation doit être vérifiable a posteriori
  par l'autorité de contrôle. Une motivation qui ne peut renvoyer à aucun élément conservé n'est
  pas opposable.
- **RGPD** — le principe de minimisation (art. 5.1.c) interdit de conserver des données
  personnelles au-delà de ce que la finalité exige.

La fiche *E4 — Journal des contrôles de gel* a arbitré cette rencontre en excluant
structurellement toute identité en clair des enregistrements du journal, au motif — cité au
paragraphe 4.5 de cette fiche — que « l'identité complète est déjà conservée dans les dossiers
d'identification auxquels la référence interne renvoie ».

**Lecture retenue pour le présent contrôle.** Ce motif vaut pour le **sujet criblé**, dont
l'identité figure effectivement au dossier. Il ne vaut pas pour **l'entrée du registre** : celle-ci
n'appartient à aucun dossier CommonLink, c'est une **publication officielle publique** (règlements
de l'Union européenne, résolutions du Conseil de sécurité des Nations unies, mesures nationales de
gel). Conserver la référence d'une entrée du registre et le score obtenu revient à conserver un
renvoi vers une donnée publique et une mesure de similarité.

> **Cette lecture est une interprétation d'un document dont la commission juridique est
> propriétaire. Elle doit être validée. Le présent contrôle a été construit sur cette lecture ;
> si la commission la rejette, la table décrite au paragraphe 4 doit être revue.**

## 4. Le contrôle mis en place

### 4.1 Une table de preuve distincte du journal

Le journal de conformité reste **inchangé** : son jeu de champs, dont l'absence d'identité en clair
est vérifiée par contrôle automatisé, n'a pas été modifié.

Les éléments de preuve sont enregistrés dans une **table distincte**, dotée de sa propre finalité
et de sa propre durée de conservation. Chaque correspondance signalée y produit une ligne par
entrée du registre rapprochée, comportant :

| Élément | Rôle dans la décision |
|---|---|
| Renvoi vers l'enregistrement du journal | Ancre la preuve à l'événement inaltérable qui a constaté le contrôle |
| Sujet criblé (nature et référence interne) | Identifie la partie contrôlée : association, représentant légal, bénéficiaire effectif, donateur |
| Valeur criblée normalisée | Rend le score intelligible : « TECHNO + » est comparé sous la forme « TECHNO » |
| Numéro de registre de l'entrée rapprochée | Renvoi public vers la mesure de gel |
| Dénomination, nature, date de naissance et référence légale de l'entrée | Permettent la discrimination |
| Score, seuil appliqué, algorithme | Rendent le rapprochement appréciable |
| Date de publication du registre consulté | Fixe la version du registre opposable |

**La preuve est écrite dans sa propre transaction, comme le journal.** Cette propriété est
essentielle et non accessoire : une correspondance est, par construction, constatée sur une
opération que la plateforme s'apprête à **refuser** — l'entrée en relation est bloquée, le don n'est
pas encaissé. Une preuve écrite dans la transaction de l'opération refusée serait annulée avec elle,
c'est-à-dire perdue précisément dans le seul cas où elle est nécessaire. La preuve est donc conservée
que l'opération soit acceptée ou refusée.

Une défaillance de l'enregistrement de la preuve ne masque jamais le résultat du criblage : celui-ci
conserve son issue bloquante, et la défaillance est journalisée.

### 4.2 Des valeurs figées et non des renvois

Les attributs de l'entrée du registre sont **recopiés au moment du criblage** et non lus à
l'affichage. Le registre est resynchronisé périodiquement et les entrées radiées sont supprimées :
un renvoi ferait disparaître la preuve d'une décision passée le jour de la radiation de l'entrée
concernée. Le même raisonnement vaut pour la valeur criblée, figée afin de résister à une
correction ultérieure du nom au dossier.

**Le re-criblage à la demande a été écarté comme source de preuve** pour ce motif : un criblage
rejoué trois jours après la décision peut renvoyer un jeu de correspondances différent, voire
aucun. La décision archivée ne correspondrait alors à aucun contrôle réellement effectué.

### 4.3 La discrimination effective

L'écran de traitement présente, en vis-à-vis :

- à gauche, **ce qui a été criblé** — la partie concernée, la valeur normalisée effectivement
  comparée, et, pour une association, son identité aux registres publics : SIREN, RNA, caractère
  actif du RNA, date du dernier contrôle ;
- à droite, **les conditions du criblage** — algorithme, seuil, version du registre, nombre de
  correspondances — puis le tableau des correspondances, une ligne par entrée rapprochée.

La référence légale de l'entrée est la colonne décisive : elle nomme le programme de sanction et est
recopiée dans la preuve au moment du rapprochement. Elle permet une motivation de la forme
« l'entrée rapprochée est une personne morale étrangère désignée au titre du programme X ; le sujet
est une association de droit français régie par la loi de 1901, dont le RNA est actif et le SIREN
vérifié » — motivation qu'aucun élément conservé ne permettrait d'établir en son absence.

### 4.4 Alerte en cas de contrôle impossible

La fiche *E4 — Journal des contrôles de gel* impose au paragraphe 4.4 l'enregistrement des
contrôles n'ayant pu être effectués, au motif qu'« un journal qui ne montrerait que les contrôles
ayant abouti serait trompeur ».

**La surface d'alerte reproduisait exactement ce silence** : seules les correspondances signalées
produisaient une alerte. Un criblage empêché — registre non ingéré, dirigeants absents du dossier,
incident technique — était journalisé et n'apparaissait sur aucun écran. Un contrôle obligatoire
pouvait donc être omis sans que quiconque en soit informé. Un cas de cette nature a été observé sur
le criblage des donateurs.

Une catégorie d'alerte distincte couvre les criblages impossibles. Sa gravité est **moyenne** et non
haute : un criblage empêché signifie que la plateforme **ignore** s'il existe une correspondance, là
où une correspondance signalée signifie qu'elle en a détecté une. Les deux situations appellent des
diligences différentes et ne doivent pas être confondues sur un même écran.

### 4.5 Décisions antérieures sur le même sujet

La clôture d'une alerte étant irréversible et toute nouvelle correspondance créant une nouvelle
alerte, un sujet dont le nom dépasse durablement le seuil déclenche une alerte à chaque opération.
L'écran présente les décisions déjà rendues sur le même sujet, avec leur motivation.

**Cet affichage est strictement informatif.** Aucune alerte n'est supprimée, clôturée ou masquée
automatiquement. Une liste d'exclusion qui survivrait à une évolution du registre constituerait une
faille du dispositif : un nom écarté aujourd'hui peut être désigné demain au titre d'un programme
qui n'existait pas. Chaque alerte reste examinée pour elle-même.

## 5. Les éléments de preuve

### 5.1 Contrôles automatisés côté serveur

Les scénarios suivants sont couverts par des contrôles automatisés.

| Scénario | Résultat attendu | Vérifié |
|---|---|---|
| Correspondance signalée sur l'association | Une ligne de preuve par entrée rapprochée, ancrée à l'enregistrement du journal | Oui |
| Valeur criblée enregistrée | La forme normalisée effectivement comparée, non le nom brut | Oui |
| Attributs de l'entrée du registre | Recopiés et non lus par renvoi | Oui |
| Criblage sans correspondance | Aucune ligne de preuve créée | Oui |
| Alerte créée sur correspondance | Porte la référence de séquence de l'enregistrement du journal | Oui |
| Registre non ingéré | Alerte de criblage impossible créée | Oui |
| Dirigeants absents du dossier | Alerte de criblage impossible créée | Oui |
| Échec d'enregistrement de la preuve | Le résultat du criblage reste inchangé et bloquant | Oui |
| **Refus de l'opération après correspondance** | **La preuve subsiste alors que l'opération est annulée** | **Oui** |
| **Référence légale de l'entrée rapprochée** | **Recopiée dans la preuve** | **Oui** |
| **Correspondance relevée sur un représentant légal** | **Reste rattachée à l'association qui porte l'alerte** | **Oui** |

Deux scénarios méritent d'être soulignés.

La capture de la preuve ne doit jamais masquer le résultat du criblage, qui est le signal bloquant
sur lequel agit l'appelant. Une défaillance de l'enregistrement de la preuve est journalisée et le
contrôle conserve son issue.

Le scénario du refus est celui qui garantit la propriété décrite au point 4.1. Il ne peut être
vérifié qu'en exerçant réellement l'annulation de l'écriture : un contrôle automatisé qui se
contenterait d'observer que l'ordre d'enregistrement a été émis serait sans valeur, puisqu'un ordre
émis puis annulé ne conserve rien.

### 5.2 Contrôles automatisés côté interface

Des contrôles automatisés couvrent l'écran de traitement. Ils vérifient notamment : la désignation
de l'entrée rapprochée et de son programme de sanction, l'affichage de la valeur normalisée,
l'affichage de l'identité aux registres publics, la présentation de l'ensemble des correspondances
et non de la seule meilleure, l'état explicite lorsqu'aucune comparaison n'a eu lieu, et le
comportement lorsque le dossier du sujet ne peut plus être résolu.

### 5.3 Ce qui n'est pas couvert par un contrôle automatisé

La restriction d'accès par rôle demeure une propriété de la configuration de sécurité, non couverte
par un contrôle automatisé de niveau contrôleur — l'infrastructure de test de la plateforme ne
prend pas en charge ce type de vérification.

## 6. Ce que ce contrôle ne couvre pas

**Alertes dépourvues d'éléments de preuve.** Les alertes émises avant que la preuve ne soit écrite
dans une transaction distincte de celle de l'opération contrôlée en sont dépourvues : l'annulation de
l'écriture d'une opération refusée emportait alors la preuve avec elle, tandis que le journal —
écrit dans une transaction propre — y échappait. Cette preuve **ne peut pas être reconstituée** : un
re-criblage produirait un résultat postérieur à la décision, ce que le point 4.2 écarte
expressément. Ces alertes doivent être traitées sur la base du journal seul, et cette limite
mentionnée dans la motivation de la décision.

**Qualification de la correspondance.** La plateforme met des éléments à disposition ; elle ne
qualifie rien. La décision demeure un acte humain motivé du responsable de conformité.

**Décision automatisée.** Aucune décision n'est prise automatiquement sur la base du criblage. Le
criblage produit un blocage et une alerte ; la qualification relève d'une personne physique
désignée. Ce point est mentionné au regard de l'article 22 du RGPD.

**Droit d'information de la personne concernée.** L'articulation entre le droit d'information des
articles 13 et 14 du RGPD et les dérogations applicables en matière de LCB-FT n'est pas tranchée
par le présent contrôle. **Ce point est ouvert et doit être arbitré par la commission juridique.**

**Traçabilité des consultations.** L'identité du responsable de conformité est enregistrée lors de
la prise en charge et de la clôture d'une alerte, non lors de la simple consultation. Cet écart est
signalé et n'est pas couvert.

**Purge à cinq ans.** La durée de conservation retenue est de cinq ans, alignée sur l'article
L.561-12 du code monétaire et financier. Le mécanisme de purge effective à l'échéance n'est pas
réalisé et relève de la responsabilité n° 6 du dispositif.

**Seuil et algorithme de rapprochement.** Le criblage applique un score composite phonétique +
orthographique par blocs (DoubleMetaphone × Jaro-Winkler, moyenne géométrique pondérée) au seuil
de 0,85. Le nom est découpé en blocs significatifs (les particules « DE », « AL », « VAN », etc.
sont filtrées) ; chaque bloc est encodé phonétiquement (DoubleMetaphone) puis comparé aux blocs du
candidat via une matrice de similarité glouton. Cette approche est insensible à l'ordre des
prénoms/noms, aux particules, aux variantes phonétiques rapprochées et aux noms cyrilliques ou
arabes translittérés. Limitation résiduelle : le seuil de 0,85 a été calibré pour le Jaro-Winkler
initial ; une recalibration empirique sur le registre en production est recommandée avant de le
considérer comme définitif. **Abaisser le rappel d'un criblage de sanctions est une décision
juridique.** Le point est porté à la connaissance de la commission.

**Suivi des travaux restants.** La majorité des points ci-dessus n'appelle pas un développement
mais un **arbitrage de la commission juridique** ou constitue une **limite déclarée et
irrécupérable** : ils ne correspondent à aucune tâche du référentiel de projet, et il serait
trompeur de leur en attribuer une. Seuls les éléments suivants relèvent d'un travail à conduire.

| Travail restant | Lien de suivi |
|---|---|
| Politique de rétention à cinq ans et purge effective à l'échéance *(épique E6)* | [1216210853624517](https://app.asana.com/1/1213718564226627/project/1213723193546726/task/1216210853624517) |
| Archivage immuable des pièces, préalable à une purge qui ait une portée *(épique E6)* | [1216210853624518](https://app.asana.com/1/1213718564226627/project/1213723193546726/task/1216210853624518) |
| Revue de sécurité du dispositif, dont la restriction d'accès par rôle non couverte au point 5.3 *(épique E6)* | [1216210853624519](https://app.asana.com/1/1213718564226627/project/1213723193546726/task/1216210853624519) |
| Traçabilité de la simple consultation d'un dossier d'alerte | *Aucune tâche de suivi identifiée dans le référentiel de projet* |

Les points suivants sont **soumis à la commission et sans tâche de suivi technique** : la validation
de la lecture retenue au point 3 sur la conservation de la référence des entrées du registre, le
seuil et l'algorithme de rapprochement, l'articulation entre les articles 13 et 14 du RGPD et les
dérogations LCB-FT, et le traitement des alertes dépourvues d'éléments de preuve — dont la preuve
ne peut pas être reconstituée.

## 7. Situation de ce contrôle dans le dispositif d'ensemble

| Responsabilité du dispositif | État au 13 août 2026 |
|---|---|
| 1 — Désignation des responsables et cloisonnement des accès | Partiellement réalisé — rôle et espace de travail du responsable de la conformité livrés ; désignation formelle et référentiel de déclaration TRACFIN, actes hors plateforme, en cours |
| 2 — Classification des risques et procédures écrites | Partiellement réalisé — infrastructure livrée ; document de classification à approuver par l'organe compétent |
| 3 — Entrée en relation et vérification de l'association | Partiellement réalisé — contrôles E3 livrés ; surveillance périodique à compléter |
| **4 — Gel des avoirs** | **Substantiellement réalisé — registre, contrôles à l'entrée en relation et au don, journal d'audit, alertes, traitement opérateur et éléments de décision livrés. Re-filtrage périodique automatique non réalisé. Procédure interne de notification DG Trésor non formalisée. Purge à cinq ans non réalisée.** |
| 5 — Surveillance des opérations atypiques et déclaration | Planifié |
| 6 — Conservation des documents et rapport annuel | Planifié |

## 8. Éléments de traçabilité

Le développement est enregistré dans l'historique des modifications du logiciel, sur la branche
dédiée au dispositif LCB-FT (`app-legal-lcb-ft`). Il comprend :

- la migration de base de données créant la table de preuve et étendant les catégories d'alerte
  (migration **V64**, avec script de retour arrière) ;
- l'enregistrement des correspondances dans les services de criblage à l'entrée en relation et au
  don, dans une transaction distincte de celle de l'opération contrôlée ;
- la propagation de la référence légale de l'entrée rapprochée jusqu'à la preuve ;
- le rattachement de l'alerte à l'enregistrement du journal qui l'a justifiée ;
- l'enrichissement de l'écran de traitement réservé au responsable de conformité ;
- les contrôles automatisés décrits au point 5, dont celui qui vérifie que la preuve subsiste au
  refus de l'opération.

Les règles — valeurs figées et non renvoyées, écriture en transaction propre, journal inchangé,
conservation à cinq ans, caractère informatif des décisions antérieures, absence de qualification
automatique — sont documentées dans le code source, à l'endroit où elles s'appliquent.

---

*Document établi le 13 août 2026. Une fiche de même nature est produite pour chaque contrôle du
dispositif LCB-FT au fur et à mesure de sa réalisation.*
