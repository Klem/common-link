# Suivi de l'état du dossier de l'association chez le prestataire de paiement

**Fiche descriptive — mécanisme hors dispositif LCB-FT, recensé pour son effet sur l'ouverture des collectes**

| | |
|---|---|
| **Entité** | CommonLink |
| **Nature du document** | Fiche descriptive d'un mécanisme mis en place, destinée à la commission juridique |
| **Rattachement** | Aucune des six responsabilités du dispositif LCB-FT *(voir point 1)*. Le mécanisme alimente la condition « prestataire » du verrou cumulatif décrit par la fiche *E3 — Non-délégation des diligences au prestataire de paiement*, point 4.1. |
| **Référence de suivi interne** | *(non renseigné — aucune tâche du référentiel de projet n'a été identifiée pour ce livrable)* |
| **Date** | 12 octobre 2026 |
| **État** | Réalisé et vérifié par contrôles automatisés, à l'exception du contrôle signalé au point 4. Non encore déployé — la mise en production de la plateforme est conditionnée à l'achèvement de l'ensemble du dispositif. |
| **Rédacteur** | Équipe technique CommonLink |

---

## 1. Avertissement sur la portée de ce document

Ce document décrit la manière dont la plateforme se tient informée de l'avancement du dossier
qu'une association constitue auprès du prestataire de paiement (Mollie), et ce qu'elle en fait.
Depuis le 2 novembre 2026, ce mécanisme ne concerne que les associations rattachées à Mollie ;
HelloAsso, attribué par défaut aux nouvelles associations, n'est pas intégré et ne fait l'objet
d'aucun suivi.

**Ce mécanisme ne relève pas du dispositif LCB-FT de CommonLink et n'en satisfait aucune
obligation.** La vérification conduite par le prestataire l'est pour son propre compte. La fiche
*E3 — Non-délégation des diligences au prestataire de paiement* établit que CommonLink ne s'appuie
pas sur cette vérification pour ses propres diligences ; la présenter ici comme un contrôle LCB-FT
de CommonLink contredirait cette conclusion. Le mécanisme est décrit parce qu'il détermine le
moment où la seconde condition d'ouverture d'une collecte — la validation par le prestataire — est
considérée comme remplie, et le moment où elle cesse d'être réexaminée.

Aucune conclusion sur la conformité du dispositif LCB-FT ne peut être tirée de la présente fiche.

## 2. Le besoin

Une association ne peut ouvrir une collecte que si deux conditions sont réunies : son dossier est
validé par CommonLink, et sa procédure de vérification auprès du prestataire de paiement est
achevée, avec l'autorisation d'encaisser des paiements. La seconde condition est appréciée sur la
base de l'état du dossier **tel que la plateforme l'a enregistré lors de sa dernière consultation
du prestataire**.

Le prestataire ne prévient pas la plateforme lorsque l'état du dossier change : il ne propose
aucun mécanisme de notification pour cette procédure. La plateforme doit donc l'interroger
elle-même. Avant le 12 octobre 2026, elle ne le faisait que lorsque l'association affichait son
espace : un dossier pouvait être validé chez le prestataire sans que la plateforme le sache tant
que l'association ne revenait pas, et une demande de complément formulée par le prestataire
pendant son examen pouvait passer inaperçue.

## 3. Le mécanisme mis en place

### 3.1 Trois occasions de consulter le prestataire

L'état du dossier chez le prestataire est désormais relu à trois occasions, qui aboutissent toutes
au même enregistrement.

| Occasion | Fréquence maximale |
|---|---|
| **Affichage de l'espace de l'association** | Une consultation du prestataire au plus toutes les cinq minutes par association ; entre deux, l'état déjà enregistré est affiché. |
| **Consultation périodique automatique**, sans action de l'association | Toutes les cinq minutes pour un dossier « à compléter », « en cours de vérification », ou validé sans autorisation d'encaisser. Toutes les heures pour un dossier resté « à compléter » depuis plus de sept jours. Un dossier « en cours de vérification » reste consulté toutes les cinq minutes, quelle que soit la durée de l'examen. |
| **Actualisation demandée par l'association** depuis son espace | Une consultation du prestataire au plus toutes les trente secondes par association ; au-delà, l'état déjà enregistré est renvoyé. |

Tant que l'onglet consacré au compte bancaire reste ouvert, l'espace de l'association relit l'état
enregistré chaque minute, et demande une actualisation lorsque l'association revient sur l'onglet
(par exemple après avoir rempli son dossier dans la fenêtre du prestataire). Le navigateur de
l'association n'interroge jamais lui-même le prestataire : il lit ce que la plateforme a
enregistré.

Pour les dossiers antérieurs au 12 octobre 2026, la date à partir de laquelle un dossier est
considéré comme « à compléter » est approchée par la date de première connexion au prestataire,
seule information disponible : un tel dossier peut donc passer à la consultation horaire plus tôt
qu'il ne l'aurait fait si la date exacte était connue.

*Complément du 2 novembre 2026.* La consultation périodique automatique ne porte que sur les
associations dont le prestataire est Mollie. Une association passée de Mollie à un autre prestataire
conserve sa connexion Mollie, mais celle-ci n'est plus consultée périodiquement.

### 3.2 Un compte qui encaisse déjà n'est plus consulté

Dès qu'un dossier est validé par le prestataire **et** que l'autorisation d'encaisser est accordée,
aucune des trois occasions ne consulte plus le prestataire pour cette association. Cette règle est
appliquée à chacune d'elles, et l'enregistrement refuse en outre d'écraser l'état d'un compte qui
s'est mis à encaisser pendant qu'une consultation était en cours.

Ce choix est délibéré : l'état enregistré commande directement l'ouverture des collectes, des
paiements de dons et des modules de collecte de l'association. Enregistrer automatiquement une
régression observée chez le prestataire couperait donc automatiquement les collectes — l'inverse
de l'orientation retenue le 9 octobre 2026, selon laquelle une telle régression doit être signalée
à la conformité et tranchée par un humain. Les conséquences de ce choix sont exposées au point 6.

Un dossier validé **sans** autorisation d'encaisser continue d'être consulté : l'association ne
collecte pas encore, de sorte qu'aucune collecte ouverte ne peut être interrompue par cette
consultation ; seule l'ouverture de la collecte peut en résulter.

### 3.3 Ce qui est enregistré

À chaque consultation, la plateforme enregistre exclusivement des indicateurs d'état de la
relation avec le prestataire : l'état du dossier, les autorisations d'encaisser et de recevoir des
virements, l'adresse à laquelle l'association reprend son dossier chez le prestataire, la date de
la consultation, la date depuis laquelle l'état courant est observé, et le fait qu'un examen par le
prestataire a déjà été observé. Aucune donnée d'identification de l'association, de ses
représentants ou de ses bénéficiaires effectifs n'en provient (voir la fiche *E3 — Non-délégation*,
point 4.2).

