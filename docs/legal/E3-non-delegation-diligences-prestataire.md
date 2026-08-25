# Non-délégation des diligences au prestataire de paiement

**Fiche de contrôle — dispositif de lutte contre le blanchiment de capitaux et le financement du terrorisme (LCB-FT)**

| | |
|---|---|
| **Entité** | CommonLink |
| **Nature du document** | Fiche descriptive d'une analyse de conformité, destinée à la commission juridique |
| **Responsabilité concernée** | Entrée en relation d'affaires et vérification de l'identité du client *(responsabilité n° 3 du dispositif interne, épique E3)* |
| **Référence de suivi interne** | Tâche Asana n° `1216210976716072` — « Pre-remplir l'onboarding avec les donnees KYB Monerium » *(intitulé reproduit verbatim)*. C'est le flux de pré-remplissage examiné au point 3 qui a motivé l'analyse. L'intitulé nomme le prestataire antérieur ; la présente analyse porte sur le prestataire effectivement utilisé aujourd'hui, ce changement étant précisément ce qui a rendu l'examen nécessaire. |
| **Lien de suivi** | https://app.asana.com/1/1213718564226627/project/1213723193546726/task/1216210976716072 |
| **Priorité assignée** | P2 |
| **Date d'analyse** | 10 août 2026 |
| **État** | Analysé et vérifié par contrôles automatisés. Non encore déployé — la mise en production de la plateforme est conditionnée à l'achèvement de l'ensemble du dispositif. |
| **Rédacteur** | Équipe technique CommonLink |

---

## 1. Avertissement sur la portée de ce document

Ce document décrit **une analyse de conformité** portant sur un seul aspect du dispositif LCB-FT :
la relation entre les diligences conduites par CommonLink sur les associations et celles conduites
par le prestataire de paiement à l'égard de ses propres clients.

Le dispositif LCB-FT de CommonLink est organisé en six responsabilités, dont celle-ci ne constitue
qu'un élément. Les cinq autres — désignation des responsables, classification des risques,
gel des avoirs, surveillance des opérations atypiques et déclaration, conservation des documents
et rapport annuel — sont **planifiées et non encore réalisées** à la date de ce document. Leur
état d'avancement fait l'objet d'un suivi distinct.

Aucune conclusion sur la conformité globale du dispositif ne peut être tirée de la présente fiche.

## 2. Le risque constaté

La plateforme CommonLink recourt à un prestataire de paiement tiers pour permettre aux associations
d'encaisser des dons. Ce prestataire soumet lui-même les associations à une procédure de
vérification avant de les autoriser à recevoir des fonds.

Le risque examiné est le suivant : si CommonLink se fondait, pour ses propres diligences, sur les
résultats de la vérification conduite par le prestataire, elle recourrait à un **tiers introducteur**
au sens de l'article R.561-13 du code monétaire et financier. Ce recours est encadré : il requiert
un contrat formalisé, prévoit des conditions strictes d'éligibilité du tiers, et ne décharge pas
CommonLink de sa responsabilité propre en cas de défaillance.

La présente fiche établit que ce risque ne se réalise pas dans la configuration actuelle de la
plateforme, et qu'aucun contrat de tierce introduction n'est requis.

## 3. Pourquoi ce risque devait être examiné

Le canal de collecte des dons a changé de prestataire en cours de développement. La présente
analyse porte sur le prestataire **effectivement utilisé aujourd'hui**. La question de la tierce
introduction ne se posait pas dans les mêmes termes avec l'ancien prestataire et n'avait pas été
formellement instruite lors de ce changement.

Ce prestataire, comme tout établissement de paiement, conduit ses propres vérifications
d'identité sur les associations qu'il accepte d'héberger : examen de la forme juridique, contrôle
des représentants, évaluation du profil de risque de l'organisation. Ces vérifications sont menées
pour le compte et dans l'intérêt du prestataire, dans le cadre de ses propres obligations
réglementaires.

Par ailleurs, le processus d'intégration de l'association sur la plateforme CommonLink comporte
une étape de **pré-remplissage** du formulaire du prestataire : certaines informations déjà
détenues par CommonLink — forme juridique, numéro d'identification, coordonnées du représentant —
sont transmises au prestataire pour éviter à l'association de les ressaisir. Cette circulation de
données, **de CommonLink vers le prestataire**, a pu faire naître la question d'une dépendance
dans l'autre sens.

## 4. Le contrôle mis en place

L'analyse du fonctionnement de la plateforme établit deux points distincts, exposés ci-après.

### 4.1 Les deux procédures de vérification sont indépendantes et cumulatives

CommonLink conduit sa propre vérification du dossier de chaque association, distincte et
indépendante de celle du prestataire. Cette vérification, instruite par l'équipe de conformité de
CommonLink et alimentée par des consultations automatisées de registres publics, aboutit à une
décision de validation ou de rejet qui est propre à CommonLink.

