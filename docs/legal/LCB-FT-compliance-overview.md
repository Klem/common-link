# Synthèse fonctionnelle — Sprint 19 LCB-FT

**Document de synthèse à destination de la commission juridique**

| | |
|---|---|
| **Entité** | CommonLink |
| **Nature du document** | Synthèse fonctionnelle non technique — vue d'ensemble du Sprint 19 |
| **Périmètre** | Épiques LCB-FT E1 à E6 — état au 21 août 2026 |
| **Rédacteur** | Équipe technique CommonLink |
| **Date** | 11 août 2026 — mises à jour des 14, 17 et 21 août 2026 |
| **Suivi des livrables** | https://app.asana.com/1/1213718564226627/project/1213723193546726 *(liens par livrable en section 6)* |

---

## 1. Avertissement de portée

Ce document est une **vue de synthèse** du Sprint 19 du dispositif LCB-FT de CommonLink. Il ne se substitue pas aux seize fiches de contrôle détaillées produites individuellement pour chaque livrable (voir liste en section 6). Les fiches individuelles font autorité pour la description précise de chaque contrôle et de ses éléments de preuve.

Le dispositif LCB-FT de CommonLink est organisé en **six responsabilités** conformément aux obligations de transposition des 4e et 5e directives LCB-FT. Ce sprint a permis de progresser principalement sur les responsabilités 3 et 4. Les responsabilités 1, 2, 5 et 6 restent partiellement ou totalement à réaliser — elles sont identifiées comme telles dans chaque section.

**Aucune conclusion sur la conformité globale du dispositif ne peut être tirée de ce document.**

La plateforme n'est pas encore déployée en production. La grande majorité des contrôles décrits est vérifiée par des contrôles automatisés. L'ensemble ne sera opposable à des tiers qu'à compter de la mise en production.

**Portée exacte de la couverture des mécanismes de niveau base de données — précisée le 21 août 2026.** Les mises à jour antérieures indiquaient que ces mécanismes n'étaient validés qu'à la main, l'environnement de test reconstruisant le schéma sans exécuter les migrations. Ce n'est plus exact : depuis le 19 août 2026, un contrôle automatisé démarre l'application contre une base PostgreSQL vide, y exécute la totalité des migrations dans l'ordre, et n'aboutit que si le schéma produit correspond exactement au mapping des entités. Il en découle une distinction que la commission doit tenir :

- **est désormais établi** que les migrations concernées *s'appliquent* sans erreur sur une base vierge — révocation des droits de modification et déclencheur d'immuabilité du journal (V51), catégories de personnes physiques et extension des sujets journalisables (V63), table de preuve et extension des catégories d'alerte (V64) ;
- **n'est pas établi** qu'une tentative de modification ou de suppression du journal soit *effectivement refusée*. Les deux contrôles qui exécuteraient une telle tentative sont écrits mais désactivés. Appliquer un script et constater l'effet du garde-fou qu'il installe sont deux choses distinctes ; seule la première est couverte.

Deux réserves s'y ajoutent, et la première a une portée plus large que ce seul contrôle. Depuis le 19 août 2026, la réussite des tests conditionne le déploiement de la plateforme — ce qui n'était pas le cas auparavant. Mais cette porte s'exécute sur un environnement dépourvu de moteur de conteneurs et **écarte l'intégralité des contrôles qui en requièrent un**, c'est-à-dire tous ceux qui s'exécutent contre une base de données réelle. Le contrôle des migrations décrit ci-dessus en fait partie, mais il n'est pas seul : une part des contrôles présentés comme automatisés dans les fiches de ce dispositif n'est vérifiée que par la suite complète lancée localement, et non avant chaque déploiement. Les contrôles ne touchant pas la base de données sont, eux, exécutés par la porte de déploiement. Seconde réserve : le motif d'environnement invoqué pour désactiver les deux contrôles de refus — moteur de conteneurs injoignable — n'est plus exact, ce qui rend leur réactivation à reprendre ; son résultat n'est pas anticipé ici.

**Un rapport d'audit de sécurité distinct est désormais disponible.** Un audit de sécurité de la plateforme a été conduit le 20 août 2026 et consigné dans `docs/security/audit-2026-08-20.md`. Il ne relève pas du dispositif LCB-FT et n'est pas repris ici, à deux exceptions près, portées dans le présent document parce qu'elles touchent directement des affirmations du dispositif : la faille de cloisonnement du rôle de responsable de la conformité (section 3, épique E1) et l'état de la revue de sécurité attendue au titre de la responsabilité n° 6 (section 7.2).

---

## 2. Résumé fonctionnel du sprint

Le Sprint 19 a livré l'essentiel de l'infrastructure de lutte contre le blanchiment de capitaux et le financement du terrorisme (LCB-FT) pour la plateforme CommonLink. En termes fonctionnels, il a permis de :

1. **Créer un rôle de responsable de la conformité** séparé de tous les autres rôles, avec des accès strictement cloisonnés et non cumulables.
2. **Poser l'infrastructure de la classification des risques** : chaque association reçoit un niveau de risque (bas, standard, élevé) qui détermine l'intensité des vérifications requises. Le document de classification reste à valider par l'organe compétent.
3. **Vérifier l'identité et le périmètre des associations** avant toute ouverture de collecte : existence légale dans les registres publics, conformité avec la loi de 1901, identification des bénéficiaires effectifs.
4. **Bloquer automatiquement toute entrée en relation** avec une association, un représentant légal ou un bénéficiaire effectif faisant l'objet d'une mesure de gel des avoirs — sans possibilité de contournement par un opérateur.
5. **Bloquer automatiquement tout don** provenant d'un donateur faisant l'objet d'une telle mesure.
6. **Tracer de façon immuable** chaque contrôle de gel — correspondance, absence de correspondance ou impossibilité de contrôle — dans un journal protégé par une chaîne de hachage, et **conserver, séparément du journal, les éléments permettant de motiver une décision** sur une correspondance signalée : valeur effectivement comparée, entrée du registre rapprochée, programme de sanction, score et seuil appliqués.
7. **Tenir à jour automatiquement** le registre national des mesures de gel publié par la Direction générale du Trésor.
8. **Donner au responsable de la conformité un espace de travail dédié et complet** : traitement des alertes de correspondance (avec traçabilité obligatoire des informations transmises à la Direction générale du Trésor), présentation des éléments de décision — ce qui a été criblé face aux entrées du registre rapprochées — et des décisions déjà rendues sur le même sujet, tableau de bord donnant une vue d'ensemble des contrôles des registres pour toutes les associations, et accès au journal d'audit immuable. Cet espace est cloisonné par redirection automatique dans les deux sens.
9. **Rendre visible tout contrôle de gel qui n'a pas pu être effectué**, par une catégorie d'alerte distincte des correspondances signalées : un criblage empêché — registre non chargé, dirigeants absents du dossier, incident technique — n'apparaissait auparavant sur aucun écran.
10. **Fermer une voie par laquelle le cloisonnement de la fonction conformité pouvait être contourné** : jusqu'au 20 août 2026, une personne non authentifiée pouvait s'attribuer elle-même le rôle de responsable de la conformité à la création d'un compte, et accéder ainsi à l'espace de conformité. La faille est corrigée ; elle est exposée en section 3, épique E1.
11. **Ouvrir l'entrée en relation aux associations dont le numéro RNA n'est pas connu**, par la saisie de leur seul numéro SIREN — cas symétrique de celui déjà traité des associations dépourvues de numéro SIREN. Aucune vérification n'est déplacée : le numéro saisi est déclaré, et la vérification reste portée par la consultation des registres conduite par le curateur.

---

## 3. Par épique

### Épique E1 — Rôle et accès du responsable de la conformité

**Ce qui a été livré**

Un rôle de responsable de la conformité distinct de tous les autres rôles de la plateforme (opérateur, association, donateur). Ce rôle est non cumulable : un même compte ne peut pas cumuler cette fonction avec une autre. Le compte est provisionné séparément de l'onboarding habituel.

L'accès aux informations et aux écrans de conformité — liste des alertes, dossiers de correspondance, journal d'audit — est réservé exclusivement à ce rôle. Toute tentative d'accès par un autre profil est rejetée. La séparation est bidirectionnelle : le responsable de la conformité n'a pas accès aux écrans de modération des dossiers.

