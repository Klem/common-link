# Décaissements d'une campagne — cycle de vie d'un virement, engagement des fonds et issues d'échec

**Fiche descriptive — hors dispositif LCB-FT (voir avertissement au point 1)**

| | |
|---|---|
| **Entité** | CommonLink |
| **Nature du document** | Fiche descriptive du fonctionnement d'un mécanisme livré, destinée à la direction et à la commission juridique |
| **Rattachement** | Aucun — ce mécanisme porte sur les virements sortants d'une campagne. Il ne relève d'aucune des six responsabilités du dispositif LCB-FT et n'est pas une mesure de vigilance client. Il détaille la ligne « Autorisation bancaire du décaissement par l'association » de `LCB-FT-compliance-overview.md`, section 5.3. |
| **Date de rédaction** | 28 septembre 2026 |
| **État** | Livré et exercé en pré-production. **Non activé en production** — voir point 7. Trois points restent ouverts, recensés au point 8. |
| **Rédacteur** | Équipe technique CommonLink |

---

## 1. Avertissement sur la portée de ce document

Ce document décrit **comment un virement sortant se comporte** : à quel moment l'argent d'une
campagne cesse d'être disponible, à quel moment il est réellement débité, ce qui le rend de
nouveau disponible, et ce que l'association peut faire quand un virement n'aboutit pas.

Il ne décrit **aucune mesure de vigilance** et ne prétend établir la conformité d'aucune obligation
LCB-FT. Il est produit parce que ce mécanisme décide de l'usage des fonds collectés auprès de
donateurs, et qu'un lecteur du tableau de synthèse de l'aperçu général mérite d'en connaître le
détail.

Il ne traite pas non plus de la vérification du bénéficiaire ni de son IBAN, qui font l'objet de la
fiche `verification-payee-iban.md`. Ce document suppose le bénéficiaire déjà vérifié.

---

## 2. Qui détient l'argent — la question préalable

**CommonLink ne détient jamais les fonds d'une campagne, et Bridge non plus.**

Les dons sont encaissés sur le compte de l'association. Lorsqu'elle décaisse, les fonds vont
**directement de son compte bancaire vers l'IBAN du bénéficiaire**. Aucun compte de cantonnement,
aucun compte de passage.

Bridge est un **initiateur de paiement** au sens de la DSP2 : il ne reçoit pas l'argent, il
transmet un ordre de virement à la banque de l'association, qui l'exécute — ou le refuse. Le rôle
de Bridge s'arrête à cet ordre et au compte rendu de son sort.

**Conséquence directe, qui gouverne tout le reste :** l'association est le **débiteur**. Aucun
mouvement de fonds ne peut avoir lieu sans qu'elle se soit authentifiée auprès de sa propre banque,
avec son propre dispositif d'authentification forte. CommonLink ne peut pas déclencher un virement
à sa place, ni l'annuler une fois autorisé.

Ce qui figure dans notre système n'est donc jamais de l'argent, mais **l'état d'un ordre** et une
comptabilité du solde restant allouable.

---

## 3. Le cycle de vie d'un virement, en termes d'argent

Cinq étapes. La colonne qui compte est la troisième.

| Étape | Ce que voit l'association | Où est l'argent | Réversible ? |
|---|---|---|---|
| **1. Enregistré** | Le paiement est saisi, pas encore émis | Sur le compte de l'association. **Déjà retiré du solde disponible de la campagne.** | Oui — sans conséquence |
| **2. En attente de validation** | « Autoriser » : un lien mène à sa banque | Sur le compte de l'association. Toujours retiré du solde. | Oui — l'abandon est sans conséquence |
| **3. Virement autorisé** | L'ordre est parti | La banque exécute. **Point de non-retour côté CommonLink.** | Non |
| **4. Virement exécuté** | Le bénéficiaire est crédité | Sur le compte du bénéficiaire | Non |
| **5. Refusé** | Le motif traduit est affiché | Sur le compte de l'association. **Rendu au solde disponible.** | Sans objet — rien n'est parti |