L'ouverture d'une collecte de dons requiert, **cumulativement** :

- que le dossier de l'association soit **validé par CommonLink** ;
- que l'association ait **complété la procédure de vérification auprès du prestataire** de paiement.

La validation par le prestataire ne dispense jamais des diligences de CommonLink. À l'inverse, la
validation par CommonLink ne dispense jamais de la validation par le prestataire. Les deux
conditions sont exigées simultanément, et chacune est vérifiée indépendamment de l'autre à chaque
tentative d'ouverture de collecte.

Ce point est décisif : si la validation du prestataire suffisait, CommonLink déléguerait ses
diligences et recourrait à un tiers introducteur. Ce n'est pas le cas.

### 4.2 Aucune donnée d'identification n'est reprise depuis le prestataire vers CommonLink

Le seul flux de données d'identification entre CommonLink et le prestataire va **de CommonLink
vers le prestataire** : lors du premier rattachement, les données que l'association a déjà
fournies à CommonLink sont transmises pour pré-remplir le formulaire du prestataire.

Lors du retour du prestataire après sa propre procédure de vérification, le traitement effectué
par la plateforme n'écrit que des données techniques liées à la connexion elle-même : jetons
d'accès, identifiant de l'organisation chez le prestataire, indicateurs d'état de la connexion,
indicateurs de capacité d'encaissement, adresse du tableau de bord de l'association chez le
prestataire, date de dernière synchronisation.

Aucun champ du dossier d'identification de l'association tenu par CommonLink — ni l'identité
de ses représentants, ni sa forme juridique, ni son numéro d'identification, ni son statut de
validation — n'est écrit, modifié ou alimenté par les données en provenance du prestataire.

### 4.3 Conséquence : absence de tierce introduction au sens de l'article R.561-13 CMF

La tierce introduction, au sens de l'article R.561-13 du code monétaire et financier, désigne
la situation dans laquelle un organisme assujetti s'appuie sur les diligences conduites par un
tiers — et sur les informations que ce tiers lui communique — pour satisfaire ses propres
obligations de vérification de l'identité du client.

Cette situation suppose, nécessairement, un **flux d'informations du tiers introducteur vers
l'organisme assujetti** : l'organisme reçoit les résultats de la vérification, les intègre dans
son propre dossier, et fonde sur eux, en tout ou en partie, sa décision d'entrée en relation.

Or, ici, aucune information de vérification ne transite du prestataire vers CommonLink. Le retour
du prestataire après sa propre procédure ne contient, et ne transmet à CommonLink, aucun résultat
d'identification. CommonLink ne reçoit donc rien qu'elle pourrait intégrer dans son propre dossier
de vérification.

En conséquence, la relation entre CommonLink et son prestataire de paiement **ne constitue pas un
recours à un tiers introducteur** au sens de l'article R.561-13 du code monétaire et financier,
et **aucun contrat de cette nature n'est requis**.

### 4.4 Complément du 21 août 2026 — le canal du prestataire antérieur est définitivement fermé

La présente fiche portait sur le prestataire de paiement effectivement utilisé et se gardait
expressément de se prononcer sur les autres canaux. L'un de ces canaux est désormais clos par
constat matériel plutôt que par analyse.

**Le prestataire antérieur ne subsiste plus dans la plateforme.** Le 20 août 2026, l'intégralité
des composants relatifs à ce prestataire a été retirée du logiciel : points d'entrée du serveur,
appels sortants, écrans, et les deux tables où étaient conservées l'autorisation d'accès et les
données de compte. Le retrait comporte la suppression de ces tables par une migration de base de
données.

**Aucune donnée personnelle n'a été détruite par ce retrait.** Les deux tables étaient vides —
aucune association n'a jamais raccordé de compte auprès de ce prestataire, ni en environnement de
recette ni ailleurs, la plateforme n'étant pas déployée en production. Il n'existait donc aucune
donnée d'identification issue de ce canal, ni à conserver, ni à effacer.

**Portée.** Ce constat ne modifie pas la conclusion du point 4.3, qui portait sur le prestataire en
service : il en écarte définitivement une réserve, en établissant que le canal du prestataire
antérieur n'a jamais transmis d'information de vérification à CommonLink et ne peut plus le faire.
La tâche de contrat de tierce introduction rattachée à ce prestataire dans le référentiel de projet,
déjà mise hors objet par la décision interne D11 du 7 août 2026, est ainsi privée de tout support
technique résiduel.

## 5. Les éléments de preuve

La démonstration du point 4.1 — indépendance et cumul des deux procédures — est établie par
**sept contrôles automatisés** portant sur le comportement de la plateforme à l'ouverture d'une
collecte. Ces contrôles sont réexécutés à chaque modification du logiciel.

