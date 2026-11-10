# Rédaction du reçu fiscal et mention du mode de versement

**Fiche de contrôle — reçu au titre des dons aux organismes d'intérêt général**

| | |
|---|---|
| **Entité** | CommonLink |
| **Nature du document** | Fiche descriptive de corrections apportées à un document remis aux donateurs, destinée à la commission juridique |
| **Responsabilité concernée** | Émission du reçu fiscal prévu aux articles 200, 238 bis et 978 du code général des impôts. **Hors dispositif LCB-FT** — cette fiche ne relève d'aucune des six responsabilités du dispositif interne. |
| **Référence de suivi interne** | *Aucune tâche de suivi identifiée dans le référentiel de projet* |
| **Origine** | Revue juridique du 17 août 2026 portant sur le parcours de campagne, le reçu fiscal et le widget de don |
| **Date de correction** | 17 août 2026 |
| **État** | Corrigé et vérifié par contrôles automatisés. Non encore déployé — la mise en production de la plateforme est conditionnée à l'achèvement du dispositif LCB-FT. |
| **Rédacteur** | Équipe technique CommonLink |

---

## 1. Avertissement sur la portée de ce document

Ce document décrit **quatre corrections de rédaction** apportées au reçu fiscal et **une
information nouvelle** qui y figure désormais. Il ne décrit pas le dispositif d'émission des reçus
dans son ensemble, ni le mandat fiscal qui en conditionne la délivrance.

Il ne porte sur aucun contrôle LCB-FT. Aucune conclusion sur la conformité du dispositif LCB-FT ne
peut en être tirée ; celle-ci fait l'objet des fiches E1 à E4 et du document de synthèse
*LCB-FT — vue d'ensemble*.

Deux points restent ouverts et sont énoncés en section 6.

Depuis le 10 novembre 2026, il décrit aussi, en section 9, le contrôle du montant payé qui
conditionne l'émission du reçu.

## 2. Ce qui a été constaté

Le reçu délivré au donateur comportait quatre affirmations que CommonLink n'est pas en position de
faire, et une mention inexacte.

| Mention d'origine | Ce qui posait problème |
|---|---|
| « Reçu produit avec CommonLink — commonlink (outil gratuit). » | **Faux.** CommonLink facture une commission de 10 % HT sur les dons collectés. La mention figurait sur un document remis au donateur, qui pouvait en déduire que l'intégralité de son versement parvenait à l'association. |
| « Modèle conforme au Cerfa n° 11580*05. » | CommonLink **suit** le modèle Cerfa ; apprécier la conformité d'un document à ce modèle relève de l'administration fiscale, pas de l'éditeur du logiciel. |
| « Il ouvre droit à la réduction d'impôt prévue à l'article 200 du CGI (66 % du montant). » | Présenté comme un **droit acquis**. Le bénéfice de la réduction dépend du respect de conditions légales et de la situation fiscale propre au donateur, que CommonLink ne connaît pas. |
| « Mode de versement : Virement, prélèvement ou carte bancaire. » | Ce n'était pas le mode de versement, mais **la liste des modes acceptés par la plateforme**. Le reçu affirmait donc trois modes à la fois, dont deux étaient faux pour tout don donné. |

Ces constats ont été établis le 17 août 2026 lors d'une revue juridique du document. Ils n'ont pas
été signalés par un tiers et n'ont donné lieu à aucun incident : **aucun reçu fiscal n'a été délivré
à un donateur réel à ce jour**, la plateforme n'encaissant encore aucun don.

## 3. Pourquoi le mode de versement était générique

La plateforme n'enregistrait pas le moyen de paiement effectivement utilisé.

Le prestataire de paiement le communique — un don par carte, par virement ou par prélèvement est
identifié comme tel dans sa réponse — mais cette information n'était ni lue ni conservée. Le
générateur de reçu n'avait donc rien à imprimer, et une phrase couvrant tous les cas y suppléait.

Le moyen de paiement n'est connu qu'**après** que le donateur l'a choisi sur la page de paiement du
prestataire, c'est-à-dire au moment où le paiement est confirmé — et non lorsque le don est initié.

## 4. Ce qui a été mis en place

### 4.1 Rédaction corrigée