### 3.4 « Complément demandé »

Lorsqu'un dossier a été observé « en cours de vérification » puis redevient « à compléter »,
l'espace de l'association l'affiche comme un **complément demandé par le prestataire** et l'invite
à reprendre son dossier. Ce signal repose sur les seules observations de la plateforme : un examen
qui commencerait et s'achèverait entre deux consultations, soit en moins de cinq minutes, ne serait
pas observé, et la demande de complément serait alors présentée comme un dossier simplement « à
compléter ».

### 3.5 Le paiement test

Le prestataire exige une première transaction pour achever la validation d'un compte. Selon sa
réponse écrite du 9 octobre 2026, un paiement de 0,01 € effectué par l'association elle-même depuis
son compte chez le prestataire suffit et déclenche l'examen. Le parcours de l'association
l'explique à l'étape « Paiement test ».

**Il s'agit d'une consigne, et non d'un état constaté.** La plateforme ne détecte pas ce paiement
et ne l'enregistre nulle part. La case « J'ai fait mon paiement test » que l'association peut
cocher n'est conservée que par son navigateur, le temps de l'affichage : elle n'est transmise ni à
la plateforme ni au journal, et disparaît au rechargement de la page. Les frais de transaction
éventuellement prélevés par le prestataire sur ce paiement ne sont pas connus ; le parcours ne le
présente jamais comme gratuit.

### 3.6 Effets sur les courriels adressés à l'association