Ces contrôles couvrent les deux sens de l'indépendance.

**Premier sens — la validation du prestataire ne suffit pas :** trois scénarios dans lesquels le
prestataire a pleinement validé l'association mais le dossier CommonLink est absent, refusé ou en
cours d'examen. Dans chacun, l'ouverture de la collecte est refusée, et il est établi que le
refus provient de l'absence de validation CommonLink et non d'un problème avec le prestataire.

**Second sens — la validation CommonLink ne suffit pas :** quatre scénarios dans lesquels le
dossier CommonLink est validé mais la relation avec le prestataire est incomplète. Dans chacun,
l'ouverture de la collecte est refusée, et il est établi que le refus provient de l'état de la
relation avec le prestataire et non du dossier CommonLink.

Le fait que chaque refus soit attribué au bon verrou — et non à l'autre — est établi par une
vérification explicite du motif de refus dans chaque contrôle automatisé. Un test du premier sens
passerait sans cette vérification même si le refus provenait en réalité du second verrou, ce qui
ruinerait la démonstration. C'est pourquoi chaque contrôle vérifie non seulement qu'un refus a
eu lieu, mais aussi qu'il est causé par la condition attendue.

| État du dossier CommonLink | État de la relation avec le prestataire | Résultat à l'ouverture | Verrou activé |
|---|---|---|---|
| Jamais validé | Prestataire pleinement validé | Refusé — vérifié | Dossier CommonLink non validé |
| Rejeté après examen | Prestataire pleinement validé | Refusé — vérifié | Dossier CommonLink non validé |
| En cours d'examen | Prestataire pleinement validé | Refusé — vérifié | Dossier CommonLink non validé |
| Validé | Aucun compte prestataire lié | Refusé — vérifié | Relation prestataire absente |
| Validé | Lien avec le prestataire rompu | Refusé — vérifié | Relation prestataire rompue |
| Validé | Vérification prestataire en cours d'examen | Refusé — vérifié | Vérification prestataire incomplète |
| Validé | Autorisation d'encaissement non encore accordée | Refusé — vérifié | Vérification prestataire incomplète |
| Validé | Prestataire pleinement validé | Autorisé | — |

La dernière ligne — l'ouverture autorisée lorsque les deux conditions sont simultanément
remplies — est vérifiée par des contrôles préexistants, non ajoutés à cette occasion.

La démonstration du point 4.2 — absence de reprise de données d'identification depuis le
prestataire — résulte d'un examen de la chaîne de traitement du retour du prestataire. Cet examen
confirme que les données écrites lors du retour sont, **exhaustivement** : jeton d'accès, jeton
de rafraîchissement, date d'expiration des jetons, état de la connexion, état de la vérification
prestataire, indicateur de capacité d'encaissement, indicateur de capacité de virement, adresse
du tableau de bord chez le prestataire, identifiant de l'organisation chez le prestataire, date
de dernière synchronisation. Aucun de ces éléments n'appartient au dossier d'identification de
l'association tenu par CommonLink.

L'ensemble des contrôles automatisés de la plateforme a été exécuté après les modifications
décrites dans la présente fiche, sans régression sur les fonctionnements existants.

## 6. Ce que ce contrôle ne couvre pas

Cette précision est apportée pour éviter toute lecture extensive de la présente fiche.

Cette fiche analyse l'indépendance des diligences de CommonLink par rapport au prestataire de
paiement et l'absence de tierce introduction. **Elle ne porte aucun jugement sur la qualité des
vérifications conduites par le prestataire**, ni sur leur portée, ni sur leur conformité aux
obligations qui lui sont propres en tant qu'établissement de paiement.

L'analyse exposée ci-dessus repose sur le constat que le prestataire ne transmet aucune donnée
d'identification à CommonLink. **Si une reprise de données du prestataire vers les dossiers
CommonLink était un jour introduite**, la question de la tierce introduction devrait être
entièrement réexaminée à la lumière des informations effectivement transmises et de l'usage qui
en serait fait par CommonLink. La présente fiche deviendrait caduque sur ce point.

Cette fiche ne couvre pas non plus :

- la **vérification automatique de l'existence juridique** de l'association auprès des registres
  publics — *l'extension de portée annoncée ici est traitée depuis par la fiche E3 — Contrôle des
  registres publics d'associations* ; le déclenchement reste à la main d'un opérateur, choix
  délibéré, et aucune reconsultation programmée n'est en place ;
- l'**identification des bénéficiaires effectifs** de l'association — *traitée depuis par la fiche
  E3 — Identification des bénéficiaires effectifs d'une association* ;
- le **contrôle des mesures de gel des avoirs**, ni sur l'association, ni sur ses représentants,
  ni sur les donateurs — *traité depuis par les fiches de l'épique E4* ;
