# Mise à disposition des fonds des dons HelloAsso

**Fiche descriptive — mécanisme hors dispositif LCB-FT, recensé pour son effet sur les décaissements et leur traçabilité**

| | |
|---|---|
| **Entité** | CommonLink |
| **Nature du document** | Fiche descriptive d'un mécanisme mis en place, destinée à la commission juridique |
| **Rattachement** | Aucune des six responsabilités du dispositif LCB-FT. Le mécanisme alimente le solde disponible décrit par la fiche *E7 — Décaissements Bridge, cycle de vie*, point 4.1. |
| **Référence de suivi interne** | `.tasks/mise-a-disposition-fonds-helloasso-sprint.md`, sprints 3 à 6 |
| **Date** | 10 novembre 2026, mise à jour le 11 novembre 2026 (sprints 4 à 6) |
| **État** | Réalisé et vérifié par contrôles automatisés, y compris sur une base de données identique à la production. **Non encore déployé** : HelloAsso n'est pas ouvert en production et ce chantier sera livré d'un bloc. Le comportement réel de HelloAsso au moment d'un versement n'a pas pu être observé (voir point 6). |
| **Rédacteur** | Équipe technique CommonLink |

---

## 1. Avertissement sur la portée de ce document

Ce document décrit comment la plateforme sait si l'argent d'un don payé via HelloAsso est
réellement arrivé sur le compte bancaire de l'association, et ce qu'elle en fait. **Ce mécanisme
ne relève pas du dispositif LCB-FT de CommonLink et n'en satisfait aucune obligation.** Il est
décrit parce qu'il conditionne ce qu'une association peut payer à ses fournisseurs et ce que la
traçabilité affirme au donateur. Aucune conclusion sur la conformité du dispositif LCB-FT ne peut
être tirée de la présente fiche.

Les associations rattachées à Mollie ne sont pas concernées : l'argent d'un don Mollie est
considéré arrivé dès la confirmation du don, comme auparavant.

## 2. Le besoin

Un don payé via HelloAsso est validé tout de suite : il est certifié et le reçu fiscal part. Mais
l'argent reste d'abord chez HelloAsso, qui le reverse plus tard à l'association, à sa demande ou
une fois par mois. Or un paiement fournisseur part du compte bancaire de l'association. Si elle
payait avec des dons encore chez HelloAsso, la traçabilité affirmerait « ce paiement a été financé
par ces dons » alors que ce n'est pas vrai.

**Règle** : un paiement fournisseur ne peut utiliser que des dons arrivés sur le compte de
l'association.

## 3. Ce qui est en place

### 3.1 Un suivi don par don

À la confirmation d'un don HelloAsso, la plateforme retient la référence du paiement HelloAsso qui
l'a réglé. Elle relit ensuite ce paiement chez HelloAsso **toutes les 24 heures** (la première
fois quelques minutes après chaque démarrage du service), pour savoir où en est son versement. HelloAsso ne prévient
pas la plateforme quand il verse : c'est elle qui va vérifier. Un même versement HelloAsso pouvant
regrouper des dons, des adhésions et de la billetterie, le suivi se fait don par don.

Seules les références et les états HelloAsso sont conservés, jamais les données du payeur que
HelloAsso renvoie aussi (identité, adresse) ; elles ne sont pas non plus écrites dans les journaux
techniques, ce que des tests vérifient.

### 3.2 Les étapes d'un don

| Étape | Signification | L'argent peut-il financer un paiement ? |
|---|---|---|
| Chez HelloAsso | Le don est encaissé, l'argent est sur le compte HelloAsso de l'association | Non |
| Versement en préparation | HelloAsso prépare le virement | Non |
| En route | HelloAsso a émis le virement vers la banque de l'association | Non |
| **Arrivé** | Le délai de sécurité est écoulé, ou l'association a confirmé avoir reçu le virement (3.8), ou un curateur a débloqué le don (3.9) | **Oui** |
| Retiré | Le don a été remboursé ou contesté | Non, jamais |

Un don ne revient jamais en arrière, sauf vers « Retiré ». Une information de HelloAsso plus
ancienne que l'étape déjà connue, ou un état que HelloAsso déclare « inconnu », est ignoré et
consigné.