**L'attestation au registre public n'est publiée qu'à l'étape 4**, après règlement effectivement
constaté par la banque. Jamais avant. C'est la contrainte qui explique la prudence de tout ce qui
suit : une attestation publiée est irrétractable, alors qu'attendre ne coûte rien.

---

## 4. Quand les fonds sont verrouillés — et quand ils sont rendus

### 4.1 Le verrouillage a lieu dès l'enregistrement, pas au moment du virement

C'est le point le plus contre-intuitif du dispositif, et le plus important pour la direction.

Le **solde disponible** d'une campagne, celui qu'affiche le tableau de bord, vaut :

> dons encaissés − virements exécutés − **virements enregistrés mais non encore exécutés**

Autrement dit : à la seconde où une association enregistre un paiement de 5 000 €, ces 5 000 €
**cessent d'être allouables**, alors même qu'aucun euro n'a quitté son compte et qu'elle n'a encore
rien autorisé auprès de sa banque.

**Pourquoi.** Sans cette réservation, une association disposant de 5 000 € pourrait enregistrer
cinq paiements de 5 000 € — chacun individuellement couvert par le solde — puis les autoriser tous.
La campagne aurait décaissé 25 000 € pour 5 000 € collectés. La réservation est ce qui rend ce
scénario impossible.

Le tableau de bord montre les deux moitiés côte à côte : la carte **« Solde disponible »** donne
ce qui reste allouable, la carte **« En attente »** donne exactement la somme réservée par les
paiements ouverts. Les deux s'additionnent pour redonner le total non encore décaissé.

**Effet de bord assumé :** une association qui enregistre des paiements sans les émettre immobilise
la capacité de sa campagne. Les montants lui reviennent dès que le paiement est abandonné ou
refusé, mais tant qu'il reste ouvert, ils sont indisponibles.

### 4.2 Le contrôle au moment de l'émission porte sur un solde différent

Au moment où l'association confirme un paiement, le contrôle de provision se fait contre les **dons
encaissés moins les seuls virements déjà exécutés** — les paiements enregistrés sont exclus du
calcul.

La raison est arithmétique : le paiement que l'on est en train de confirmer est lui-même enregistré.
S'il était compté, il se bloquerait lui-même.

### 4.3 La restitution, et les deux garde-fous qui l'encadrent

Un montant réservé retourne au solde disponible dans deux cas seulement : **abandon** ou **refus**.
Dans les deux cas, rien n'a été débité.

Deux protections encadrent cette restitution, chacune écrite après un incident réel.

**Premier garde-fou — le lien d'autorisation est révoqué *avant* que le montant soit rendu.**

Un lien d'autorisation refusé par la banque reste techniquement utilisable chez Bridge : un refus
ne le consomme pas. Rendre le montant au solde en laissant le lien vivant ouvre donc une fenêtre
pendant laquelle l'association peut à la fois réallouer les fonds *et* voir le virement initial
partir. C'est exactement ce qui s'est produit le 22 septembre 2026 : **120 € sont partis contre des
fonds déjà rendus au solde.** L'ordre des opérations est désormais inversé — révocation d'abord,
restitution ensuite.

**Second garde-fou — un sursis avant de rendre un montant dont le lien vient d'expirer.**

Un lien d'autorisation a une durée de vie (un jour en production, cinq minutes en pré-production
pour rendre le scénario observable). Le problème : la banque considère l'association « entrée dans
le tunnel » dès le premier écran, et conserve cet état pendant **toute** l'authentification. Une
association qui commence à autoriser une minute avant l'échéance est donc encore dans cet état quand
le lien meurt.

Rendre le montant à cet instant revient à le remettre au solde pendant que la banque exécute le
virement. Le système attend donc **trois minutes** (une minute en pré-production) après le dernier
signe de vie avant de libérer quoi que ce soit. Attendre ne coûte rien — une expiration n'a pas
d'échéance — alors que libérer trop tôt est irrattrapable.

### 4.4 Un refus terminal ne rend pas le virement réémettable

C'est la distinction structurante du dispositif, et elle n'est pas intuitive :

- **Un abandon n'est pas un échec.** Délai d'autorisation expiré, lien annulé, association qui ferme
  son onglet, ou qui clique sur « Annuler » chez sa banque : personne n'a jamais demandé à la
  banque d'exécuter quoi que ce soit. Le paiement redevient **réémettable**, avec un nouveau lien,
  et son montant retourne au solde.