| Mention corrigée |
|---|
| « Reçu produit avec CommonLink. Ce document ne préjuge pas de l'éligibilité fiscale du bénéficiaire. » |
| « Établi selon le modèle Cerfa n° 11580*05. » |
| « Il est susceptible d'ouvrir droit à la réduction d'impôt prévue à l'article 200 du CGI (66 % du montant), sous réserve du respect des conditions légales et de la situation fiscale propre au donateur. » |

Le taux affiché (66 % ou 75 % selon la catégorie d'éligibilité de l'organisme) était déjà déterminé
à partir du mandat fiscal en vigueur ; seule la formulation entourant ce taux a changé.

**La tarification de CommonLink ne figure pas sur le reçu.** La mention « outil gratuit » a été
supprimée parce qu'elle était fausse, non pour être remplacée par l'énoncé de la commission : le
reçu est un document fiscal du donateur, la relation commerciale entre CommonLink et l'association
n'y a pas sa place. Elle relève des conditions générales et de la facturation de l'association.

### 4.2 Mode de versement réel

Le moyen de paiement communiqué par le prestataire est désormais enregistré sur le don au moment de
la confirmation du paiement, puis traduit en libellé français sur le reçu :

| Moyen rapporté par le prestataire | Libellé imprimé |
|---|---|
| Carte de crédit, carte de débit | Carte bancaire |
| Virement | Virement bancaire |
| Prélèvement SEPA | Prélèvement SEPA |
| Bancontact, iDEAL, Sofort, EPS, Giropay, Przelewy24, Trustly, Belfius, KBC | Virement bancaire en ligne |
| PayPal | PayPal |
| Apple Pay | Apple Pay |
| Moyen non reconnu par la plateforme | Le code du prestataire, tel quel |
| Moyen non communiqué | Non précisé |

Les deux dernières lignes sont délibérées. Un moyen de paiement inconnu de la plateforme est imprimé
tel quel plutôt que rattaché de force à une catégorie : un nouveau moyen offert par le prestataire ne
peut ainsi jamais devenir silencieusement une mention fausse. Et lorsque le moyen n'a pas été
communiqué — cas d'un don confirmé par le mécanisme de rattrapage interne plutôt que par la
notification du prestataire — le reçu porte « Non précisé », ce qui est exact, au lieu de deviner.

*Complément du 2 novembre 2026.* Pour un don encaissé par HelloAsso, le reçu imprime le moyen de
paiement rapporté par HelloAsso sous l'un des libellés suivants : Carte bancaire, Prélèvement SEPA,
Virement bancaire, Chèque, Espèces. Un code inconnu de la plateforme est, là aussi, imprimé tel quel.

### 4.3 Portée dans le temps

**Les reçus déjà émis conservent leur rédaction d'origine.** Cela n'est pas un choix de mise en
œuvre mais une conséquence du dispositif de preuve : le document PDF est conservé octet pour octet,
et son empreinte cryptographique est inscrite sur registre distribué. Régénérer un reçu invaliderait
l'empreinte publiée et détruirait la preuve d'intégrité qui en fait l'intérêt.

Aucun reçu n'ayant été délivré à ce jour, cette limite est sans portée pratique en l'état.

## 5. Les éléments de preuve

Sept contrôles automatisés vérifient la rédaction **sur le texte réellement extrait du document PDF
produit**, et non sur le code qui le compose — un contrôle portant sur le code ne prouverait pas que
la phrase parvient au donateur.

| Ce qui est vérifié |
|---|
| Le document ne contient pas le mot « gratuit », et porte la mention « Reçu produit avec CommonLink. » |
| Le document porte « Établi selon le modèle Cerfa » et ne porte pas « conforme au Cerfa » |
| Le document porte « susceptible d'ouvrir droit », le taux applicable, et la réserve sur les conditions légales |
| Un don par carte porte « Mode de versement Carte bancaire », et non l'ancienne liste des trois modes |
| Un virement porte « Virement bancaire » |
| Un moyen de paiement inconnu est imprimé tel quel |
| Un moyen de paiement non communiqué porte « Non précisé » |

Deux contrôles supplémentaires portent sur l'enregistrement du moyen de paiement à la confirmation
du don : l'un vérifie que le moyen rapporté est bien conservé, l'autre qu'un moyen absent laisse
l'information vide sans empêcher la confirmation du don.

## 6. Ce que cette correction ne couvre pas