### 3.3 Le délai de sécurité

Un don « en route » devient « arrivé » **deux jours ouvrés** après la date de virement indiquée par
HelloAsso (décision de la direction du 10 novembre 2026, réglable sans développement). Les jours
ouvrés sont ceux des virements interbancaires en euros (calendrier TARGET2 : hors week-ends, 1er
janvier, Vendredi saint, lundi de Pâques, 1er mai, 25 et 26 décembre). Si HelloAsso ne fournit pas
de date de virement, le délai part du jour où la plateforme a constaté le virement.

Le don est daté « arrivé » au moment où la plateforme le constate, **jamais antidaté** : même si la
vérification passe en retard, les dons déjà imputés aux paiements passés ne changent pas.

### 3.4 Le blocage du décaissement

Le solde utilisable pour un paiement fournisseur, à l'enregistrement comme au virement, ne compte
que les dons « arrivés » *(fiche E7, points 4.1 et 4.2)*. Un don retiré après être arrivé cesse
aussi d'y compter. Côté donateur, seuls les dons arrivés sont imputés aux paiements, du plus
anciennement arrivé au plus récent.

### 3.5 Les remboursements

Un remboursement ou une contestation, qu'il soit signalé par une notification HelloAsso ou
constaté lors de la vérification quotidienne, fait passer le don à « Retiré » : il ne peut plus
financer aucun paiement. Il déclenche une alerte à l'équipe technique et **une seule** inscription
au journal de conformité, qui mentionne l'étape où en était le don (notamment s'il était déjà
« arrivé »). Si HelloAsso signale à nouveau le même remboursement, l'alerte est renouvelée, pas
l'inscription.

Chaque don remboursé ou contesté donne sa propre alerte : deux remboursements rapprochés ne se
masquent plus l'un l'autre (à compter du déploiement de cette correction). L'alerte donne à
l'équipe ce qu'il faut pour traiter le remboursement : identifiant du don, montant, date,
association, campagne, numéro du reçu fiscal et état indiqué par HelloAsso, qui distingue un
remboursement d'une opposition bancaire. Elle ne contient aucune donnée du donateur.

Le traitement du remboursement est **manuel** (décision de la direction) : la procédure prévoit que l'équipe vérifie le
remboursement, prévienne l'association et, si un reçu fiscal a été émis, informe le donateur que ce
reçu est annulé, puis retire le don de la commission. La procédure est décrite dans le guide de
bascule HelloAsso (§ 6.6). La plateforme ne marque pas le reçu comme annulé et n'envoie aucun
message au donateur d'elle-même.

### 3.6 Les alertes à l'équipe technique

Chaque situation n'alerte qu'une fois par don ou par association (deux associations concernées en
même temps reçoivent chacune leur alerte) :
- HelloAsso indique ne pas pouvoir transférer l'argent ;
- un état « inconnu » persiste plus de trois jours (valeur proposée par l'équipe technique, sans
  décision produit, réglable) ;
- un don reste « en route » au-delà de cinq jours ouvrés sans être arrivé ;
- la vérification d'une association échoue trois fois de suite ;
- des dons pas encore versés par HelloAsso appartiennent à une association dont le lien HelloAsso
  est coupé ou en panne : ils ne peuvent plus être suivis (l'alerte indique leur nombre et leur
  montant) et demandent un déblocage manuel (décision de la direction Q5) ;
- un don a été confirmé sans que son paiement HelloAsso puisse être identifié (cas non attendu) :
  le don reste valide, mais ne deviendra jamais disponible seul. Le déblocage manuel (3.9) ne couvre
  pas ce cas : il faudrait une intervention de l'équipe technique (point ouvert, voir 4).

### 3.7 L'actualisation à la demande

