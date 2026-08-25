# Contrôle des registres publics d'associations

**Fiche de contrôle — dispositif de lutte contre le blanchiment de capitaux et le financement du terrorisme (LCB-FT)**

| | |
|---|---|
| **Entité** | CommonLink |
| **Nature du document** | Fiche descriptive d'un contrôle mis en place, destinée à la commission juridique |
| **Responsabilité concernée** | Entrée en relation d'affaires et vérification de l'identité du client *(responsabilité n° 3 du dispositif interne, épique E3)* |
| **Référence de suivi interne** | Tâche Asana n° `1216210976716067` — « Intégrer l'API Entreprise (RNA + SIRENE, DJEPVA) » |
| **Lien de suivi** | https://app.asana.com/1/1213718564226627/project/1213723193546726/task/1216210976716067 |
| **Priorité assignée** | P1 |
| **Date d'établissement** | 7 août 2026 — état décrit arrêté au 13 août 2026 |
| **État** | Réalisé et vérifié par contrôles automatisés. Non encore déployé — la mise en production de la plateforme est conditionnée à l'achèvement de l'ensemble du dispositif. |
| **Rédacteur** | Équipe technique CommonLink |

---

## 1. Avertissement sur la portée de ce document

Ce document décrit **un seul contrôle**, portant sur un seul point du traitement d'un dossier
d'association par la plateforme.

Le dispositif LCB-FT de CommonLink est organisé en six responsabilités, dont celle-ci ne constitue
qu'un élément. Les autres sont à des stades d'avancement différents — certaines partiellement
réalisées, d'autres planifiées et non encore engagées ; leur état fait l'objet d'un suivi distinct
et est rappelé au point 7.

Aucune conclusion sur la conformité globale du dispositif ne peut être tirée de la présente fiche.

## 2. Le constat qui motive ce contrôle

La plateforme disposait d'un mécanisme de vérification de l'existence juridique des associations
auprès des registres publics. Ce mécanisme interrogeait quatre sources — le registre national des
entreprises, le fichier Sirene de l'INSEE, le Journal officiel des associations et le bulletin des
annonces civiles et commerciales — et conservait le résultat de chaque consultation de façon
inaltérable.

Ce mécanisme présentait **trois lacunes** au regard des obligations LCB-FT.

D'une part, aucune source ne fournissait les **représentants légaux** de l'association. Or, sans
liste de représentants, il est impossible de procéder au contrôle des mesures de gel des avoirs,
qui doit porter sur les personnes physiques agissant au nom d'une entité — et pas seulement sur
l'entité elle-même. La collecte de ces informations est un prérequis au filtrage.

D'autre part, le **répertoire national des associations (RNA)**, géré par la Direction de la
jeunesse, de l'éducation populaire et de la vie associative (DJEPVA), n'était pas exploité alors
qu'il constitue la source de référence officielle pour l'existence et le statut des associations
déclarées en France.

Enfin, trois des quatre consultations étaient **conditionnées à la présence d'un numéro SIREN**
dans le dossier. Une association déclarée en préfecture reçoit un numéro RNA ; le numéro SIREN,
lui, n'est attribué que si l'association en fait la demande — typiquement lorsqu'elle emploie du
personnel, perçoit des subventions publiques ou exerce une activité assujettie. **Une association
régulièrement déclarée peut donc n'avoir aucun numéro SIREN.** Un tel dossier ressortait du
contrôle sans statut d'activité, sans catégorie juridique, sans représentants et sans vérification
des procédures collectives — et l'opérateur ne pouvait distinguer une association réellement
introuvable d'une association simplement dépourvue de SIREN.

## 3. Pourquoi ces lacunes existaient

Le mécanisme de vérification avait été conçu en prenant appui sur les sources disponibles à travers
des API ouvertes interrogeables sans démarche administrative préalable. Les données du répertoire
national des associations n'avaient pas été intégrées à la liste de ces sources.

La question des représentants légaux n'avait pas été traitée pour des raisons distinctes : les
sources déjà interrogées retournent cette information, mais elle n'était pas extraite ni conservée
— le mécanisme se limitait à répondre à la question « cette association existe-t-elle ? » sans
collecter les données qui permettraient, à l'étape suivante, de contrôler les personnes qui la
représentent.

La dépendance au numéro SIREN, enfin, tenait à l'ordre dans lequel les sources avaient été
intégrées : le fichier Sirene et le bulletin des annonces civiles et commerciales s'interrogent par
ce numéro, et le registre national des entreprises avait été raccordé sur le même identifiant alors
qu'il accepte également le numéro RNA.