**L'annulation d'un reçu en cas de remboursement imposé n'est pas traitée.** Si un don devait être
remboursé après émission du reçu — décision de justice, erreur manifeste, opposition bancaire — la
plateforme n'offre aujourd'hui aucun moyen d'invalider le reçu délivré. Il n'existe ni marque
d'annulation sur le document conservé, ni fonction permettant de la poser, ni exclusion des reçus
annulés d'un décompte annuel. Ce point a été explicitement écarté du périmètre de la revue du
17 août 2026 et reste entier.

**Le décompte annuel des reçus délivré à l'administration n'est pas produit.** L'article 222 bis du
CGI impose à l'organisme bénéficiaire de déclarer chaque année le montant global des dons et le
nombre de reçus délivrés. Le reçu rappelle cette obligation au bénéficiaire ; la plateforme ne
produit pas ce décompte. Ce travail relève du générateur de rapport annuel, non réalisé (voir
*LCB-FT — vue d'ensemble*, section 7.2, responsabilité n° 6).

**La commission de 10 % HT n'est mentionnée sur aucun document remis au donateur.** La décision de
ne pas la porter sur le reçu (section 4.1) est un choix de rédaction du reçu, non une décision sur
l'information du donateur en général. Savoir si, et où, le donateur doit être informé de la part
prélevée par la plateforme est un point d'appréciation qui n'est pas tranché ici.

## 7. Éléments de traçabilité

| | |
|---|---|
| **Générateur du reçu** | `api/src/main/kotlin/org/commonlink/service/ReceiptService.kt` |
| **Enregistrement du moyen de paiement** | `api/src/main/kotlin/org/commonlink/service/MollieWebhookService.kt` |
| **Conservation** | colonne `donations.payment_method`, migration `V65__donation_payment_method.sql` |
| **Contrôles automatisés** | `api/src/test/kotlin/org/commonlink/service/ReceiptServiceTest.kt`, `MollieWebhookServiceTest.kt` |

## 8. Revue complémentaire du 1er septembre 2026

Un second retour, formulé le 14 août 2026, reprenait pour l'essentiel les constats de la section 2
(déjà corrigés le 17 août, voir ci-dessus) et ajoutait trois points. Aucun n'a donné lieu à une
modification de code.

**Récépissé de déclaration en préfecture.** Le retour demandait de vérifier si cette mention devait
figurer au côté du RNA/SIREN déjà affiché (section « BÉNÉFICIAIRE DU VERSEMENT »). Le formulaire
Cerfa n° 11580*05 ne comporte pas de champ distinct pour un récépissé de préfecture : sa section
d'identification du bénéficiaire demande dénomination, adresse, objet, catégorie et un identifiant
RNA ou SIRET/SIREN — c'est ce que le reçu affiche déjà. Point clos, sans changement.

**Adresse postale obligatoire au tunnel de don.** Signalée comme restant à faire, cette exigence est
en réalité satisfaite depuis le 15 juillet 2026 côté API (`CreateGuestDonationRequest`, champs
`donorAddressLine1`/`donorPostalCode`/`donorCity` en `@NotBlank`) et depuis le 3 août 2026 côté
formulaire (`donationSchema.ts`, `DonationForm.tsx`) — donc avant ce retour. La mesure de l'impact
sur la conversion, mentionnée dans le même retour, reste un sujet analytics hors périmètre de cette
fiche.

**Orthographe d'un exemple (Vallauris/06220) et graphie « Maïolino ».** Ces deux mentions n'existent
que dans `landing-page/`, hors du périmètre technique suivi par l'équipe (ne contient ni le
générateur de reçu ni un document remis à un donateur). Signalé pour correction séparée si la page
reste en usage.

## 9. Contrôle du montant payé avant émission du reçu (5 et 10 novembre 2026)

**Le constat.** Un reçu est émis dès qu'un don est confirmé : il est numéroté, son empreinte est
inscrite sur la chaîne et il ne peut plus être annulé (section 6). Jusqu'au 5 novembre 2026, un don
était confirmé dès que le prestataire de paiement déclarait le paiement réussi, sans que la
plateforme vérifie que la somme effectivement payée était bien celle du don.

**Ce qui a été mis en place le 5 novembre 2026.** Avant de confirmer un don, la plateforme relit le
paiement chez le prestataire (Mollie ou HelloAsso) et compare la somme payée au montant du don.
- Les deux sommes sont égales, au centime près : le don est confirmé et le reçu émis.
- Elles diffèrent, ou la somme payée n'est pas communiquée : le don reste **en attente**. Aucun
  reçu n'est émis, aucune empreinte n'est inscrite. L'équipe technique reçoit une alerte, et une
  entrée est inscrite au journal de conformité, une seule fois par don. Aucune tolérance d'écart
  n'est admise.