Les courriels existants qui informent l'association d'un changement d'état de son dossier partent
désormais dès que la consultation périodique observe ce changement, sans attendre une visite. Dans
un cas particulier — l'autorisation d'encaisser accordée après la validation du dossier — le
courriel annonçant la validation peut être envoyé une seconde fois ; ce doublon est accepté.

## 4. Les éléments de preuve

Les contrôles automatisés ci-dessous ont été écrits pour ce mécanisme. Sauf mention contraire, ils
ont été exécutés avec succès le 12 octobre 2026, sur un environnement qui ne requiert pas de base de
données réelle.

| Ce qui est vérifié | État |
|---|---|
| Un compte qui encaisse n'est jamais consulté par la consultation périodique, quelle que soit l'ancienneté de sa dernière consultation | Exécuté — vérifié |
| Un compte qui encaisse n'est consulté ni à l'affichage, ni sur actualisation demandée | Exécuté — vérifié |
| L'enregistrement n'écrase jamais l'état d'un compte qui encaisse | Exécuté — vérifié |
| Un dossier validé sans autorisation d'encaisser reste consulté, et l'octroi de cette autorisation est signalé une seule fois | Exécuté — vérifié |
| Un dossier « à compléter » depuis plus de sept jours passe à la consultation horaire ; en deçà, la cadence de cinq minutes est conservée | Exécuté — vérifié |
| Un dossier « en cours de vérification » n'est jamais traité comme en sommeil, même après plusieurs semaines | Exécuté — vérifié |
| L'affichage conserve sa limite de cinq minutes ; l'actualisation demandée la contourne, mais deux demandes rapprochées n'entraînent qu'une consultation | Exécuté — vérifié |
| La limite d'actualisation s'applique par association, et une association ne peut actualiser que son propre dossier | Exécuté — vérifié |
| Une défaillance du prestataire, ou d'une association, n'interrompt pas le traitement des autres et ne produit aucune erreur pour l'association | Exécuté — vérifié |
| Lorsque la consultation du prestataire est désactivée, ni la consultation périodique ni l'actualisation demandée n'interrogent le prestataire | Exécuté — vérifié |
| « Complément demandé » n'apparaît qu'après un examen observé, jamais sur un premier dossier « à compléter » | Exécuté — vérifié |
| Côté espace de l'association : actualisation au retour sur l'onglet, relecture chaque minute, paiement test présenté comme consigne et case conservée localement seulement, paiement test jamais qualifié de gratuit | Exécuté — vérifié |
| **Au niveau de la base de données** : la sélection des dossiers soumis à la consultation périodique exclut les comptes qui encaissent et les liens rompus | **Écrit, non exécuté** — ce contrôle requiert un moteur de conteneurs indisponible lors de la livraison, et la porte de déploiement écarte cette famille de contrôles (voir la synthèse, section 1) |

Pour la garantie du point 3.2, les contrôles exécutés portent sur la règle appliquée par chacune
des trois occasions et par l'enregistrement ; le contrôle qui porterait sur la sélection effectuée
par la base de données ne l'a pas été.

## 5. Conditions d'exploitation

La consultation du prestataire est **désactivée** dans les environnements de développement et de
recette, et activée dans la configuration de production. En recette, ni la consultation
périodique, ni l'actualisation demandée, ni le signal « complément demandé » ne sont donc
observables ; seule une simulation de la validation est disponible, réservée à ces environnements.
Aucun des comportements décrits au point 3 n'a, à ce jour, été observé sur un dossier réel.

La limite de trente secondes de l'actualisation demandée est tenue en mémoire par chaque serveur :
elle est remise à zéro au redémarrage et, si plusieurs serveurs fonctionnent simultanément,
s'applique à chacun séparément. Elle protège le prestataire contre des sollicitations répétées ;
elle n'a aucune portée de conformité.

## 6. Ce que ce mécanisme ne couvre pas

Cette précision est apportée pour éviter toute lecture extensive de la présente fiche.