Aucun incident n'est survenu du fait de ces lacunes. La plateforme n'encaisse aucun don réel à ce
jour.

## 4. Le contrôle mis en place

### 4.1 Les données collectées

**Représentants légaux.** À chaque consultation des registres, la liste des représentants légaux
retournée par les sources est extraite et conservée dans la ligne de contrôle, sous la forme de
noms. Cette information est relisible par l'opérateur qui examine le dossier. Elle constitue une
partie du périmètre des personnes soumises au filtrage des mesures de gel des avoirs, traité par
la fiche *E4 — Contrôle de gel des avoirs à l'entrée en relation d'affaires*.

**Statut d'activité au RNA.** Le résultat conserve si l'association est déclarée active au
répertoire national des associations. Cette donnée est obtenue par l'intermédiaire du registre
national des entreprises, qui agrège les données du RNA et les expose par une API ouverte — le
répertoire national des associations ne proposant pas d'interface publique interrogeable sans
démarche administrative préalable.

**Catégorie juridique INSEE.** La catégorie juridique attribuée par l'INSEE est captée et
conservée. Elle fonde le **verdict de périmètre**, décrit par la fiche *E3 — Contrôle automatique
du périmètre d'intervention de la plateforme*, qui fait autorité sur la liste des formes juridiques
acceptées et sur les effets de ce verdict.

**Numéro SIREN résolu.** Le numéro SIREN retenu dans une ligne de contrôle peut provenir des
registres et non de la déclaration de l'association. Il est conservé comme les autres éléments, de
façon inaltérable, avec l'identité de l'opérateur ayant déclenché la consultation. **Le dossier de
l'association n'est pas modifié** : le numéro résolu n'est pas reporté sur les données déclaratives.

Le principe des sources existantes est conservé : chaque appel est isolé, son échec n'interrompt pas
le contrôle et produit un avertissement tracé dans le résultat.

### 4.2 L'identification ne dépend pas d'un numéro SIREN

**Le numéro SIREN n'est un préalable à aucune consultation.** La première consultation interroge le
registre national des entreprises par le numéro RNA lorsque le SIREN n'est pas connu.
L'enregistrement retourné porte le numéro SIREN de l'association, qui alimente à son tour les
consultations qui en dépendent — fichier Sirene de l'INSEE et bulletin des annonces civiles et
commerciales. Une association identifiée par son seul RNA bénéficie ainsi de la totalité des
consultations.

**Un enregistrement n'est retenu que sur correspondance exacte du numéro RNA.** L'interface du
registre des entreprises est un moteur de recherche : interrogé par un numéro RNA, il peut retourner
plusieurs entités et ne garantit pas que l'entité recherchée figure en tête. Le contrôle parcourt
l'ensemble des réponses et n'exploite que celle dont le numéro RNA correspond exactement à celui du
dossier. En l'absence de correspondance, aucune donnée n'est retenue.

**L'absence du registre des entreprises n'est pas une inexistence.** Ce registre ne recense que les
entités titulaires d'un SIREN. Une association qui n'y figure pas n'en est pas moins régulièrement
déclarée. Le résultat porte « indéterminé » et non « inexistante » dans cette situation.

**Le statut d'activité peut être établi par le Journal officiel des associations.** La consultation
du Journal officiel s'effectue sur le numéro RNA — celui du dossier, ou à défaut celui que
l'enregistrement du registre des entreprises fait apparaître. Une association identifiée par son
seul numéro SIREN dont l'enregistrement ne porte aucun numéro RNA échappe donc à cette consultation :
ni publication, ni avis de dissolution ne sont recherchés pour elle, et le dossier n'en porte pas
mention comme d'une absence de résultat mais comme d'une consultation non effectuée. Pour une
association absente du registre des entreprises, le Journal officiel est la seule source
disponible : une publication la concernant, sans avis de dissolution, établit qu'elle est toujours
en activité. Deux limites sont posées délibérément — l'absence de publication n'est jamais
interprétée comme une inactivité (le statut reste indéterminé), et si la consultation du registre
des entreprises a échoué, le statut reste indéterminé plutôt que d'être établi par une source de
second rang : une panne de la source principale doit rester visible dans le dossier.

**Les avis de dissolution sont recherchés du plus récent au plus ancien.** La consultation du
Journal officiel retourne une tranche d'avis ; demandée sans ordre, elle pourrait laisser échapper
un avis de dissolution pour une association ayant fait l'objet de nombreuses publications. L'ordre
décroissant est celui dans lequel un avis de dissolution — dernier événement de la vie d'une
association — se trouve nécessairement.

### 4.2 bis Complément du 21 août 2026 — l'entrée par un numéro SIREN auto-déclaré