- **Un refus bancaire est terminal.** La banque a été interrogée et a dit non. Le paiement est
  définitivement retiré, son montant rendu au solde, et l'association doit en créer un nouveau.

Sans cette distinction, une association qui laissait expirer un écran voyait son paiement retiré
définitivement. Elle en recréait alors un autre — ce qui a produit, le 23 septembre 2026, **quatre
lignes identiques issues d'un seul paiement**.

---

## 5. Quand un virement n'aboutit pas — ce que l'association peut faire

Chaque refus est traduit en une phrase en français, à partir d'un code stable transmis par la
banque. La phrase brute renvoyée par le système bancaire n'est **jamais** affichée : elle est
conservée pour le support, mais elle mélange des codes techniques et des messages en anglais, dont
l'un exposait un identifiant interne dans une bulle d'aide destinée à une association.

Les causes sont de deux origines, réunies dans le même affichage parce qu'elles appellent la même
conduite : **dix-sept motifs transmis par la banque** via Bridge, et **sept causes internes** —
délai d'autorisation expiré, lien annulé, service bancaire injoignable, contrôle de destination, et
autres échecs survenus avant qu'aucune banque ne soit interrogée.

Elles se rangent en trois familles, qui appellent trois conduites différentes. Les tableaux
ci-dessous mélangent les deux origines : ce qui compte pour l'association n'est pas d'où vient le
motif, mais ce qu'elle doit en faire.

### 5.1 Relancer le virement tel quel

Rien à corriger : l'opération peut être réémise à l'identique.

| Cause | Ce que l'association lit |
|---|---|
| Autorisation non terminée dans le délai | « Autorisation non terminée dans le délai imparti. Relancez le virement. » |
| Autorisation annulée chez sa banque | « Autorisation annulée depuis l'interface de votre banque. » |
| Délai d'autorisation expiré | « Le délai d'autorisation a expiré. Relancez le virement. » |
| Lien d'autorisation annulé | « Le lien d'autorisation a été annulé. Relancez le virement. » |
| Trop d'opérations pour la banque | « Nombre d'opérations trop élevé pour votre banque. Réessayez plus tard. » |
| Service bancaire momentanément injoignable | « Le service bancaire n'a pas pu être joint. Réessayez dans quelques minutes. » |
| Provision insuffisante | « Provision insuffisante sur le compte de l'association. » — relancer **après approvisionnement** |

Sur ces paiements, l'interface propose directement un bouton **« Réessayer »**, qui réémet le
**même** paiement avec un nouveau lien d'autorisation. Il ne crée pas de seconde ligne comptable :
c'est précisément ce qui a produit les quatre doublons du 23 septembre.

### 5.2 Corriger avant de réémettre

Réémettre à l'identique échouera de la même manière.

| Cause | Ce que l'association lit |
|---|---|
| Compte invalide ou inexistant | « Numéro de compte invalide ou inexistant. Vérifiez l'IBAN du bénéficiaire et le compte de l'association. » |
| Compte clôturé | « Un des comptes du virement est clôturé. Vérifiez l'IBAN du bénéficiaire et le compte de l'association. » |
| Compte bloqué | « Un des comptes du virement est bloqué. Vérifiez l'IBAN du bénéficiaire et le compte de l'association. » |
| Nom ou adresse du bénéficiaire insuffisants | « Nom ou adresse du bénéficiaire insuffisants pour votre banque. » |
| Informations du compte payeur incomplètes | « Informations du compte payeur incomplètes côté banque. » |
| Identifiant d'une des parties invalide | « Identifiant d'une des parties invalide ou manquant. » |

**Une décision de rédaction mérite d'être signalée.** Sur les trois premiers motifs, la banque dit
« le compte » sans préciser lequel. Ce serait normalement le compte crédité — mais l'IBAN du
bénéficiaire ayant déjà été vérifié chez nous auprès de sa banque, un tel refus désignera souvent
le compte de l'**association**. Les messages disent donc « un des comptes du virement » et invitent
à vérifier les deux, plutôt que d'envoyer l'association corriger un IBAN qui n'a rien.