Le serveur fournit, pour chaque campagne, l'état de ses dons (chez HelloAsso, versement en
préparation, en route, arrivés, retirés), les versements HelloAsso qui les portent, les déblocages
par un curateur et le montant utilisable pour un paiement — le même que celui du contrôle à
l'enregistrement d'un paiement (3.4). Tout est calculé par le serveur. L'onglet Paiements de chaque
campagne les affiche (bloc « Où en sont vos dons »), et le formulaire de paiement indique le montant
utilisable et refuse à l'écran un montant supérieur — le serveur restant seul juge. L'association
peut demander une actualisation depuis cet onglet (l'ouverture de l'onglet en demande une aussi). Le serveur limite à **une vérification par association toutes les
quinze minutes**, vérification quotidienne comprise, y compris si plusieurs demandes arrivent en
même temps ou sur plusieurs serveurs. Une demande de trop n'est pas une erreur : elle reçoit l'état
connu et l'heure à partir de laquelle une nouvelle vérification sera possible.

### 3.8 La confirmation « Virement reçu » par l'association

Le serveur permet à l'association de déclarer qu'un versement HelloAsso est arrivé sur son compte
bancaire, pour rendre ses dons disponibles avant la fin du délai de sécurité. Seul le compte de
l'association peut le faire (décision de la direction Q3), depuis l'onglet Paiements (« J'ai reçu
ce virement », puis une fenêtre de confirmation qui rappelle l'émetteur, la date, le montant sur le
relevé et la date de disponibilité automatique). La confirmation porte sur un versement entier,
toutes campagnes de l'association.

**Le clic de l'association ne suffit pas** : avant toute chose, la plateforme relit chez HelloAsso
chacun des dons concernés. Si un seul n'apparaît pas comme versé dans ce versement, la confirmation
est refusée et rien ne change. Un don remboursé ou contesté reste « Retiré » et n'est pas concerné ;
un remboursement découvert à cette relecture est traité comme en 3.5. Si l'accès HelloAsso de
l'association est coupé ou en panne, la confirmation est refusée : il faut alors passer par le
déblocage manuel (3.9). Si HelloAsso ne répond pas, la confirmation est refusée et peut être
renouvelée plus tard.

Les dons passent « arrivés » en une seule opération, datés du moment de la confirmation (jamais
antidatés). La confirmation est inscrite **une fois** au journal de conformité : qui, quand, quel
versement, combien de dons, quel montant. La répéter ne change rien et n'ajoute pas d'inscription.

Le montant net du versement (crédits moins débits, tel qu'il apparaît sur le relevé bancaire) est lu
chez HelloAsso pour être montré à l'association. Le détail du versement que renvoie HelloAsso
contient les paiements et l'identité des autres payeurs de l'association : seul le total est
conservé ; aucune ligne n'est enregistrée ni écrite dans les journaux techniques, ce que des tests
vérifient. Si ce montant ne peut pas être lu, la confirmation reste possible.

### 3.9 Le déblocage manuel par un curateur

Quand HelloAsso ne permet pas de savoir qu'un versement est arrivé (incident chez HelloAsso,
versement affiché « en route » alors que reçu, accès HelloAsso de l'association coupé ou en panne
avec des dons pas encore versés), un curateur peut rendre des dons disponibles (décisions de la
direction Q4 et Q5). Il doit fournir :
- un motif (2 000 caractères au plus) ;
- la ligne du relevé bancaire de l'association qui montre le virement, **recopiée en texte** (500
  caractères au plus) **et** fournie en fichier (PDF, JPEG ou PNG, 10 Mo au plus, dont le contenu est
  vérifié). Seule cette ligne est demandée, jamais le relevé entier.

Seuls les dons de l'association encore suivis et pas encore arrivés peuvent être débloqués ; un don
déjà arrivé ou retiré fait refuser l'ensemble, sans aucun changement. Le déblocage est fait en une
seule opération : justificatif enregistré, dons « arrivés » datés du moment du déblocage. Il est
inscrit au journal de conformité (curateur, association, dons, montant, motif, référence du
justificatif) ; la ligne du relevé et le fichier n'y figurent pas, car ce journal ne s'efface
jamais. Un don débloqué n'est plus relu chez HelloAsso.

Le justificatif est conservé dans la base de la plateforme. **Durée de conservation : non définie —
décision de la direction en attente (Q8) ; aucune suppression automatique n'est en place.**

Il n'existe pas d'écran pour ce déblocage (décision du 10 novembre 2026) : le curateur passe par
l'interface de programmation de la plateforme.