Le point 4.2 décrit le cas d'une association identifiée par son seul numéro RNA. Depuis le
18 août 2026, le cas symétrique existe : une association qui détient un numéro SIREN mais dont le
numéro RNA n'est pas connu peut créer son compte en saisissant elle-même son numéro SIREN, sans
passer par la recherche assistée dans les registres. Trois conséquences sont portées à la
connaissance de la commission.

**Le numéro saisi est un numéro déclaré, non un numéro vérifié à la saisie.** La création du compte
ne consulte aucun registre : elle enregistre ce que l'association déclare. La vérification reste
entièrement portée par la consultation des registres décrite dans la présente fiche, déclenchée
ultérieurement par le curateur lors de l'instruction du dossier. Ce séquencement est inchangé et
c'est le verrou de vérification avant ouverture de collecte qui garantit qu'aucune collecte ne
s'ouvre sur un dossier non instruit.

**Un même numéro SIREN ne peut pas être enregistré deux fois.** La création du compte est refusée
lorsque le numéro déclaré est déjà porté par une autre association, les deux emplacements où ce
numéro peut être stocké étant examinés. Cette unicité est assurée par le logiciel et **non par une
contrainte de la base de données** : les colonnes concernées n'en portent pas. Un enregistrement
effectué par un autre chemin que la création de compte — reprise de données, intervention directe en
base — n'est donc pas couvert. Le contrôle est par ailleurs délibérément limité au numéro SIREN : la
voie d'entrée par numéro RNA conserve son comportement antérieur et n'oppose aucun refus en cas de
doublon.

**Point ouvert — la garantie de correspondance exacte n'est pas symétrique.** Le point 4.2 énonce
qu'un enregistrement du registre national des entreprises n'est retenu que si son numéro RNA
correspond exactement à celui du dossier. Cette vérification n'a pas d'équivalent sur la voie du
numéro SIREN : l'interrogation étant faite par un moteur de recherche en texte intégral, c'est le
premier enregistrement retourné qui est retenu, sans qu'il soit vérifié que le numéro SIREN qu'il
porte est bien celui du dossier. Ce comportement était sans portée pratique lorsque les numéros
SIREN présents en base provenaient de reprises de données ; il en acquiert une depuis que le numéro
est saisi par l'association elle-même. Les conséquences seraient l'attribution au dossier de la
catégorie juridique, du statut et des dirigeants d'une autre entité — c'est-à-dire, en aval, un
verdict de périmètre et un périmètre de criblage portant sur la mauvaise personne morale. Aucune
correspondance erronée n'a été constatée ; le point est signalé comme un écart de conception à
corriger, non comme un incident.

### 4.3 Deux caractéristiques à souligner devant la commission

**Le contrôle est informatif, à une exception près.** Ni le résultat des consultations, ni la
présence ou l'absence de représentants, ni le statut RNA ne déclenchent d'approbation ou de rejet
automatique du dossier. L'ensemble des informations produites est mis à la disposition de
l'opérateur chargé de l'examen, qui seul peut prendre une décision. **L'exception est le verdict de
périmètre** : établi à partir de la catégorie juridique captée ici, il est bloquant — une entité
établie hors du périmètre de la loi de 1901 ne peut être approuvée. Ce verdict s'applique à tout
dossier dont la catégorie juridique est connue, y compris ceux identifiés par leur seul numéro RNA.
Ses effets sont décrits par la fiche de périmètre.

**Le contrôle est déclenché exclusivement à la main d'un opérateur.** Aucun événement dans le cycle
de vie d'un dossier — dépôt, modification, première connexion — ne déclenche automatiquement une
consultation des registres. L'interrogation est une action explicite, tracée avec l'identité de
l'opérateur qui l'a initiée. Cette contrainte a été arrêtée délibérément (décision interne D9) pour
éviter que des interrogations non maîtrisées ne produisent des données incomplètes ou contradictoires
dans le dossier.

### 4.4 Lisibilité de l'écran d'examen

Une association sans SIREN est nécessairement absente du registre national des entreprises, qui ne
recense que les entités titulaires d'un tel numéro. Cette absence et une panne de la source sont
deux situations distinctes, présentées comme telles à l'opérateur : confondues sous une même mention
« vérification indisponible », elles conduiraient l'opérateur à conclure à tort qu'un contrôle n'a
pas pu être effectué. La catégorie juridique ayant fondé le verdict de périmètre — ainsi que la
liste des formes acceptées — est affichée à l'opérateur.

## 5. Les éléments de preuve

Les scénarios suivants sont couverts par des contrôles automatisés, réexécutés à chaque modification
du logiciel.