### 5.3 Ne jamais réessayer sans contacter la banque

| Cause | Ce que l'association lit |
|---|---|
| Suspicion de fraude | « Virement bloqué par votre banque pour suspicion de fraude. Contactez-la avant toute nouvelle tentative. » |
| Motif réglementaire | « Refusé pour motif réglementaire. Contactez votre banque. » |
| Opération interdite sur ce type de compte | « Votre banque interdit ce type d'opération sur ce compte. Contactez-la. » |

### 5.4 Deux cas particuliers, arbitrés explicitement

**Le refus « côté payeur » est traité comme terminal — c'est un choix, pas un oubli.** Ce motif
recouvre chez Bridge deux situations opposées : une autorisation délibérément refusée par
l'association, et un simple défaut de provision. La première ne doit pas être réémise, la seconde
le peut. Faute de pouvoir les distinguer, le paiement est retiré et l'association doit en créer un
nouveau si elle le souhaite. Réémettre en aveugle sur une ambiguïté a été jugé plus coûteux.

**Un refus sans motif communiqué** reste possible : certaines banques ne renvoient aucune raison.
Le message dit alors « Refusé par votre banque, sans motif communiqué », sans inventer de cause.

---

## 6. Ce qui rattrape un virement oublié

Le compte rendu d'un virement arrive normalement par une notification de Bridge. Ce canal ne suffit
pas, pour deux raisons :

1. Bridge ne renvoie jamais une notification qu'il considère délivrée. Une notification acceptée
   mais non appliquée — un incident applicatif de notre côté — bloquerait un virement pour
   toujours. C'est arrivé **trois fois le 23 septembre 2026**.
2. Certaines banques (LCL, Nickel sont documentées comme telles) **ne rapportent aucun statut
   d'exécution** : leur compte rendu s'arrête à « autorisé » et ne dit jamais « exécuté ».

Un **balayage périodique** relit donc auprès de Bridge tout virement engagé dont l'état n'a pas
bougé depuis un certain temps (toutes les trente minutes en production, pour les virements sans
nouvelles depuis six heures). Il ne décide jamais rien de lui-même : il rejoue la lecture et laisse
la logique ordinaire conclure.

Au-delà d'un second seuil — trois jours en production — un virement toujours en cours est **signalé
à un humain** plutôt que promu automatiquement. Une attestation au registre public étant
irrétractable, rien n'est jamais conclu sur une absence de nouvelle.

**Ce rattrapage a une limite importante, décrite au point 8.2.**

---

## 7. Ce qui est réellement actif, environnement par environnement

| | Production | Pré-production | Local |
|---|---|---|---|
| Virements réels | **Non** | Oui — sauf si le mode démonstration y est réactivé | Optionnel |
| Émission possible depuis l'interface | **Non — bouton désactivé** | Oui | Oui |
| Durée de vie d'un lien | 1 jour | 5 minutes | 1 jour |
| Sursis avant restitution | 3 minutes | 1 minute | 3 minutes |
| Balayage de rattrapage | 30 min / 6 h / 3 jours | 5 min / 15 min / 1 h | idem production |

**La production n'exécute aucun virement à ce jour.** Les identifiants Bridge de production ne sont
pas provisionnés et le mode démonstration reste actif par défaut. Dans cette configuration, un
virement serait **simulé** — aucun appel à la banque, aucune authentification, aucun mouvement de
fonds — et pourtant affiché comme réglé. Plutôt que de laisser ce risque ouvert, la fonctionnalité
se **ferme** en production : le bouton d'émission est désactivé et affiche « Les paiements ne sont
pas encore activés ».

Ce verrou n'est pas seulement d'interface. Le serveur refuse la création comme la confirmation d'un
paiement dans la même condition : une requête rejouée directement sur l'interface de programmation
échoue au lieu d'être simulée. En local et en pré-production, le parcours simulé reste au contraire
disponible — c'est ce qui permet d'exercer l'onglet Paiements sans identifiant bancaire.

**Activer la production suppose deux gestes** : provisionner les identifiants Bridge de production,
et désactiver explicitement le mode démonstration sur cet environnement.