### 3.10 Avant une déconnexion de HelloAsso

La déconnexion reste toujours possible (décision de la direction Q5). Le serveur fournit, avant,
le nombre et le montant des dons de l'association que HelloAsso n'a pas encore versés (toutes
campagnes ; les dons « en route » ne sont pas comptés : ils deviendront disponibles à la fin du
délai, sans HelloAsso). Ce nombre et ce montant sont aussi inscrits au journal de conformité avec la
déconnexion. La fenêtre de déconnexion affiche cet avertissement à l'association quand ce nombre
n'est pas nul (texte validé par la direction en français ; version anglaise rédigée par l'équipe, la direction lui ayant délégué les textes d'interface en anglais) ; si l'aperçu ne peut pas être lu,
la fenêtre s'affiche sans avertissement. Ces dons ne peuvent plus être suivis : l'équipe technique
est alertée (3.6) et ils passent par le déblocage manuel (3.9).

### 3.11 Recommandation du versement « à la demande »

Une fois son compte HelloAsso relié, l'association voit un conseil (et une entrée de la foire aux
questions) recommandant le versement « à la demande » plutôt que mensuel, pour que ses dons
deviennent plus vite utilisables (décision de la direction Q2). Rien n'est imposé ni vérifié :
le réglage se fait dans l'espace HelloAsso de l'association. Textes validés par la direction, en
français et en anglais.

## 4. Ce qui n'est pas encore en place

| Point ouvert | Prévu |
|---|---|
| Un écran de déblocage manuel pour le curateur | **Hors de ce chantier** (décision du 10 novembre 2026) |
| La durée de conservation du justificatif de déblocage, et sa suppression | Décision de la direction en attente (Q8) |
| Le déblocage d'un don confirmé sans paiement HelloAsso identifiable (cas non attendu, 3.6) | Non prévu à ce jour : le cas n'a jamais été observé |
| Un vrai statut « remboursé » pour un don, la marque d'annulation sur le reçu fiscal et le message automatique au donateur | Traitement manuel sur alerte en attendant (décision de la direction, 3.5) ; l'endroit où l'équipe consigne l'annulation d'un reçu reste à décider |

Limite connue : un don remboursé **après** être arrivé n'est plus relu chaque jour ; son retrait
dépend alors de la notification de remboursement de HelloAsso. S'il avait déjà financé un paiement,
ce paiement reste imputé à ce don dans la traçabilité (aucun modèle de remboursement n'existe dans
la plateforme) ; l'alerte et le journal de conformité en gardent la trace.

## 5. État par environnement

Le suivi est actif dès le démarrage du service, sur tous les environnements, et ne fait aucun appel
à HelloAsso tant qu'aucun don n'est à suivre. Il peut être désactivé par configuration
(`HELLOASSO_CASHOUT_SYNC_ENABLED`), ce qui est le cas dans les tests automatisés. Le délai de
sécurité, l'intervalle entre deux vérifications et les seuils d'alerte se règlent par variables
d'environnement (`HELLOASSO_CASHOUT_*`).

## 6. Ce qui n'a pas pu être vérifié

L'environnement de test de HelloAsso ne permet pas de déclencher un versement (l'association de test
n'y est jamais vérifiée). Les réponses de HelloAsso pour un don « en préparation », « versé » et
« remboursé » ont donc été **reconstituées** à partir de la documentation de HelloAsso, et non
observées. Restent en question auprès de HelloAsso (envoyées le 10 novembre 2026) :
- la date de virement indiquée est-elle celle de l'émission ou celle du crédit attendu ? Le délai
  de deux jours ouvrés a été fixé en supposant l'émission ;
- la date et la référence du versement sont-elles toujours renseignées dès le virement ?
- les montants du détail d'un versement sont-ils en euros ou en centimes ? Le montant net montré à
  l'association suppose des euros (réglable sans développement, `HELLOASSO_CASHOUT_EXPORT_AMOUNT_UNIT`).

Ces points devront être confirmés lors d'un premier versement réel, avant de s'appuyer sur le délai
automatique.

---

*Document établi le 10 novembre 2026, mis à jour le 11 novembre 2026.*