**Côté serveur**

| Scénario vérifié | Résultat attendu |
|---|---|
| Les représentants retournés par le registre des entreprises sont extraits lors d'une consultation | Les noms des dirigeants sont présents dans le résultat |
| Les représentants sont relisibles depuis la ligne de contrôle conservée | La liste est relisible sans nouvelle interrogation des registres |
| La source du statut RNA répond | Le statut d'activité au RNA est conservé dans la ligne de contrôle |
| La source du statut RNA est indisponible | Un avertissement est tracé, le statut reste indéterminé, le contrôle aboutit malgré tout |
| Une seconde consultation est déclenchée | Deux enregistrements distincts sont créés, le précédent n'est pas modifié (caractère immuable) |
| Association identifiée par son seul RNA, présente dans les registres | Le registre est interrogé par le RNA, le SIREN est résolu, les consultations INSEE et annonces civiles s'exécutent, statut d'activité et représentants sont conservés |
| Le registre retourne une entité dont le RNA ne correspond pas | L'enregistrement est écarté, aucune donnée n'est retenue, aucun SIREN n'est adopté |
| Association sans SIREN absente du registre des entreprises | L'existence reste indéterminée, et non « inexistante » |
| Association absente du registre, publication au Journal officiel sans dissolution | Le statut d'activité est établi comme actif |
| Avis de dissolution publié au Journal officiel | Le statut d'activité est établi comme inactif |
| Aucune publication au Journal officiel pour ce RNA | Le statut d'activité reste indéterminé |
| Consultation du Journal officiel | Les avis sont demandés du plus récent au plus ancien, l'ordre de tri est effectivement transmis à la source |

**Côté écran d'examen**

| Scénario vérifié | Résultat attendu |
|---|---|
| Association absente du registre des entreprises, aucune source en échec | L'écran indique « non répertoriée » et non « vérification indisponible » |
| Consultations INSEE et annonces civiles non effectuées faute de SIREN | Le motif affiché est l'absence de numéro SIREN, non une panne |
| Consultation du registre des entreprises réellement en échec | L'écran indique « vérification indisponible » et le motif de panne |
| Catégorie juridique connue et libellée | Le code et son libellé sont affichés à l'opérateur |
| Catégorie juridique connue mais sans libellé référencé | Le code brut est affiché, sans libellé erroné |
| Catégorie juridique non récupérée | L'absence est indiquée explicitement |

Le contrôle vérifiant que le statut d'activité reste indéterminé lorsque la source principale est
indisponible couvre également le cas d'un repli sur le Journal officiel.

L'ensemble des contrôles automatisés de la plateforme a été exécuté, sans régression sur les
fonctionnements existants.

## 6. Ce que ce contrôle ne couvre pas

Cette précision est apportée pour éviter toute lecture extensive de la présente fiche.

Ce contrôle collecte les représentants légaux, vérifie l'existence de l'association et capte sa
catégorie juridique. **Il ne constitue pas un contrôle des mesures de gel des avoirs.** En
particulier, il ne couvre pas :

- le **filtrage des représentants collectés contre le registre des mesures de gel** — la collecte
  décrite ici en est le prérequis ; le filtrage lui-même relève de la fiche *E4 — Contrôle de gel
  des avoirs à l'entrée en relation d'affaires*, **livrée** ;
- l'**identification complète des bénéficiaires effectifs** au sens de la réglementation, qui
  dépasse la seule liste des représentants déclarés — *traitée par la fiche E3 — Identification des
  bénéficiaires effectifs d'une association, livrée* ;
- les **effets du verdict de périmètre** et la liste des formes juridiques acceptées, qui relèvent
  de la fiche *E3 — Contrôle automatique du périmètre d'intervention de la plateforme*, **livrée** ;
- la **vérification que l'enregistrement retenu est bien celui du dossier lorsque l'entrée se fait
  par un numéro SIREN** — la correspondance exacte n'est contrôlée que sur la voie du numéro RNA
  *(voir le point 4.2 bis)* ;
- la **vérification du numéro déclaré au moment de la création du compte** — elle n'a pas lieu ; la
  vérification est portée par la consultation décrite ici, déclenchée ultérieurement par le curateur ;
- l'**unicité du numéro SIREN au niveau de la base de données** — elle est assurée par le logiciel
  sur la seule voie de création de compte, sans contrainte de schéma *(voir le point 4.2 bis)* ;
- la **reconsultation périodique des registres** pour les associations déjà en relation d'affaires
  — la consultation reste déclenchée à la main d'un opérateur (décision interne D9) et **aucune
  reconsultation programmée n'est en place** ; un changement de dirigeant, une dissolution ou une
  radiation survenus après l'entrée en relation ne sont donc pas détectés d'eux-mêmes ;