**Écart ouvert — la perte de validation d'un compte qui encaisse déjà n'est pas détectée.** Cette
limite n'est pas introduite par le présent mécanisme : auparavant, la plateforme cessait déjà de
consulter le prestataire dès la validation du dossier, que l'autorisation d'encaisser soit accordée
ou non. Elle a été rendue explicite et restreinte aux seuls comptes qui encaissent le 12 octobre
2026. Au regard du point 3.2, si le prestataire retirait sa validation ou l'autorisation d'encaisser à une
association dont les collectes sont ouvertes, la plateforme ne le constaterait pas : elle
continuerait de tenir la condition « prestataire » pour remplie, et les collectes de cette
association resteraient ouvertes de son côté. Aucune alerte n'est émise et aucun humain n'en est
informé par la plateforme. Ce que le prestataire ferait lui-même, dans ce cas, des paiements qui
lui seraient présentés n'est pas établi et n'a pas été vérifié.

Cet écart n'est pas « hors périmètre » : il résulte d'une question laissée ouverte le 12 octobre
2026. Deux voies sont identifiées — conserver l'état qui commande l'ouverture des collectes et
enregistrer à part l'état observé jusqu'à décision de la conformité, ou accepter la coupure
automatique et revenir sur l'orientation du 9 octobre 2026. Le choix entre elles est renvoyé à un
chantier dédié, non planifié à la date de la présente fiche.

Seule la **rupture du lien technique** entre la plateforme et le compte de l'association chez le
prestataire — notamment lorsque l'association retire l'autorisation qu'elle avait donnée à
CommonLink — est détectée, lors du renouvellement périodique de l'accès — effectué depuis le
2 novembre 2026 pour les seules associations rattachées à Mollie : la collecte est alors
suspendue automatiquement. Ce comportement est antérieur au présent mécanisme et n'en dépend pas ;
il ne renseigne pas sur l'état de la validation chez le prestataire.

Ce mécanisme ne couvre pas non plus :

- la **traçabilité des changements d'état** du dossier chez le prestataire : aucun changement
  n'est inscrit au journal d'audit, et aucun n'alerte la fonction conformité ; seuls des courriels
  sont adressés à l'association. Ce point dépend de la question ouverte ci-dessus ;
- la **détection du paiement test** (point 3.5) ;
- la **confirmation, sur un dossier réel, que le paiement test suffit** à déclencher l'examen du
  prestataire : l'étape a été livrée sur la foi de la réponse écrite du prestataire du 9 octobre
  2026, sans constat sur le dossier de l'association pilote ; elle est à ajuster dès ce constat ;
- l'**actualisation du reste de l'espace de l'association** : seul l'onglet consacré au compte
  bancaire se tient à jour en continu ; les autres écrans peuvent présenter un état en retard
  jusqu'au rechargement de la page. Certains d'entre eux peuvent en outre présenter comme validé un
  dossier validé sans autorisation d'encaisser. Ces écarts sont d'affichage seulement : la
  condition appliquée par le serveur à l'ouverture d'une collecte n'en dépend pas ;
- la **qualité ou la portée de la vérification conduite par le prestataire**, sur laquelle la
  plateforme ne porte aucun jugement.

**Suivi des travaux restants.**

| Travail restant | Lien de suivi |
|---|---|
| Conduite à tenir lorsqu'un compte qui encaisse perd sa validation chez le prestataire — détection, alerte de la conformité, absence de coupure automatique | *Aucune tâche de suivi identifiée dans le référentiel de projet* |
| Journal des changements d'état du dossier chez le prestataire et alerte de régression — conditionné au point précédent | *Aucune tâche de suivi identifiée dans le référentiel de projet* |
| Constat, sur le dossier de l'association pilote, de l'effet du paiement test | *Action hors développement — aucune tâche de suivi identifiée* |

## 7. Éléments de traçabilité

Le mécanisme a été livré le 12 octobre 2026 sur la ligne de développement consacrée au parcours
d'ouverture du compte bancaire. La règle du point 3.2 et son motif sont documentés au sein même du
logiciel, à chacun des endroits où elle s'applique, afin qu'une évolution ultérieure ne rétablisse
pas la consultation des comptes qui encaissent sans que la question ouverte au point 6 ait été
tranchée.

L'état de l'environnement de recette et de production décrit au point 5 est issu de la lecture des
fichiers de configuration au 12 octobre 2026.

---

*Document établi le 12 octobre 2026, complété le 2 novembre 2026 (restriction aux associations rattachées à Mollie).*