Au cours de ce sprint, les écrans de traitement des alertes de gel ont été construits (voir épique E4), puis complétés par un espace de travail complet : tableau de bord centralisé (résumé des alertes, contrôles des registres pour toutes les associations, journal d'audit récent), règles de routage étanches dans les deux sens, et contrôles automatisés couvrant les scénarios de routage et d'accès aux données de conformité. La restriction d'accès par rôle aux points d'entrée du serveur demeure une propriété de la configuration de sécurité de la plateforme : elle n'est pas couverte par un contrôle automatisé, l'infrastructure de test ne prenant pas en charge ce type de vérification. La désignation formelle de la personne physique responsable et son inscription dans le référentiel de déclaration TRACFIN sont des actes hors plateforme, en cours.

**Complément du 21 août 2026 — une voie de contournement, constatée et corrigée le 20 août 2026**

L'affirmation ci-dessus n'était pas vérifiée jusqu'au 20 août 2026, et la commission doit en être
informée. Un audit de sécurité a établi que le rôle demandé lors de la création d'un compte n'était
soumis à aucune restriction : les trois voies publiques d'inscription acceptaient indifféremment
n'importe quel rôle, y compris celui de responsable de la conformité. Une personne non authentifiée
disposant d'une adresse de courriel valide pouvait donc s'attribuer ce rôle et accéder en lecture à
l'espace de conformité — alertes de gel, identités des donateurs criblés, consultations de registres
et journal d'audit.

Le cloisonnement décrit plus haut n'était pas en défaut : les refus par rôle fonctionnaient. La
faille se situait en amont, dans l'attribution du rôle — la plateforme vérifiait qui portait le
rôle, non comment il avait été obtenu.

Trois éléments en bornent la portée et en décrivent la suite. **Aucune donnée réelle n'était
exposée** : la plateforme n'est pas déployée, aucun don n'a été encaissé, aucune alerte réelle n'a
été émise, et aucune exploitation n'a été constatée. **La faille est corrigée** : les rôles de
conformité et de modération sont désormais refusés sur les trois voies publiques, refus appliqué à
la fois à la réception du formulaire et dans le service d'authentification, et ces comptes ne
peuvent être créés que par la procédure d'amorçage réservée à l'exploitation. **Une limite
subsiste** : la plateforme ne sait pas révoquer un jeton d'accès individuel, de sorte que
l'exclusion immédiate d'un compte de conformité compromis passerait par une rotation du secret de
signature, invalidant toutes les sessions. Ce dernier point n'est pas corrigé.

Le constat, son chemin d'exploitation et l'état de sa remédiation figurent au rapport
`docs/security/audit-2026-08-20.md` (constat C1), qui fait autorité.

**Implications fonctionnelles**

Aucun opérateur de la plateforme ne peut accéder aux dossiers sensibles de conformité. La personne responsable de la conformité, à l'inverse, ne peut pas intervenir dans la modération des associations. Cette séparation traduit l'exigence légale d'isolation des fonctions (art. L. 561-32 CMF).

**Exigence couverte** : Responsabilité n° 1 — Désignation des responsables et cloisonnement des accès. *(Partiellement — point d'entrée technique livré ; désignation formelle et référentiel TRACFIN en cours.)*

---

### Épique E2 — Classification des risques et mesures de vigilance

**Ce qui a été livré**

Un champ de niveau de risque (bas, standard, élevé) attaché à chaque association et à chaque don. Un document interne de classification définissant, pour chaque niveau, les mesures de vigilance applicables, les documents requis et la fréquence des révisions — en référence aux articles L. 561-5 à L. 561-10-2 du code monétaire et financier. Ce document est versionné et toute modification laisse une trace datée.

**Implications fonctionnelles**

Toute modification de la politique de classification requiert une validation explicite et est enregistrée dans l'historique du logiciel. Le niveau de risque d'une association détermine la fréquence de révision de son dossier (3 ans pour le niveau bas, 2 ans pour le standard, 1 an pour le niveau élevé) et la liste des documents exigibles.

**Complément du 21 août 2026 — trois précisions sur l'état réel de cette responsabilité.**

*La retenue sur l'attribution d'un niveau n'est pas un verrou logiciel.* Les versions antérieures du présent document indiquaient qu'un niveau différent de « standard » ne pouvait être attribué qu'une fois le document de classification approuvé. Cette formulation décrit une règle d'exploitation et non un contrôle programmé. En réalité, **aucun écran ni aucun point d'entrée du serveur ne permet aujourd'hui d'attribuer un niveau de risque** : le champ est créé à la valeur « standard » et seul un accès direct à la base pourrait le modifier. La date d'adoption du document est lue au démarrage et inscrite au journal technique, mais elle **ne conditionne aucun traitement**. La retenue constatée tient donc à l'absence de la fonctionnalité, non à une garantie technique ; le verrou restera à écrire le jour où l'attribution sera développée.

*Une révision du document de classification a été rédigée, et n'est pas en vigueur.* Une version `2026-08-v2` a été rédigée le 18 août 2026. Aucun environnement ne la charge : le logiciel applique et estampille la version `2026-08-v1`, à laquelle se rapportent les fréquences de révision citées ci-dessus. La révision ramènerait le document au strict exigé par la réglementation — retrait des fréquences chiffrées de réexamen au profit de l'obligation de mise à jour continue (art. L.561-6 al. 2 CMF) et du réexamen sur événement, retrait de deux pièces non imposées, reformulation du bénéficiaire effectif sur l'art. R.561-3 CMF dans sa rédaction issue du décret n° 2024-720 du 5 juillet 2024, et affirmation du niveau standard comme niveau par défaut, le niveau faible devenant un allègement à justifier client par client. Le détail figure à la fiche *E2 — Correspondance entre le niveau de risque et les mesures de vigilance*, point 4.1.

*Une divergence sur l'adoption est signalée sans être tranchée.* La version en vigueur porte une date d'adoption renseignée — 7 août 2026 — la révision porte la mention « à valider par le conseil », et le présent document comme le suivi de projet décrivent l'adoption comme un prérequis non satisfait. Ces trois énoncés ne peuvent être simultanément exacts ; l'arbitrage figure en section 7.3.

**Exigence couverte** : Responsabilité n° 2 — Classification des risques et procédures de vigilance écrites. *(Partiellement — infrastructure technique livrée ; document de classification à approuver par le conseil avant attribution de niveaux différenciés.)*

---

### Épique E3 — Entrée en relation et vérification de l'association

**Ce qui a été livré**

Cinq contrôles couvrant le processus de vérification d'une association avant l'ouverture de la collecte :

1. **Vérification dans les registres publics** — consultation des registres officiels pour confirmer l'existence légale, identifier les représentants légaux et capter la catégorie juridique INSEE de l'association. Ces informations alimentent directement le contrôle de gel des avoirs (épique E4) et le contrôle de périmètre décrit au point suivant.

   L'identification ne dépend d'aucun numéro SIREN. Une association régulièrement déclarée peut n'en avoir aucun, celui-ci n'étant attribué que sur demande : la première consultation interroge donc le registre national des entreprises par le numéro RNA, et l'enregistrement retenu doit correspondre exactement à ce numéro. L'absence d'une association de ce registre n'est pas interprétée comme une inexistence — il ne recense que les titulaires d'un SIREN — mais comme un statut indéterminé ; le statut d'activité peut alors être établi par le Journal officiel des associations. Les avis du Journal officiel sont demandés du plus récent au plus ancien, ordre dans lequel un avis de dissolution se trouve nécessairement.

2. **Vérification du périmètre loi de 1901** — contrôle, à partir de la catégorie juridique INSEE, que l'association relève bien du régime de la loi de 1901. Le périmètre accepté couvre l'ensemble des formes déclarées de la famille INSEE `92` : association déclarée (`9220`), entreprises d'insertion par l'économique (`9221`), association intermédiaire (`9222`), groupement d'employeurs (`9223`), association reconnue d'utilité publique (`9230`) et association de droit local d'Alsace-Moselle (`9260`). En sont exclues l'association non déclarée (`9210`, dépourvue de personnalité morale) et la congrégation (`9240`, régime cultuel distinct), ainsi que toute catégorie extérieure à cette famille — fondations, partis politiques, syndicats, sociétés commerciales. Ce verdict qualifie la forme juridique et rien d'autre ; l'adéquation de l'objet de l'association relève de l'examen de l'opérateur. Le verrou s'applique également aux dossiers identifiés par leur seul numéro RNA, dont la catégorie juridique est accessible.

3. **Identification des bénéficiaires effectifs** — recueil et documentation des personnes physiques exerçant le contrôle effectif de l'association au sens de l'article R. 561-1 CMF. Ces personnes sont également soumises au filtrage de gel des avoirs.

4. **Non-délégation des diligences à un prestataire** — confirmation documentée que les vérifications LCB-FT de la plateforme sont menées indépendamment des vérifications réalisées par les prestataires de paiement (Mollie). Les données transmises par ces prestataires ne se substituent pas aux diligences propres de la plateforme.

5. **Verrou de vérification avant ouverture de collecte** — la publication d'une campagne est conditionnée à l'achèvement de la vérification KYB de l'association. Ce verrou est automatique et ne peut pas être contourné.

**Implications fonctionnelles**

Une association ne peut pas ouvrir sa collecte sans que son existence légale, son périmètre et ses bénéficiaires effectifs aient été vérifiés. La vérification est à l'initiative de l'opérateur (curateur) mais ne peut pas être contournée par l'association. Les données fournies par les prestataires externes ne dispensent pas la plateforme de ses propres obligations de diligences.

Deux verrous sont **bloquants** et non seulement informatifs : une entité établie hors du périmètre de la loi de 1901 ne peut pas être approuvée, et l'approbation exige qu'au moins un représentant légal ait été retenu au dossier. Le verrou de périmètre s'applique à tous les dossiers dont la catégorie juridique est connue, y compris ceux identifiés par leur seul numéro RNA.

En revanche, l'absence de bénéficiaire effectif **au sens strict** — hors fonctions dirigeantes — ne bloque pas à elle seule l'approbation : l'article R. 561-3 du code monétaire et financier assimilant tout dirigeant d'association à un bénéficiaire effectif, l'exigence d'un représentant légal satisfait l'obligation d'identifier une personne physique. Toutes les personnes physiques retenues, quelle que soit leur catégorie, sont filtrées contre le registre des mesures de gel.

**Complément du 21 août 2026 — l'entrée en relation par un numéro SIREN auto-déclaré**

Le point 1 ci-dessus décrit l'affranchissement du numéro SIREN comme préalable à l'identification.
Depuis le 18 août 2026, le cas symétrique est ouvert : une association qui détient un numéro SIREN
mais dont le numéro RNA n'est pas connu peut créer son compte en saisissant elle-même ce numéro.
Trois points sont portés à la connaissance de la commission.

**Aucune vérification n'est déplacée ni allégée.** La création du compte enregistre une déclaration ;
elle ne consulte aucun registre. La vérification reste entièrement portée par la consultation des
registres conduite par le curateur lors de l'instruction du dossier, et le verrou de vérification
avant ouverture de collecte garantit qu'aucune collecte ne s'ouvre sur un dossier non instruit.

**L'unicité du numéro SIREN est assurée par le logiciel, non par la base de données.** Un numéro déjà
enregistré est refusé à la création d'un compte, les deux emplacements où il peut être stocké étant
examinés. Aucune contrainte de schéma ne double ce contrôle : un enregistrement effectué par un
autre chemin n'est pas couvert. La voie d'entrée par numéro RNA conserve son comportement antérieur
et n'oppose aucun refus en cas de doublon.

**Deux écarts de conception sont signalés.** La garantie de correspondance exacte énoncée au point 1
— un enregistrement du registre des entreprises n'est retenu que si son numéro RNA correspond
exactement à celui du dossier — n'a pas d'équivalent sur la voie du numéro SIREN : le premier
enregistrement retourné par le moteur de recherche est retenu sans vérification de son numéro. Par
ailleurs, la consultation du Journal officiel s'effectuant sur le numéro RNA, une association
identifiée par son seul numéro SIREN dont l'enregistrement ne porte aucun numéro RNA échappe à cette
consultation : ni publication ni avis de dissolution ne sont recherchés pour elle. Ces deux écarts
n'ont produit aucune erreur constatée ; ils sont repris en section 7.2 comme développements
restants. Le premier importe parce qu'un enregistrement erroné entraînerait un verdict de périmètre
et un périmètre de criblage portant sur la mauvaise personne morale.

**Exigence couverte** : Responsabilité n° 3 — Entrée en relation et vérification de l'association. *(Partiellement — vérifications à l'entrée livrées ; surveillance périodique et révision des dossiers existants restent à compléter.)*

---

### Épique E4 — Gel des avoirs

**Ce qui a été livré**

Huit composants couvrant l'ensemble du processus de contrôle des avoirs gelés :

1. **Service de filtrage** — moteur de comparaison des noms contre le registre national des mesures de gel publié par la Direction générale du Trésor. Ce registre consolide les mesures françaises, européennes et onusiennes applicables en France.

2. **Journal immuable des contrôles** — chaque contrôle de gel (résultat positif, négatif ou impossible) est inscrit dans un journal protégé par une chaîne de hachage SHA-256. Aucune entrée ne peut être modifiée ou supprimée sans que l'altération soit détectable. Les noms des personnes filtrées ne figurent jamais dans les journaux applicatifs.

3. **Mise à jour automatique du registre** — le registre de gel est synchronisé automatiquement à intervalle configurable. En cas d'indisponibilité du registre officiel, le dernier registre connu est conservé et le service continue d'opérer (conception dite « fail-open » pour la synchronisation, distincte de la conception bloquante du filtrage lui-même). **Complément du 21 août 2026** : le déclencheur des exécutions périodiques était hébergé dans le composant de configuration de la chaîne de blocs, lequel est désactivé lorsque la plateforme fonctionne avec une chaîne simulée — configuration actuellement retenue en production par décision documentée. Hébergé là, il aurait emporté avec lui la mise à jour du registre, qui aurait cessé **silencieusement** : aucun échec n'aurait été enregistré, faute de tentative, et le registre se serait figé sans signal. Le déclencheur a été extrait dans un composant distinct, indépendant de l'état du composant blockchain, et les deux modifications ont été livrées ensemble — la situation décrite n'a existé dans aucune version déployée. Cette indépendance conditionne désormais la mise à jour du registre et est documentée comme telle dans le logiciel.

4. **Contrôle à l'entrée en relation** — lors de la validation du dossier d'une association par un opérateur, trois entités sont filtrées obligatoirement : l'association elle-même, ses représentants légaux, et chacun de ses bénéficiaires effectifs retenus. Une correspondance ou une impossibilité de contrôle bloque la validation sans possibilité de contournement. Les représentants légaux proviennent de deux sources cumulées — ceux identifiés dans les registres publics et ceux saisis au dossier par le curateur (composant 8) — et le contrôle n'est déclaré impossible sur ce périmètre que si les **deux** sources sont simultanément absentes.

5. **Contrôle à la création d'un don** — avant tout encaissement, le nom du donateur est filtré contre le registre. Une correspondance ou une impossibilité de contrôle bloque le paiement : aucune transaction Mollie n'est créée, aucun don n'est enregistré. **Complément du 18 août 2026** : la date de naissance, qui ne sert qu'à écarter les homonymes, est devenue **facultative** au formulaire de don et **n'est pas conservée** — la valeur saisie sert au filtrage puis est écartée, aucune ligne de don ne la porte, et le donateur en est informé sous le champ. La ville de naissance n'est plus demandée : aucun contrôle ne l'utilisait. Le contrôle lui-même est inchangé — il porte sur le nom — mais son résultat devient plus strict lorsque la date manque : faute de pouvoir comparer les années, **toutes** les correspondances de nom sont retenues et partent à l'examen humain. L'absence de cette donnée ne peut donc jamais rendre le dispositif plus permissif ; elle a pour contrepartie des refus opposés à de simples homonymes, et une charge de traitement accrue pour la fonction conformité.

6. **Traitement des alertes et information à la Direction générale du Trésor** — le responsable de la conformité dispose d'un espace dédié pour consulter les alertes générées par les contrôles de gel, les prendre en charge, et les clore avec une décision motivée. En cas de correspondance avérée, les informations de la notification à la Direction générale du Trésor (date, moyen, référence) doivent être renseignées avant la clôture. La notification elle-même est un acte humain hors système : la plateforme en trace la réalisation sans l'exécuter. **Complément du 17 août 2026** : une alerte de gel née d'un **don** est désormais poussée par courriel vers la boîte de la fonction conformité, et non plus seulement déposée dans l'espace de traitement. Le cycle de vie de l'alerte supposait un lecteur — hypothèse vraie à l'entrée en relation, où un opérateur instruit le dossier au moment du criblage, et fausse au don, où le donateur est refusé par un message neutre sans que personne ne soit devant un écran. Le courriel porte la référence de l'alerte, sa gravité et un lien de traitement ; il ne comporte **aucune donnée d'identité**, ni dans son corps ni dans son objet, la messagerie n'étant pas un canal à accès contrôlé. Le destinataire est une boîte de fonction et non une personne nommée, afin que l'alerte continue de parvenir à la fonction conformité indépendamment des changements d'effectif.

7. **Éléments de décision sur une alerte de correspondance** — la décision du responsable de la conformité n'était pas motivable : l'écran ne présentait que des données agrégées (« 3 correspondances, score maximal 0,93 »), sans indiquer quelle entrée du registre avait été rapprochée ni sur quelle valeur la comparaison avait porté. Chaque correspondance signalée produit désormais, dans une table de preuve **distincte du journal** — dont le jeu de champs reste inchangé —, une ligne par entrée rapprochée : partie contrôlée, valeur normalisée effectivement comparée, dénomination et numéro de l'entrée du registre, référence légale du programme de sanction, score, seuil, algorithme et version du registre consultée. Ces valeurs sont **figées au moment du criblage** et non lues à l'affichage, une entrée radiée du registre faisant sinon disparaître la preuve d'une décision passée. Pour le même motif, le re-criblage à la demande a été écarté comme source de preuve. Deux compléments : les criblages impossibles génèrent désormais une alerte de gravité **moyenne** — distincte de la correspondance signalée, qui est de gravité haute — et l'écran présente, à titre strictement informatif, les décisions déjà rendues sur le même sujet, sans qu'aucune alerte ne soit jamais clôturée ou masquée automatiquement.

8. **Saisie des représentants légaux comme bénéficiaires effectifs** — la liste des dirigeants publiée au Journal officiel des associations est vide pour la majorité des associations, notamment celles dépourvues de numéro RNA actif ; le contrôle de gel était alors déclaré impossible et l'approbation bloquée sans recours. Le dossier distingue désormais deux catégories de personnes physiques : les **représentants légaux** (président, trésorier, secrétaire, administrateurs) et les **bénéficiaires effectifs** stricts. Les premiers peuvent être saisis par le curateur d'après les pièces d'identification présentées par l'association, et cette saisie est obligatoire avant approbation. Les deux catégories sont soumises aux mêmes règles de chiffrement, d'audit et de conservation, et toutes deux sont filtrées contre le registre de gel. Cette assimilation des représentants légaux aux bénéficiaires effectifs s'appuie sur l'article R. 561-3 du code monétaire et financier, dans sa rédaction issue du décret n° 2024-720 du 5 juillet 2024. La saisie manuelle n'est pas une dispense de contrôle : elle en est la modalité, encadrée par un opérateur habilité, lorsque la source officielle est indisponible.

**Implications fonctionnelles**

- Aucune association dont un représentant ou un bénéficiaire effectif ferait l'objet d'une mesure de gel ne peut entrer en relation d'affaires avec CommonLink.
- Aucun don d'un donateur faisant l'objet d'une mesure de gel ne peut être encaissé.
- Toute correspondance déclenche une alerte visible uniquement du responsable de la conformité. Ni l'association ni le donateur ne sont informés de l'existence de cette alerte ni de sa teneur — cette confidentialité est une obligation légale (interdiction de divulgation, art. L. 561-19 CMF). L'acteur concerné reçoit uniquement un refus générique.
- Chaque résultat de filtrage est tracé de façon immuable, permettant à un auditeur de reconstituer l'intégralité des contrôles effectués et la version du registre consultée à chaque moment.
- Un contrôle de gel qui n'a pas pu être effectué est désormais visible du responsable de la conformité, et non plus seulement journalisé. La distinction de gravité est délibérée : un criblage empêché signifie que la plateforme **ignore** s'il existe une correspondance, là où une correspondance signalée signifie qu'elle en a détecté une.
- Une correspondance signalée sur un donateur est portée activement à la connaissance de la fonction conformité, et non plus seulement mise à sa disposition. Deux limites sont à retenir : l'envoi est fait **au mieux** — la plateforme enregistre qu'elle a remis le message au serveur de messagerie, non qu'il est parvenu ni qu'il a été lu, la trace opposable de l'ouverture de l'alerte demeurant l'entrée du journal d'audit — et **aucune relance n'existe** lorsqu'une alerte reste non traitée. Un courriel manqué ramène donc la détection à la consultation spontanée de l'espace de traitement. Les alertes de criblage impossible, en particulier, ne déclenchent aujourd'hui aucune notification, alors qu'un contrôle empêché n'est pas un contrôle favorable ; cet écart est repris en 7.2.
- La plateforme met des éléments à disposition ; elle ne qualifie aucune correspondance. Aucune décision n'est prise automatiquement sur la base du criblage — la qualification demeure un acte humain motivé d'une personne physique désignée (point mentionné au regard de l'article 22 du RGPD).

**Points ouverts portés à la connaissance de la commission juridique**

La fiche *E4 — Preuve de correspondance* soulève six points que les livrables du sprint ne tranchent pas : des alertes déjà émises sont dépourvues d'éléments de preuve et cette preuve ne peut pas être reconstituée ; la conservation de la référence des entrées du registre repose sur une interprétation à valider ; le seuil et l'algorithme de rapprochement appellent une décision juridique ; l'articulation entre le droit d'information des articles 13 et 14 du RGPD et les dérogations LCB-FT n'est pas tranchée ; la simple consultation d'un dossier d'alerte n'est pas tracée ; la purge à cinq ans n'est pas réalisée.

**Ces points sont exposés, avec ce qu'ils conditionnent, aux sections 7.2 et 7.3**, qui font autorité et ne sont pas reprises ici afin qu'une seule liste soit tenue à jour. Les quatre premiers appellent un arbitrage de la commission et figurent en 7.3 ; les deux derniers — traçabilité de la consultation et purge — sont des développements restants et figurent en 7.2.

**Exigence couverte** : Responsabilité n° 4 — Gel des avoirs. *(Substantiellement réalisé — re-filtrage périodique automatique des associations déjà en relation d'affaires, procédure interne de notification DG Trésor et purge à cinq ans restent à formaliser ou à réaliser.)*

---

### Épique E5 — Surveillance des opérations atypiques et déclaration de soupçon

**Ce qui a été livré** : Rien dans ce sprint.

**Implications fonctionnelles** : La plateforme ne dispose pas encore de mécanisme de détection des comportements atypiques (fractionnement de dons, montants inhabituels, récurrence suspecte) ni de procédure de déclaration de soupçon à TRACFIN.

**Exigence couverte** : Responsabilité n° 5 — Surveillance des opérations atypiques et déclaration de soupçon. *(Non réalisé — planifié.)*

---

### Épique E6 — Conservation des documents et rapport annuel

**Ce qui a été livré** : Rien dans ce sprint.

**Implications fonctionnelles** : La plateforme ne dispose pas encore de procédure formalisée pour la conservation des documents pendant leur durée réglementaire (cinq ans post-relation d'affaires) ni pour l'établissement du rapport annuel de conformité.

**Exigence couverte** : Responsabilité n° 6 — Conservation des documents et rapport annuel. *(Non réalisé — planifié.)*

---

## 4. Tableau de statut global du dispositif

| Responsabilité | État au 21 août 2026 |
|---|---|
| 1 — Désignation des responsables et cloisonnement des accès | Partiellement réalisé — rôle technique, espace de travail complet du responsable (tableau de bord, registres, journal d'audit) et règles d'isolation dans les deux sens livrés ; une voie de contournement de l'attribution du rôle a été constatée et corrigée le 20 août 2026 *(épique E1)* ; la révocation d'un jeton d'accès individuel reste impossible ; désignation formelle et référentiel TRACFIN en cours *(voir note)* |
| 2 — Classification des risques et procédures écrites | Partiellement réalisé — infrastructure livrée, version `2026-08-v1` en vigueur ; **aucun point d'entrée ne permet d'attribuer un niveau de risque** et aucun verrou ne subordonne une attribution à l'adoption du document ; révision `2026-08-v2` rédigée le 18 août 2026 et non mise en vigueur ; divergence sur la date d'adoption à arbitrer *(sections 7.3 et note ci-dessous)* |
| 3 — Entrée en relation et vérification de l'association | Partiellement réalisé — vérifications à l'entrée livrées, tant pour les associations dépourvues de numéro SIREN que, depuis le 18 août 2026, pour celles dont le numéro RNA n'est pas connu, avec un périmètre loi 1901 couvrant la famille INSEE `92` ; deux écarts de conception sur la voie d'entrée par numéro SIREN *(section 7.2)* ; surveillance périodique à compléter |
| 4 — Gel des avoirs | Partiellement réalisé — contrôles à l'adhésion et au don, journal d'audit, registre, alertes (correspondance et criblage impossible), traitement opérateur et éléments de décision livrés ; re-filtrage périodique automatique, formalisation de la procédure DG Trésor et purge à cinq ans à livrer. Une partie des alertes déjà émises reste dépourvue de preuve *(voir points ouverts, épique E4)* |
| 5 — Surveillance des opérations atypiques et déclaration | Non réalisé — planifié |
| 6 — Conservation des documents et rapport annuel | Non réalisé — planifié |

> **Note sur la responsabilité 6.** Cette responsabilité reste non engagée sur ses livrables propres — archivage immuable, purge à cinq ans, rapport annuel. L'un de ses travaux restants a toutefois changé d'état : la revue de sécurité du dispositif a été conduite le 20 août 2026 et consignée. Elle n'a pas conclu sans réserve ; ses conclusions et les risques qu'elle accepte explicitement sont repris en section 7.2.
>
> **Note sur les responsabilités 1 et 2.** Plusieurs fiches indiquent encore « Planifié » pour ces deux responsabilités : leur tableau de statut reflète l'état connu à la date de leur rédaction et n'a pas été reconsidéré depuis. Les fiches de référence de ces responsabilités — E1 (6 août) pour la responsabilité 1, E2 (7 août) pour la responsabilité 2 — décrivent respectivement le cloisonnement technique du rôle et l'infrastructure de classification des risques comme livrés. La présente synthèse retient l'état décrit dans la fiche de référence de chaque responsabilité.

---

## 5. Les contrôles mis en place — tableau de synthèse

Les tableaux suivants couvrent l'intégralité du parcours, des contrôles pré-existants à ceux ajoutés depuis. Trois familles de contrôles sont distinguées : ceux qui existaient avant le Sprint 19, ceux que ce sprint a ajoutés au titre du dispositif LCB-FT, et ceux ajoutés depuis sans relever de ce dispositif. Cette dernière famille est recensée parce qu'elle modifie le parcours de publication et de don qu'un lecteur des deux premiers tableaux supposerait inchangé — non parce qu'elle satisfait une obligation LCB-FT.

### 5.1 Contrôles pré-existants (antérieurs au Sprint 19)

| Contrôle | Déclencheur | Déclenchement | Effet sur le parcours | Déblocage |
|---|---|---|---|---|
| Confirmation d'adresse email | Tout nouvel utilisateur à l'inscription | Automatique | Bloquant — compte inutilisable sans confirmation | Lien envoyé par email |
| Modération du dossier d'identification | Opérateur (curateur) | Geste humain | Bloquant — aucune campagne possible sans dossier validé | Curateur (validation ou rejet motivé) |
| Objectif de campagne strictement positif | Association lors de la demande de publication | Automatique sur événement | Bloquant — publication refusée | Association (corriger l'objectif) |
| Vérification KYB avant publication | Association lors de la demande de publication | Automatique sur événement | Bloquant — publication refusée si statut KYB non vérifié | Curateur (compléter la procédure de vérification) |
| Compte Mollie connecté avant publication | Association lors de la demande de publication | Automatique sur événement | Bloquant — publication refusée sans compte de paiement connecté | Association (se connecter à Mollie) |
| KYC Mollie complète avant publication | Association lors de la demande de publication | Automatique sur événement | Bloquant — publication refusée si KYC Mollie incomplète | Association (finaliser l'onboarding Mollie) |

### 5.2 Contrôles ajoutés dans ce sprint (LCB-FT)

| Contrôle | Déclencheur | Déclenchement | Effet sur le parcours | Déblocage |
|---|---|---|---|---|
| Vérification dans les registres publics (E3) | Curateur lors de l'instruction du dossier | Geste humain | Non bloquant en lui-même — alimente le dossier ; requis pour que le filtrage gel soit complet à l'entrée en relation, et fonde le verdict de périmètre qui, lui, est bloquant | Curateur (relancer la consultation) |
| Vérification du périmètre loi de 1901 (E3) | Système, à partir de la catégorie juridique INSEE captée lors du scan des registres | Automatique sur événement | **Bloquant** — une entité établie hors du périmètre de la loi de 1901 ne peut pas être approuvée · un périmètre indéterminé n'est pas bloquant | Curateur (relancer la consultation des registres ; le verdict lui-même n'est pas contournable) |
| Identification des bénéficiaires effectifs (E3) | Curateur lors de l'instruction du dossier | Geste humain | Non bloquant en lui-même — l'absence de bénéficiaire effectif au sens strict ne bloque pas l'approbation ; chaque bénéficiaire retenu est en revanche filtré contre le registre de gel, et une correspondance bloque | Curateur (renseigner les bénéficiaires effectifs) |
| Saisie des représentants légaux (E4) | Curateur lors de l'instruction du dossier, d'après les pièces d'identification | Geste humain | **Bloquant** — l'approbation exige au moins un représentant légal retenu au dossier · évite le blocage sans recours des associations dont les dirigeants ne sont pas publiés au Journal officiel | Curateur (renseigner les représentants légaux) |
| Filtrage gel des avoirs — entrée en relation (E4) | Système, déclenché par la validation du dossier par le curateur | Automatique sur événement · sans possibilité de contournement opérateur | Bloquant — la validation est refusée en cas de correspondance ou d'impossibilité de contrôle · l'association ne reçoit qu'un refus générique | Responsable de la conformité (examen de l'alerte) |
| Mise à jour automatique du registre de gel (E4) | Planificateur interne (cron) | Automatique · périodique | Non bloquant — en cas d'indisponibilité, le dernier registre connu est conservé | Automatique à la prochaine exécution réussie |
| Filtrage gel des avoirs — création d'un don (E4) | Système, déclenché avant tout encaissement | Automatique sur événement · sans possibilité de contournement | Bloquant — aucun paiement initié en cas de correspondance ou d'impossibilité de contrôle · le donateur ne reçoit qu'un refus générique | Responsable de la conformité (examen de l'alerte) |
| Alerte de criblage impossible (E4) | Système, lorsqu'un contrôle de gel obligatoire n'a pas pu être effectué (registre non chargé, dirigeants absents du dossier, incident technique) | Automatique sur événement | Non bloquant en lui-même — le blocage est déjà produit par l'issue du criblage · gravité moyenne, distincte de la correspondance signalée | Responsable de la conformité (examen de l'alerte) |
| Traitement d'une alerte de gel (E4) | Responsable de la conformité | Geste humain | Non bloquant sur le parcours en cours — traitement postérieur à la détection · les éléments de preuve du rapprochement sont présentés à l'appui de la décision | Responsable de la conformité |
| Information à la Direction générale du Trésor (E4) | Responsable de la conformité, lors de la clôture d'une alerte avec décision « correspondance avérée » | Automatiquement exigé à la clôture | Bloquant sur la clôture de l'alerte — impossible de clore sans renseigner la date, le moyen et la référence de l'information | Responsable de la conformité (renseigner les champs obligatoires) |
| Notification de la fonction conformité sur alerte de gel au don (E4) | Système, à la création de l'alerte | Automatique sur événement · une seule fois par alerte ouverte | Non bloquant — le refus du don est déjà produit par le criblage · envoi au mieux, sans preuve de délivrance ni relance · limité aux alertes de gel portant sur un donateur | Sans objet — l'alerte reste accessible dans l'espace de traitement même si le courriel échoue |

### 5.3 Contrôles ajoutés depuis, hors dispositif LCB-FT

Ces contrôles ne satisfont aucune obligation LCB-FT et ne sont rattachés à aucune des six responsabilités. Ils sont recensés ici parce qu'ils modifient le parcours de publication d'une campagne et celui du don, qu'un lecteur des deux tableaux précédents supposerait inchangé.

| Contrôle | Déclencheur | Déclenchement | Effet sur le parcours | Déblocage |
|---|---|---|---|---|
| Budget prévisionnel équilibré *(bloquant depuis le 17 août 2026 ; auparavant recommandation)* | Association lors de la demande de publication | Automatique sur événement | Bloquant — publication refusée si les deux volets ne sont pas renseignés ou si leur écart atteint un euro | Association (équilibrer le budget) |
| Résultat attendu renseigné *(bloquant depuis le 17 août 2026 ; auparavant recommandation)* | Association lors de la demande de publication | Automatique sur événement | Bloquant — publication refusée en deçà de vingt caractères | Association (renseigner le résultat attendu) |
| Calendrier de collecte renseigné *(bloquant depuis le 26 août 2026 ; auparavant non vérifié côté serveur malgré un blocage déjà affiché côté interface)* | Association lors de la demande de publication | Automatique sur événement | Bloquant — publication refusée si la date de début ou de fin n'est pas renseignée | Association (renseigner les deux dates) |
| Plafond de collecte d'une campagne | Système, avant toute initiation de paiement | Automatique sur événement | Bloquant — le don est refusé lorsqu'il porterait la collecte au-delà de l'objectif majoré d'une marge paramétrable. Le plafond tient compte des montants réservés par les paiements encore ouverts, de sorte que deux paiements simultanés ne puissent pas dépasser ensemble le plafond | Sans objet — le donateur peut donner un montant compatible avec la capacité restante |
| Nombre de paiements simultanément ouverts sur une campagne | Système, avant toute initiation de paiement | Automatique sur événement | Bloquant au-delà du seuil configuré — empêche qu'un tiers non authentifié immobilise la capacité restante d'une campagne en ouvrant des paiements qu'il n'honore pas *(constat d'audit du 20 août 2026)* | Automatique à l'expiration des réservations |

Les trois premières conditions traduisent une exigence de sincérité de la sollicitation : un donateur est sollicité au regard d'un plan chiffré, d'un résultat annoncé et d'une période de collecte définie — trois des cinq éléments que la notice ACPR de collecte ouverte au public impose de porter à la connaissance du donateur (voir `notice-collecte-publique-mentions-obligatoires.md`, hors dispositif LCB-FT, pour le détail des cinq éléments et de leur affichage sur la page publique). Le budget et le résultat attendu étaient présentés à l'association comme des recommandations et sont devenus bloquants le 17 août 2026 ; le calendrier l'est devenu le 26 août 2026, jusqu'alors bloquant côté interface mais non vérifié côté serveur. Les trois conditions sont appliquées dans l'interface et, indépendamment, par le serveur, avec des règles identiques de part et d'autre. **Elles s'ajoutent au verrou de vérification du dossier d'identification et ne s'y substituent pas.**

Le plafond de collecte répond à une contrainte d'irréversibilité et non à une logique de vigilance : une fois un paiement encaissé, le reçu fiscal est numéroté, son empreinte inscrite sur la chaîne et le document transmis au donateur, de sorte qu'un remboursement n'est pas une opération ordinaire. Le refus intervient au seul moment où il est encore sans conséquence. **Ce contrôle ne constitue en rien un dispositif de détection d'opérations atypiques** — il ne mesure ni la récurrence, ni le fractionnement, ni la cohérence d'un versement ; la responsabilité n° 5 reste entière.

---

## 6. Fiches de contrôle détaillées

Ce document s'appuie sur les seize fiches individuelles suivantes, disponibles dans le répertoire `docs/legal/` :

| Épique | Fiche | Objet | Lien de suivi |
|---|---|---|---|
| E1 | E1-role-responsable-conformite.md | Cloisonnement du rôle de responsable de la conformité | [1216210976716059](https://app.asana.com/1/1213718564226627/project/1213723193546726/task/1216210976716059) |
| E1 | E1-referentiel-declarant-tracfin.md | Référentiel de déclaration TRACFIN | [1216210976716058](https://app.asana.com/1/1213718564226627/project/1213723193546726/task/1216210976716058) |
| E2 | E2-niveau-de-risque-donnees.md | Enregistrement du niveau de risque | [1216210976716065](https://app.asana.com/1/1213718564226627/project/1213723193546726/task/1216210976716065) |
| E2 | E2-correspondance-risque-vigilance.md | Mesures de vigilance différenciées par niveau de risque | [1216210976716064](https://app.asana.com/1/1213718564226627/project/1213723193546726/task/1216210976716064) |
| E3 | E3-controle-registres-publics.md | Consultation des registres officiels (RNA / Infogreffe) | [1216210976716067](https://app.asana.com/1/1213718564226627/project/1213723193546726/task/1216210976716067) |
| E3 | E3-controle-perimetre-loi-1901.md | Vérification du périmètre de la loi de 1901 | [1216210976716070](https://app.asana.com/1/1213718564226627/project/1213723193546726/task/1216210976716070) |
| E3 | E3-identification-beneficiaires-effectifs.md | Identification des bénéficiaires effectifs | [1216210976716068](https://app.asana.com/1/1213718564226627/project/1213723193546726/task/1216210976716068) |
| E3 | E3-non-delegation-diligences-prestataire.md | Non-délégation des diligences LCB-FT à un tiers | [1216210976716072](https://app.asana.com/1/1213718564226627/project/1213723193546726/task/1216210976716072) |
| E3 | E3-verrou-verification-avant-collecte.md | Verrou de vérification avant ouverture de collecte | [1216210976716069](https://app.asana.com/1/1213718564226627/project/1213723193546726/task/1216210976716069) |
| E4 | E4-service-controle-mesures-de-gel.md | Service de filtrage des avoirs gelés | [1216210853624504](https://app.asana.com/1/1213718564226627/project/1213723193546726/task/1216210853624504) |
| E4 | E4-journal-controles-de-gel.md | Journal immuable des contrôles (chaîne de hachage) | [1216210853624508](https://app.asana.com/1/1213718564226627/project/1213723193546726/task/1216210853624508) |
| E4 | E4-mise-a-jour-registre-de-gel.md | Mise à jour automatique du registre national | [1216210853624507](https://app.asana.com/1/1213718564226627/project/1213723193546726/task/1216210853624507) |
| E4 | E4-controle-gel-entree-en-relation.md | Filtrage à l'entrée en relation d'affaires, y compris la constitution du périmètre des personnes physiques filtrées | [1216210853624505](https://app.asana.com/1/1213718564226627/project/1213723193546726/task/1216210853624505) |
| E4 | E4-controle-gel-des-dons.md | Filtrage à la création d'un don | [1216210853624506](https://app.asana.com/1/1213718564226627/project/1213723193546726/task/1216210853624506) |
| E4 | E4-traitement-alerte-et-information-tresor.md | Traitement des alertes et notification DG Trésor | [1216210853624509](https://app.asana.com/1/1213718564226627/project/1213723193546726/task/1216210853624509) |
| E4 | E4-preuve-de-correspondance-gel.md | Éléments de décision du responsable de la conformité sur une alerte de correspondance | *(non renseigné)* |

Les liens de suivi renvoient au référentiel interne de gestion de projet (Asana), où chaque livrable est suivi individuellement. Ils sont reproduits depuis l'en-tête des fiches. La fiche *Preuve de correspondance* fait exception : elle n'est rattachée à aucun élément de suivi, le référentiel de projet ne comportant aucune tâche correspondant à ce livrable, dont la réalisation procède d'un constat fait à la mise en service et non d'une tâche planifiée.

Chaque fiche porte en outre, à la fin de sa section « ce que ce contrôle ne couvre pas », un tableau **« Suivi des travaux restants »** rattachant chacun de ses points ouverts à la tâche correspondante du référentiel de projet, ou signalant explicitement l'absence d'une telle tâche. La section 7 ci-après en donne la vue consolidée.

**Une fiche du répertoire ne relève pas du dispositif LCB-FT.** Le répertoire `docs/legal/` comporte en outre une fiche consacrée à la rédaction du reçu fiscal et à la mention du mode de versement (`E5-recu-fiscal-mentions.md`, 17 août 2026). Elle porte sur le reçu prévu aux articles 200, 238 bis et 978 du code général des impôts et ne se rattache à aucune des six responsabilités du dispositif interne. Elle n'entre donc pas dans le décompte des seize fiches ci-dessus et aucune conclusion sur le dispositif LCB-FT ne peut en être tirée. Son nom de fichier porte le préfixe `E5-`, qui désigne par ailleurs l'épique « Surveillance des opérations atypiques » : ce préfixe ne doit pas être lu comme un rattachement à cette épique.

**Une seconde fiche, hors dispositif LCB-FT, a été ajoutée le 26 août 2026 : `notice-collecte-publique-mentions-obligatoires.md`.** Elle porte sur les cinq éléments que la notice ACPR impose de décrire sur la page publique d'une campagne en collecte ouverte au public (objet, montant cible, calendrier, description chiffrée de l'utilisation des fonds, résultat attendu) — deux d'entre eux, le montant cible et le calendrier, n'atteignaient pas la page publique avant cette date. Comme pour la fiche précédente, aucune conclusion sur le dispositif LCB-FT ne peut en être tirée. Son nom de fichier ne porte volontairement aucun préfixe `E`, pour ne pas reproduire la confusion relevée ci-dessus sur `E5-recu-fiscal-mentions.md`.

La saisie des représentants légaux comme bénéficiaires effectifs faisait initialement l'objet d'une fiche séparée, rédigée dans un registre technique. Elle est intégrée à la fiche *Filtrage à l'entrée en relation d'affaires*, dont elle constitue le périmètre d'application : le contrôle de gel porte sur les personnes physiques identifiées au dossier, et les conditions de leur identification ne se lisent pas utilement séparément du contrôle qu'elles alimentent.

---

## 7. Ce qui reste à faire — vue consolidée et suivi

Cette section rassemble, par nature de travail, l'ensemble des points ouverts déclarés par les seize fiches, avec la référence de suivi correspondante. Elle ne crée aucune information nouvelle : elle consolide ce que chaque fiche énonce dans sa propre section « ce que ce contrôle ne couvre pas ».

Trois natures de travail sont distinguées, car elles n'appellent ni les mêmes acteurs ni les mêmes délais : les **prérequis non techniques**, à la charge de l'entité ; les **développements restants**, à la charge de l'équipe technique ; les **arbitrages** relevant de la commission juridique, qui ne sont pas des tâches et n'ont pas de référence de suivi.

### 7.1 Prérequis non techniques — bloquants pour certains développements

| Prérequis | Ce qu'il bloque | Lien de suivi |
|---|---|---|
| Désigner le responsable LCB-FT, le déclarant et le correspondant TRACFIN | Utilisation effective de l'espace de conformité ; obligation de notification à la DG Trésor | [1216210976716057](https://app.asana.com/1/1213718564226627/project/1213723193546726/task/1216210976716057) |
| Compte ERMES actif et numéro de télédéclarant | Toute déclaration de soupçon ; modèle de dossier de soupçon | [1216210976716058](https://app.asana.com/1/1213718564226627/project/1213723193546726/task/1216210976716058) |
| Classification des risques rédigée, datée et validée par l'organe compétent | Attribution d'un niveau de risque autre que « standard » ; niveau de diligence exigible sur les pièces d'identité | [1216210976716062](https://app.asana.com/1/1213718564226627/project/1213723193546726/task/1216210976716062) |
| Procédures LCB-FT écrites | Conduite à tenir en cas de correspondance ; procédure de notification DG Trésor ; back-office de revue | [1216210976716063](https://app.asana.com/1/1213718564226627/project/1213723193546726/task/1216210976716063) |
| Documentation de l'interdiction de divulgation et du devoir d'abstention | Modèle de dossier de soupçon | [1216210853624515](https://app.asana.com/1/1213718564226627/project/1213723193546726/task/1216210853624515) |
| Formation de l'équipe à la LCB-FT | Aucun développement — condition d'exploitation | [1216210976716055](https://app.asana.com/1/1213718564226627/project/1213723193546726/task/1216210976716055) |
| Seuils de fractionnement validés | Détection du fractionnement | *Aucune tâche de suivi identifiée dans le référentiel de projet* |
| Boîte de la fonction conformité effectivement surveillée, et son filtrage anti-indésirable configuré pour ne pas écarter les notifications de la plateforme | Efficacité réelle de la notification des alertes de gel au don — le mécanisme technique est livré, sa portée dépend entièrement de la surveillance de la boîte destinataire | *Aucune tâche de suivi identifiée dans le référentiel de projet* |
| Délai cible de traitement d'une alerte de gel | Relance et escalade sur alerte non traitée — rien ne peut être développé avant que le délai ne soit fixé | *Aucune tâche de suivi identifiée dans le référentiel de projet* |

**Un prérequis est devenu sans objet.** Le contrat de tierce introduction au titre de l'article R. 561-13 du code monétaire et financier n'est plus requis : la **décision interne D11 du 7 août 2026** a acté qu'aucune tierce introduction n'est pratiquée. La tâche correspondante — [1216210976716071](https://app.asana.com/1/1213718564226627/project/1213723193546726/task/1216210976716071) — est à **clore** et ne bloque plus rien. La fiche *E3 — Non-délégation des diligences au prestataire de paiement* corrobore cette décision pour le prestataire de paiement effectivement utilisé, en établissant qu'aucune information de vérification ne transite de ce prestataire vers CommonLink ; elle ne se prononce pas sur les autres canaux.

### 7.2 Développements restants par responsabilité

**Responsabilité n° 1 — Désignation et cloisonnement des accès**

| Travail restant | Lien de suivi |
|---|---|
| Cloisonnement des dossiers de soupçon par déclarant — un déclarant ne visualise que ses propres dossiers | [1216210976716060](https://app.asana.com/1/1213718564226627/project/1213723193546726/task/1216210976716060) |
| Interface de gestion des désignations de déclarants | *Aucune tâche de suivi identifiée dans le référentiel de projet* |
| Révocation d'un jeton d'accès individuel — l'exclusion immédiate d'un compte de conformité compromis suppose aujourd'hui une rotation du secret de signature, qui invalide toutes les sessions *(constat d'audit du 20 août 2026, non corrigé)* | *Aucune tâche de suivi identifiée dans le référentiel de projet* |

**Responsabilité n° 2 — Classification des risques**

L'infrastructure est livrée. Le seul travail restant est le prérequis non technique de la section 7.1. La collecte et la vérification automatiques des pièces exigées par niveau de risque ne sont rattachées à aucune tâche de suivi.

**Responsabilité n° 3 — Entrée en relation et vérification de l'association**

| Travail restant | Lien de suivi |
|---|---|
| Traçage des refus d'ouverture de collecte et métriques du rapport annuel | [1217152626184665](https://app.asana.com/1/1213718564226627/project/1213723193546726/task/1217152626184665) |
| Reconsultation périodique programmée des registres publics — détection d'un changement de dirigeant, d'une dissolution ou d'une radiation | *Aucune tâche de suivi identifiée dans le référentiel de projet* |
| Collecte d'une pièce d'identité propre à chaque bénéficiaire effectif | *Aucune tâche de suivi identifiée dans le référentiel de projet* |
| Vérification que l'enregistrement retenu au registre des entreprises est bien celui du dossier lorsque l'entrée se fait par un numéro SIREN — la correspondance exacte n'est contrôlée que sur la voie du numéro RNA | *Aucune tâche de suivi identifiée dans le référentiel de projet* |
| Contrainte d'unicité du numéro SIREN au niveau du schéma de base de données — l'unicité n'est aujourd'hui assurée que par le logiciel, sur la seule voie de création de compte | *Aucune tâche de suivi identifiée dans le référentiel de projet* |
| Statut d'activité des associations identifiées par leur seul numéro SIREN sans numéro RNA connu — la consultation du Journal officiel, qui s'effectue sur le numéro RNA, n'a pas lieu pour elles | *Aucune tâche de suivi identifiée dans le référentiel de projet* |

**Responsabilité n° 4 — Gel des avoirs**

| Travail restant | Lien de suivi |
|---|---|
| Procédure interne de notification à la DG Trésor | [1216210853624509](https://app.asana.com/1/1213718564226627/project/1213723193546726/task/1216210853624509) · [1216210976716063](https://app.asana.com/1/1213718564226627/project/1213723193546726/task/1216210976716063) |
| Réactivation des contrôles de refus de modification au niveau base de données et exécution de la vérification d'intégrité de la chaîne — *précisé le 21 août 2026* : l'application des migrations concernées est désormais couverte par un contrôle automatisé, l'effet du garde-fou d'immuabilité ne l'est pas *(section 1)*, et le motif d'environnement invoqué pour désactiver les deux contrôles n'est plus exact | [1216210853624519](https://app.asana.com/1/1213718564226627/project/1213723193546726/task/1216210853624519) |
| Re-criblage périodique automatique des associations et des dons déjà contrôlés | *Aucune tâche de suivi identifiée dans le référentiel de projet* |
| Branchement du mécanisme d'alerte opérationnelle sur échec de synchronisation du registre | *Aucune tâche de suivi identifiée dans le référentiel de projet* |
| Traçabilité de la simple consultation d'un dossier d'alerte — **toujours ouvert pour un dossier d'alerte**. Pour l'espace de travail « Associations » de la conformité (27 août 2026), l'**ouverture** d'un dossier association et l'**ouverture** du détail d'une campagne sont tracées (journal `ASSOCIATION_DOSSIER_CONSULTED` / `CAMPAIGN_DOSSIER_CONSULTED`), vérifié par contrôles automatisés, **non encore déployé**. Portée limitée à l'ouverture du dossier : la liste des campagnes d'une association, l'historique d'acceptation CGU/CGV d'une association, et l'historique d'acceptation CGU/CGV des donateurs d'une campagne — ces deux derniers exposent des noms et emails de signataires — se lisent chacun sans écriture de journal propre | *Aucune tâche de suivi identifiée dans le référentiel de projet* |
| Couverture automatisée des deux scénarios de filtrage déclarés par la fiche *Filtrage à l'entrée en relation* | *Aucune tâche de suivi identifiée dans le référentiel de projet* |
| Vérification d'identité du donateur (KYC donateur) | *Aucune tâche de suivi identifiée dans le référentiel de projet* |
| Délai maximal de traitement d'une alerte et relance sur alerte non traitée — **conditionné par un prérequis de procédure** (le délai cible n'est pas fixé) | *Aucune tâche de suivi identifiée dans le référentiel de projet* |
| Notification de la fonction conformité sur alerte de criblage impossible | *Aucune tâche de suivi identifiée dans le référentiel de projet* |

**Responsabilité n° 5 — Surveillance des opérations atypiques et déclaration** *(non engagée)*

| Travail restant | Lien de suivi |
|---|---|
| Détection du fractionnement — agrégation par donateur et par projet sur six mois glissants | [1216210853624511](https://app.asana.com/1/1213718564226627/project/1213723193546726/task/1216210853624511) |
| Moteur de règles d'atypie — montant, vélocité, géographie | [1216210853624512](https://app.asana.com/1/1213718564226627/project/1213723193546726/task/1216210853624512) |
| Back-office de revue des alertes — classer, examen renforcé, déclarer | [1216210853624513](https://app.asana.com/1/1213718564226627/project/1213723193546726/task/1216210853624513) |
| Modèle de dossier de soupçon pré-remplissant les champs ERMES | [1216210853624514](https://app.asana.com/1/1213718564226627/project/1213723193546726/task/1216210853624514) |

Le dépôt d'une déclaration dans ERMES restera **manuel** : TRACFIN ne publie aucune interface applicative. Aucune intégration n'est prévue ni ne sera construite.

**Responsabilité n° 6 — Conservation des documents et rapport annuel** *(non engagée)*

| Travail restant | Lien de suivi |
|---|---|
| Archivage immuable des pièces justificatives — versionnement sans réécriture et chiffrement | [1216210853624518](https://app.asana.com/1/1213718564226627/project/1213723193546726/task/1216210853624518) |
| Politique de rétention à cinq ans et purge par anonymisation — identité effacée, ligne comptable conservée | [1216210853624517](https://app.asana.com/1/1213718564226627/project/1213723193546726/task/1216210853624517) |
| Revue de sécurité du dispositif — **conduite le 20 août 2026 et consignée** (`docs/security/audit-2026-08-20.md`). Un constat critique et six constats majeurs ont été corrigés, dont la voie de contournement du cloisonnement de la fonction conformité *(épique E1)*. **Trois risques sont explicitement acceptés en l'état et non corrigés** : un défaut de cloisonnement entre associations sur les bénéficiaires de virement, la durée de validité des jetons d'accès jointe à un profil de base permissif, et la contradiction de configuration sur la simulation de la chaîne en production. Ces acceptations sont à confirmer ou à revoir avant la mise en production, la revue ayant été conduite hors production et sans données réelles | [1216210853624519](https://app.asana.com/1/1213718564226627/project/1213723193546726/task/1216210853624519) |
| Traçage des refus et instrumentation des métriques du rapport annuel *(sous-tâche du générateur)* | [1217152626184665](https://app.asana.com/1/1213718564226627/project/1213723193546726/task/1217152626184665) |
| Générateur du rapport annuel IFP — publication avant le 30 juin | [1216210853624520](https://app.asana.com/1/1213718564226627/project/1213723193546726/task/1216210853624520) |

L'ordre entre les deux premiers travaux n'est pas indifférent : les pièces sont aujourd'hui stockées en clair et un remplacement s'opère par suppression puis insertion. Purger un stockage encore modifiable n'apporterait aucune garantie ; l'archivage immuable doit précéder la purge.

### 7.3 Points appelant un arbitrage de la commission juridique

Ces points ne sont pas des tâches de développement et ne portent aucune référence de suivi. Ils conditionnent néanmoins des travaux listés ci-dessus.

| Point ouvert | Ce qu'il conditionne |
|---|---|
| **Clé d'agrégation du donateur** — les comptes invités étant provisionnés automatiquement par le formulaire de don, deux dons d'une même personne peuvent porter deux profils distincts. Sans clé stable, la détection de fractionnement est vide de sens. | Détection du fractionnement — question la plus urgente de la responsabilité n° 5 |
| **Sens de « projet reçu »** — s'agit-il d'une étape éditoriale, une équipe CommonLink acceptant ou refusant un projet soumis, ou du seul franchissement des conditions techniques d'ouverture ? | Périmètre du traçage des refus et des métriques du rapport annuel |
| **Conservation de la référence des entrées du registre de gel** — la lecture retenue par la fiche *Preuve de correspondance* distingue le sujet criblé, dont l'identité figure au dossier, de l'entrée du registre, publication officielle publique. Si la commission la rejette, la table de preuve doit être revue. | Table de preuve des correspondances |
| **Seuil et algorithme de rapprochement** — similarité de Jaro-Winkler au seuil de 0,85, méthode qui sur-pondère les préfixes communs. Abaisser le rappel d'un criblage de sanctions est une décision juridique. | Volume de faux positifs soumis au responsable de la conformité |
| **Articulation entre le droit d'information des articles 13 et 14 du RGPD et les dérogations LCB-FT** | Information des personnes concernées par un criblage |
| **Alertes dépourvues d'éléments de preuve** — la preuve ne peut pas être reconstituée ; ces alertes doivent être traitées sur la base du journal seul, et cette limite mentionnée dans la motivation. | Traitement des alertes déjà émises |
| **Périmètre du premier exercice reporté** — la production n'encaisse rien à ce jour ; l'exercice démarrerait à la mise en production et non au 1er janvier 2026, ce qui change le périmètre du rapport dû avant le 30 juin 2027. | Générateur du rapport annuel IFP |
| **Statut d'adoption du document de classification des risques** — la version en vigueur porte une date d'adoption renseignée au 7 août 2026, la révision rédigée le 18 août 2026 porte la mention « à valider par le conseil », et le présent document comme le suivi de projet décrivent l'adoption comme un prérequis non satisfait. Ces trois énoncés ne peuvent être simultanément exacts ; la commission doit qualifier ce qui s'est tenu le 7 août 2026. | Version du document à mettre en vigueur, date d'adoption à retenir, et levée ou maintien du prérequis bloquant de la section 7.1 |
| **Contenu de la révision `2026-08-v2`** — retrait des fréquences chiffrées de réexamen au profit de la seule obligation de mise à jour continue et de réexamen sur événement, retrait de deux pièces jugées non imposées, statut du niveau « faible » comme allègement à justifier client par client. Ces choix réduisent des exigences internes en s'appuyant sur une lecture des textes qu'il appartient à la commission de valider. | Mise en vigueur de la révision ; contenu des mesures de vigilance opposables |
| **Acceptation des trois risques de sécurité maintenus** — cloisonnement inter-associations sur les bénéficiaires de virement, durée de validité des jetons d'accès et profil de base permissif, simulation de la chaîne en production. Acceptés par décision technique hors production ; leur maintien à l'ouverture de l'encaissement relève d'une appréciation qui dépasse la seule technique. | Conditions de la mise en production |

### 7.4 Portes de mise en production

Deux travaux sont exigibles avant l'ouverture de l'encaissement, indépendamment de toute échéance calendaire : le **contrôle de gel au don** — livré — et le **traçage des refus** ([1217152626184665](https://app.asana.com/1/1213718564226627/project/1213723193546726/task/1217152626184665)), qui ne l'est pas. S'y ajoutent, comme conditions d'exploitation et non de développement, la désignation formelle des responsables et la régularisation de la procédure interne de notification à la DG Trésor.

**Deux portes s'y ajoutent au titre de la mise à jour du 21 août 2026.** La **réactivation des deux contrôles de refus de modification du journal** : l'immuabilité du journal des contrôles de gel est l'élément qui rend le dispositif opposable à un auditeur, et l'effet du garde-fou qui la produit n'est établi par aucun contrôle automatisé à ce jour (section 1). Et la **confirmation ou la revue des trois risques de sécurité acceptés en l'état** le 20 août 2026, cette acceptation ayant été prononcée sur un environnement sans données réelles (section 7.2, responsabilité n° 6).

---

*Document établi le 11 août 2026, mis à jour les 14, 17 et 21 août 2026 : état des livrables arrêté au 21 août 2026.*

*La mise à jour du 21 août porte sur les développements survenus depuis le 18 août 2026 et sur ce qu'ils modifient dans les affirmations du présent document. Six points en constituent la substance : la voie de contournement du cloisonnement de la fonction conformité, constatée et corrigée le 20 août 2026 (épique E1) ; l'état réel de la responsabilité n° 2 — absence de tout point d'entrée d'attribution d'un niveau de risque, révision du document de classification rédigée sans être mise en vigueur, divergence sur sa date d'adoption ; l'ouverture de l'entrée en relation aux associations dont le numéro RNA n'est pas connu, et les deux écarts de conception qu'elle rend significatifs (épique E3) ; l'indépendance désormais requise du déclencheur de mise à jour du registre de gel vis-à-vis du composant blockchain (épique E4) ; la portée exacte de la couverture automatisée des mécanismes de niveau base de données, qui établit l'application des migrations sans établir l'effet du garde-fou d'immuabilité ; et la tenue d'une revue de sécurité du dispositif, avec trois risques explicitement acceptés en l'état.*

*Ce document est une synthèse de travaux en cours. Il sera mis à jour à chaque sprint livrant des éléments du dispositif LCB-FT.*