- la **surveillance des opérations atypiques** et la déclaration de soupçon ;
- la **conservation** des pièces justificatives et le **rapport annuel** d'activité.

La qualification de la forme juridique par le verdict de périmètre ne dit rien de l'**adéquation de
l'objet** de l'association à la plateforme, qui reste entièrement du ressort de l'examen manuel par
l'opérateur.

**Suivi des travaux restants.** Les éléments ci-dessus qui restent à réaliser sont suivis dans le
référentiel interne de gestion de projet aux références suivantes.

| Travail restant | Lien de suivi |
|---|---|
| Surveillance des opérations atypiques et déclaration de soupçon *(épique E5)* | [1216210853624511](https://app.asana.com/1/1213718564226627/project/1213723193546726/task/1216210853624511) · [1216210853624512](https://app.asana.com/1/1213718564226627/project/1213723193546726/task/1216210853624512) · [1216210853624513](https://app.asana.com/1/1213718564226627/project/1213723193546726/task/1216210853624513) · [1216210853624514](https://app.asana.com/1/1213718564226627/project/1213723193546726/task/1216210853624514) |
| Conservation des pièces justificatives et rapport annuel *(épique E6)* | [1216210853624518](https://app.asana.com/1/1213718564226627/project/1213723193546726/task/1216210853624518) · [1216210853624517](https://app.asana.com/1/1213718564226627/project/1213723193546726/task/1216210853624517) · [1216210853624520](https://app.asana.com/1/1213718564226627/project/1213723193546726/task/1216210853624520) |
| Correspondance exacte du numéro SIREN sur l'enregistrement retenu, par symétrie avec la voie du numéro RNA | *Aucune tâche de suivi identifiée dans le référentiel de projet* |
| Contrainte d'unicité du numéro SIREN au niveau du schéma de base de données | *Aucune tâche de suivi identifiée dans le référentiel de projet* |
| Reconsultation périodique programmée des registres publics | *Aucune tâche de suivi identifiée dans le référentiel de projet* |

## 7. Situation de ce contrôle dans le dispositif d'ensemble

| Responsabilité du dispositif | État au 13 août 2026 |
|---|---|
| 1 — Désignation des responsables et cloisonnement des accès | Partiellement réalisé — rôle et espace de travail du responsable de la conformité livrés ; désignation formelle et référentiel de déclaration TRACFIN, actes hors plateforme, en cours |
| 2 — Classification des risques et procédures écrites | Partiellement réalisé — infrastructure livrée ; document de classification à approuver par l'organe compétent |
| **3 — Entrée en relation et vérification de l'association** | **Plusieurs contrôles réalisés : verrou de vérification avant ouverture de collecte, présent contrôle des registres publics, contrôle de périmètre loi 1901, identification des bénéficiaires effectifs. La surveillance périodique et la révision des dossiers existants restent à compléter.** |
| 4 — Gel des avoirs | Substantiellement réalisé — registre, contrôles à l'entrée en relation et au don, journal immuable, alertes et traitement livrés ; re-filtrage périodique automatique, formalisation de la procédure de notification à la DG Trésor et purge à cinq ans restent à livrer |
| 5 — Surveillance des opérations atypiques et déclaration | Planifié |
| 6 — Conservation des documents et rapport annuel | Planifié |

## 8. Éléments de traçabilité

Le contrôle est enregistré dans l'historique des modifications du logiciel, sur la ligne de
développement dédiée au dispositif LCB-FT (`app-legal-lcb-ft`). Il comprend l'extension du service
de vérification des registres, les migrations de base de données correspondantes — **V54**
(représentants et statut d'activité au RNA) et **V55** (catégorie juridique INSEE) — et les
contrôles automatisés décrits au point 5. L'affranchissement du numéro SIREN comme préalable, le
repli sur le Journal officiel et l'ordre de recherche des avis sont intégralement applicatifs et
n'ont requis aucune migration.

Le caractère informatif du contrôle, l'exception que constitue le verdict de périmètre et l'absence
de déclenchement automatique sont documentés directement dans le code du service concerné, afin
qu'une évolution ultérieure ne puisse pas les remettre en cause par inadvertance.

---

*Document établi le 7 août 2026, état arrêté au 13 août 2026, complété le 21 août 2026 (point 4.2 bis — entrée par un numéro SIREN auto-déclaré). Une fiche de même nature est produite
pour chaque contrôle du dispositif LCB-FT au fur et à mesure de sa réalisation.*