Le reçu imprime toujours le **montant du don**, jamais la somme payée au prestataire.

**Ce qui a été ajouté le 10 novembre 2026.**
- L'entrée du journal de conformité porte désormais, outre le montant attendu et la somme payée,
  leur **écart**.
- *Contribution volontaire à HelloAsso.* Sur la page de paiement HelloAsso, le donateur peut ajouter
  une contribution destinée à HelloAsso. Le 10 novembre 2026, sur des dons de l'environnement de
  test de HelloAsso faits avec une contribution, cette contribution n'apparaissait ni dans la somme
  payée, ni dans le total de la commande, ni sur l'attestation de paiement, ni dans l'espace
  HelloAsso de l'association. Un don avec contribution est donc confirmé normalement, et son reçu
  porte le seul montant du don.
- *Surveillance.* Pour détecter un éventuel changement de comportement de HelloAsso, la plateforme
  signale à l'équipe technique, **sans bloquer la confirmation**, toute commande HelloAsso qui ne
  correspond pas au paiement qui confirme le don : un autre total, plusieurs lignes, ou une ligne
  qui n'est pas le don. Ce signalement intervient une seule fois par don. Il ne porte que sur des
  identifiants et des montants, jamais sur les données du payeur.

**Les éléments de preuve.**

| Ce qui est vérifié |
|---|
| Une somme payée égale au montant du don, quelle que soit son écriture (25,5 ou 25,500), confirme le don sans aucun signalement |
| Une somme différente, ou absente, laisse le don en attente, avec une alerte et une seule entrée au journal, qui porte l'écart |
| Un don HelloAsso payé avec une contribution, à partir de la réponse réelle de HelloAsso relevée le 10 novembre 2026 (anonymisée), est confirmé au montant du don, sans signalement |
| Le reçu d'un tel don imprime le montant du don, en chiffres et en lettres, sur le texte extrait du document PDF |
| Une commande dont le total diffère du paiement, qui comporte plusieurs lignes ou une ligne autre que le don, confirme le don et donne lieu à un seul signalement |
| Une commande conforme, ou dont HelloAsso ne communique ni total ni lignes, ne donne lieu à aucun signalement |
| Un total ou des lignes de commande dans une forme inattendue n'empêchent pas la lecture du paiement |

**Ce que ce contrôle ne couvre pas.**
- L'absence de la contribution dans la somme payée a été observée dans l'**environnement de test**
  de HelloAsso. Une confirmation écrite a été demandée à HelloAsso le 10 novembre 2026 et reste
  sans réponse.
- Un don resté en attente pour écart de montant n'est pas traité automatiquement : il appartient à
  l'équipe de l'examiner.
- Le contrôle porte sur la confirmation du don. Un remboursement survenant après l'émission du reçu
  relève du point ouvert de la section 6.

**Éléments de traçabilité.**

| | |
|---|---|
| **Contrôle du montant** | `api/src/main/kotlin/org/commonlink/service/PaymentAmountGuard.kt` |
| **Confirmation HelloAsso et signalement des commandes** | `api/src/main/kotlin/org/commonlink/service/HelloAssoCheckoutService.kt` |
| **Confirmation Mollie** | `api/src/main/kotlin/org/commonlink/service/MollieWebhookService.kt` |
| **Alertes** | `PAYMENT_AMOUNT_MISMATCH`, `HELLOASSO_ORDER_AMOUNT_DELTA` (`TechnicalAlertService.kt`) |
| **Journal de conformité** | événement `PAYMENT_AMOUNT_MISMATCH` |
| **Contrôles automatisés** | `PaymentAmountGuardTest.kt`, `HelloAssoCheckoutServiceTest.kt`, `HelloAssoClientTest.kt`, `ReceiptServiceTest.kt` ; réponses HelloAsso anonymisées dans `api/src/test/resources/helloasso/cashout/` |

---

*Document établi le 17 août 2026, complété le 1er septembre 2026, le 2 novembre 2026 (moyens de paiement HelloAsso) et le 10 novembre 2026 (contrôle du montant payé, section 9). Il décrit l'état des corrections
arrêté à cette date et sera mis à jour si la rédaction du reçu évolue.*