---

## 8. Ce que ce dispositif ne couvre pas

Trois points sont ouverts. Aucun n'est un défaut de rédaction : ce sont des travaux non faits.

### 8.1 Deux motifs annoncent une alerte technique qui n'est pas branchée

Deux causes de refus désignent un défaut de **notre** intégration, non un problème de
l'association : une requête mal formée de notre part, et une date d'exécution refusée alors que
nous n'en transmettons aucune. Le message affiché dit « Nos équipes sont prévenues ».

**Ce n'est pas vrai aujourd'hui.** Aucune alerte technique n'est émise sur ces deux motifs. Le
message est une promesse que le code ne tient pas. Le brancher est un travail court, non réalisé.

### 8.2 Un virement figé chez une banque silencieuse n'est pas escaladé

Le signalement décrit au point 6 mesure l'ancienneté d'un virement sur la **date de dernière
réponse de Bridge**. Or le balayage de rattrapage interroge Bridge, qui répond — ce qui rafraîchit
cette date sans que rien n'ait bougé.

**Conséquence :** le signalement ne se déclenche en pratique que si **Bridge lui-même est
injoignable** pendant toute la fenêtre. Il répond donc à « notre fournisseur est tombé », jamais à
« la banque ne rapporte rien » — c'est-à-dire précisément le cas pour lequel il a été écrit, celui
de LCL et Nickel. Un virement figé à l'état « autorisé » chez l'une de ces banques, relu avec succès
à chaque balayage, ne sera **pas** signalé.

Une correction avait été engagée la semaine du 22 septembre — un second horodatage ne bougeant qu'à
un changement d'état réel. Elle a été **retirée avant toute mise en service** : l'exigence a été
écartée le 25 septembre. Couvrir ce cas demande donc un travail qui reste entier.

**Portée du risque :** l'argent est parti et le bénéficiaire est crédité ; ce qui manque est
l'attestation au registre public et l'avertissement à un opérateur. Aucun fonds n'est en jeu, mais
la promesse de traçabilité l'est.

### 8.3 Deux motifs de refus n'ont jamais été vérifiés en conditions réelles

L'environnement de test de Bridge permet de provoquer quinze des **dix-sept motifs transmis par la
banque** (les sept causes internes, elles, sont produites par notre propre code et sont donc toutes
exerçables). **Deux motifs bancaires n'ont aucun jeu d'identifiants de test** : la provision
insuffisante et l'identifiant de partie invalide. Leur formulation française n'a donc jamais été
confrontée à un refus réel — elle est tirée de la documentation, pas d'une observation.

La provision insuffisante étant un cas très probable en usage courant, ce point mérite une
vérification dès la première exécution réelle en production.

---

## 9. Suivi des travaux restants

| Point ouvert | Nature | Bloquant pour |
|---|---|---|
| Brancher l'alerte technique sur les deux motifs d'intégration (8.1) | Développement | Rien — mais le message affiché est inexact tant que ce n'est pas fait |
| Détecter un virement figé chez une banque qui ne rapporte pas (8.2) | Développement | La traçabilité des virements exécutés par LCL et Nickel |
| Vérifier la formulation des deux motifs non testables (8.3) | Observation en production | Rien — vérification à la première occurrence réelle |
| Provisionner les identifiants Bridge de production et désactiver le mode démonstration (7) | Prérequis non technique | **Toute exécution réelle de virement en production** |

---

## 10. Références techniques

Pour le lecteur technique, la logique décrite ici se trouve dans :

- `PayoutService` — calcul des soldes disponible et confirmable, fermeture de la fonctionnalité par
  environnement ;
- `PayoutConfirmer` — réservation, restitution, refus terminal, règlement et mise en file de
  l'attestation ;
- `BridgeWebhookService` — traitement des comptes rendus de Bridge, distinction abandon / refus,
  sursis avant restitution ;
- `BridgePayoutReconciler` — balayage de rattrapage et signalement ;
- `.tasks/bridge-error-codes.md` — matrice complète des motifs de refus, avec le texte d'origine de
  Bridge en regard de chaque traduction française.