- la **surveillance des opérations atypiques** et la déclaration de soupçon ;
- la **conservation** des pièces justificatives et le **rapport annuel** d'activité.

Aucune de ces composantes ne peut être considérée comme couverte par la présente fiche, et le
dispositif LCB-FT dans son ensemble **ne doit pas être présenté comme accompli**.

**Conséquence sur un prérequis non technique.** Le référentiel de projet porte une tâche de
conclusion d'un « Contrat de tierce introduction avec Monerium (art. R.561-13 CMF) » *(intitulé
reproduit verbatim)*. Cette tâche est **sans objet** depuis la **décision interne D11 du 7 août
2026**, aux termes de laquelle aucune tierce introduction n'est pratiquée. C'est cette décision, et
non la présente fiche, qui fonde la mise hors objet ; elle est à clore et non à réaliser, et ne
bloque plus aucun travail.

La présente analyse **corrobore** cette décision pour le seul prestataire qu'elle examine — celui
effectivement utilisé aujourd'hui : le point 4.3 établit qu'aucune information de vérification ne
transite du prestataire vers CommonLink, donc qu'aucun contrat de tierce introduction n'est requis
à ce titre. Elle ne se prononce pas sur les autres canaux, dont celui que nomme l'intitulé de la
tâche.

Cette corroboration demeure conditionnée au constat du point 4.2 : si une reprise de données du
prestataire vers les dossiers CommonLink était introduite, la question devrait être réexaminée.

**Suivi des travaux restants.** Les éléments ci-dessus qui restent à réaliser sont suivis dans le
référentiel interne de gestion de projet aux références suivantes.

| Travail restant | Lien de suivi |
|---|---|
| Contrat de tierce introduction (art. R. 561-13 CMF) — **sans objet**, tâche à clore | [1216210976716071](https://app.asana.com/1/1213718564226627/project/1213723193546726/task/1216210976716071) |
| Surveillance des opérations atypiques et déclaration de soupçon *(épique E5)* | [1216210853624511](https://app.asana.com/1/1213718564226627/project/1213723193546726/task/1216210853624511) · [1216210853624512](https://app.asana.com/1/1213718564226627/project/1213723193546726/task/1216210853624512) · [1216210853624513](https://app.asana.com/1/1213718564226627/project/1213723193546726/task/1216210853624513) · [1216210853624514](https://app.asana.com/1/1213718564226627/project/1213723193546726/task/1216210853624514) |
| Conservation des pièces justificatives et rapport annuel *(épique E6)* | [1216210853624518](https://app.asana.com/1/1213718564226627/project/1213723193546726/task/1216210853624518) · [1216210853624517](https://app.asana.com/1/1213718564226627/project/1213723193546726/task/1216210853624517) · [1216210853624520](https://app.asana.com/1/1213718564226627/project/1213723193546726/task/1216210853624520) |
| Reconsultation périodique programmée des registres publics | *Aucune tâche de suivi identifiée dans le référentiel de projet* |

## 7. Situation de ce contrôle dans le dispositif d'ensemble

| Responsabilité du dispositif | État au 10 août 2026 |
|---|---|
| 1 — Désignation des responsables et cloisonnement des accès | Planifié |
| 2 — Classification des risques et procédures écrites | En cours — la rédaction des documents conditionne les développements correspondants |
| **3 — Entrée en relation et vérification de l'association** | **Cette analyse est réalisée. D'autres composantes de cette responsabilité sont en cours ou planifiées.** |
| 4 — Gel des avoirs | Planifié — chantier prioritaire suivant |
| 5 — Surveillance des opérations atypiques et déclaration | Planifié |
| 6 — Conservation des documents et rapport annuel | Planifié |

## 8. Éléments de traçabilité

L'analyse exposée dans ce document s'appuie sur l'examen du code source de la plateforme tel
qu'il existe au 10 août 2026, sur la ligne de développement dédiée au dispositif LCB-FT.

La règle selon laquelle les diligences de CommonLink et celles du prestataire sont cumulatives
et indépendantes est documentée au sein même du logiciel, à l'endroit précis où les deux
vérifications sont appliquées à l'ouverture d'une collecte. L'objectif est qu'une évolution
ultérieure ne supprime pas l'une des deux conditions en la jugeant redondante avec l'autre.

La liste exhaustive des données écrites lors du retour du prestataire, citée au point 5, est
issue de la lecture directe du traitement correspondant dans le code source au 10 août 2026.

---

*Document établi le 10 août 2026, complété le 21 août 2026 (point 4.4 — retrait intégral du prestataire antérieur). Une fiche de même nature sera produite pour chaque contrôle
du dispositif LCB-FT au fur et à mesure de sa réalisation.*
