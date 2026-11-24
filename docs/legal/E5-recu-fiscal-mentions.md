# Rédaction du reçu fiscal et mention du mode de versement

**Fiche de contrôle — reçu au titre des dons aux organismes d'intérêt général**

| | |
|---|---|
| **Entité** | CommonLink |
| **Nature du document** | Fiche descriptive de corrections apportées à un document remis aux donateurs, destinée à la commission juridique |
| **Responsabilité concernée** | Émission du reçu fiscal prévu à l'article 200 du code général des impôts (donateurs particuliers ; le modèle Cerfa cite aussi les articles 238 bis et 978, dont les cases ne sont jamais cochées — section 11). **Hors dispositif LCB-FT** — cette fiche ne relève d'aucune des six responsabilités du dispositif interne. |
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

Depuis le 23 novembre 2026, il décrit aussi, en section 10, la signature apposée sur les reçus.

## 2. Ce qui a été constaté

Le reçu délivré au donateur comportait quatre affirmations que CommonLink n'est pas en position de
faire, et une mention inexacte.

| Mention d'origine | Ce qui posait problème |
|---|---|
| « Reçu produit avec CommonLink — commonlink (outil gratuit). » | **Faux.** CommonLink facture une commission sur les dons collectés (8 % HT pour les organismes établis en France ; voir section 11). La mention figurait sur un document remis au donateur, qui pouvait en déduire que l'intégralité de son versement parvenait à l'association. |
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
17 août 2026.

Pour les dons encaissés via HelloAsso, à compter du déploiement de cette correction, un
remboursement ou une opposition bancaire signalés par HelloAsso déclenchent une alerte à l'équipe,
une par don, avec le numéro du reçu fiscal concerné. La procédure prévoit alors une annulation
**à la main** (décision de la direction) : l'équipe informe le donateur que son reçu est annulé et
ne peut pas servir à sa déclaration de revenus. Le
reçu conservé par la plateforme ne porte toujours aucune marque d'annulation, et l'endroit où
l'équipe consigne l'annulation reste à décider. Un remboursement que HelloAsso ne signale pas avec
la référence du don n'est pas détecté. Pour les dons encaissés via Mollie, rien n'a changé.

**Le décompte annuel des reçus délivré à l'administration n'est pas produit.** L'article 222 bis du
CGI impose à l'organisme bénéficiaire de déclarer chaque année le montant global des dons et le
nombre de reçus délivrés. Le reçu rappelle cette obligation au bénéficiaire ; la plateforme ne
produit pas ce décompte. Ce travail relève du générateur de rapport annuel, non réalisé (voir
*LCB-FT — vue d'ensemble*, section 7.2, responsabilité n° 6).

**La commission ne figure pas sur le reçu.** La direction a tranché le 23 novembre 2026 : la
commission (8 % HT pour les organismes établis en France) est portée à la connaissance du donateur
par les conditions générales donateur et la page Tarifs ; rien n'est ajouté au reçu (section 11).

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

## 10. Signature des reçus (23 novembre 2026)

**Ce qui a été constaté.** Jusqu'au 23 novembre 2026, le cadre « Signature » du reçu restait vide :
le reçu portait le nom et la fonction du signataire habilité, sans signature.

**Ce qui a été mis en place.**
- *Nature de la signature.* Le cadre « Signature » porte désormais une **image de signature** du
  signataire habilité. Ce n'est **pas une signature électronique** au sens du règlement eIDAS :
  l'image reproduit une signature manuscrite sur le document, sans certificat ni procédé
  d'identification.
- *Deux façons de la fournir*, au choix de l'association, dans l'onglet « Mandat fiscal » de ses
  paramètres :
  - importer une image (PNG ou JPEG) ;
  - dessiner la signature à l'écran (image PNG).

  Un troisième mode, « saisir » (nom du signataire imprimé dans une police manuscrite), a existé
  jusqu'au 23 novembre 2026, sans avoir été mis en production ; il a été retiré par l'arbitrage de
  la direction (section 11) : ce n'est pas une signature.
- *Contrôles à l'enregistrement*, appliqués par le serveur (l'écran applique les mêmes règles et réduit en outre l'image à 600 × 200 pixels avant l'envoi) : nom (au plus
  255 caractères) et fonction (au plus 100 caractères) non vides ; image reconnue comme PNG ou JPEG
  par son contenu, et non par le type annoncé, d'au plus 200 Ko et 1 200 × 600 pixels, lisible.
  Aucun autre format n'est accepté.
- *Attestation.* Chaque enregistrement exige que l'utilisateur coche la phrase suivante, faute de
  quoi il est refusé :
  « J'atteste être le signataire désigné ci-dessus ou disposer de son autorisation expresse pour
  apposer sa signature sur les reçus fiscaux émis au nom de l'association. »
- *Journal.* Chaque enregistrement inscrit au journal de conformité, inaltérable et chaîné, un
  événement `RECEIPT_SIGNATURE_ATTESTED`, dans la même opération que l'enregistrement lui-même :
  nom et fonction du signataire, mode, empreinte SHA-256 de l'image, texte exact de l'attestation, utilisateur et horodatage. Le signataire et sa signature
  peuvent être modifiés à tout moment ; chaque modification exige une nouvelle attestation et
  produit une nouvelle entrée.
- *Nom et fonction du signataire.* Ils ne peuvent plus être modifiés depuis l'onglet
  « Informations » du profil : uniquement par ce bloc, sous attestation. Un enregistrement du profil
  qui les contiendrait encore les ignore.
- *Mandat fiscal.* La signature d'un mandat exige, outre le nom et la fonction du signataire, une
  signature enregistrée, l'objet social et l'adresse du siège. Depuis l'arbitrage du 23 novembre
  2026 (section 11), aucun reçu n'est émis sans signature : pour une association dont le mandat
  est déjà actif sans signature, la collecte est suspendue jusqu'à ce qu'elle en enregistre une.
- *Reçus déjà émis.* Ils ne sont pas modifiés : chaque reçu est produit une seule fois et conservé
  octet pour octet. Une signature enregistrée ou
  modifiée ne s'applique qu'aux reçus émis ensuite.
- *Pas de reçu sans signature.* Si la signature enregistrée ne peut pas être imprimée (image
  illisible), le reçu n'est pas émis : l'erreur est consignée dans les journaux techniques et
  l'émission est retentée chaque minute (section 11).
- *Mise en page.* Le cadre garde la hauteur qu'il avait ; l'image est réduite pour y tenir, sans
  déformation.
- *Courriel d'envoi du reçu.* Lorsque le donateur a donné sans créer de compte, le courriel porte un
  paragraphe l'invitant à créer son compte donateur avec la même adresse e-mail, pour retrouver
  l'ensemble de ses dons et de ses reçus au même endroit, avec un lien vers la page d'inscription
  donateur. Le lien ne contient pas l'adresse e-mail. Un donateur qui a déjà un compte ne reçoit
  pas ce paragraphe.

La rédaction du reçu a ensuite été revue par l'arbitrage du 23 novembre 2026 (section 11).

**Les éléments de preuve.**

| Ce qui est vérifié |
|---|
| Une attestation non cochée, un nom ou une fonction vides ou trop longs, un mode inconnu sont refusés, sans rien enregistrer ni inscrire au journal |
| Une image qui n'est ni PNG ni JPEG par son contenu (SVG, GIF, WebP), de plus de 200 Ko, de plus de 1 200 × 600 pixels ou illisible est refusée |
| Une image est exigée à chaque enregistrement ; le mode « saisir » est refusé |
| Un enregistrement met à jour le signataire, remplace la signature précédente et inscrit au journal une entrée portant l'empreinte SHA-256 de l'image et le texte exact de l'attestation |
| L'enregistrement du profil n'écrit plus le nom ni la fonction du signataire |
| La signature d'un mandat est refusée sans signature enregistrée |
| Le reçu contient l'image de signature enregistrée, réduite pour tenir dans son cadre |
| Une signature absente ou illisible empêche l'émission du reçu (section 11) |
| Le courriel du reçu porte l'invitation et son lien pour un donateur sans compte, et ne la porte pas pour un donateur inscrit |
| Sur une base PostgreSQL réelle créée par les migrations : la table de signature refuse un mode autre que « importer » ou « dessiner » et une signature sans image, et sa ligne disparaît avec l'association |

**Ce que cela ne couvre pas.**
- L'image de signature n'apporte aucune preuve de l'identité du signataire : l'attestation est une
  déclaration de l'utilisateur de l'association, conservée au journal.
- Seule la dernière signature est conservée en base. Le journal garde, pour chaque enregistrement,
  l'empreinte de l'image et non l'image elle-même ; l'image figure en revanche dans chaque reçu émis
  avec elle.
- *Conservation des reçus.* La durée est fixée depuis le 23 novembre 2026 (six ans à compter de
  l'établissement du reçu), mais la suppression à son terme n'est pas mise en œuvre (section 11).
- La mise en page a été resserrée le 23 novembre 2026 : avec les données d'essai, le reçu tient
  désormais sur une page (section 11).

**Éléments de traçabilité.**

| | |
|---|---|
| **Enregistrement et contrôles** | `api/src/main/kotlin/org/commonlink/service/SignatoryService.kt`, `api/src/main/kotlin/org/commonlink/controller/SignatoryController.kt` (`GET` et `PUT /api/association/me/signatory`, `GET /api/association/me/signatory/image`) |
| **Stockage** | table `association_signature` (migration `V98__association_signature.sql`, retour arrière `U98__association_signature.sql`) |
| **Impression sur le reçu** | `api/src/main/kotlin/org/commonlink/service/ReceiptService.kt` |
| **Mandat fiscal** | `api/src/main/kotlin/org/commonlink/service/MandateService.kt` |
| **Courriel** | `api/src/main/kotlin/org/commonlink/service/DonationReceiptService.kt`, `SmtpEmailService.kt` |
| **Journal de conformité** | événement `RECEIPT_SIGNATURE_ATTESTED` |
| **Contrôles automatisés** | `SignatoryServiceTest.kt`, `SignatoryControllerTest.kt`, `AssociationSignatureRepositoryTest.kt`, `MandateServiceTest.kt`, `ReceiptServiceTest.kt`, `AssociationServiceTest.kt`, `AssociationControllerTest.kt`, `DonationReceiptEmailTest.kt`, `SmtpEmailServiceTest.kt` |

## 11. Arbitrage de la direction sur la rédaction et l'émission du reçu (23 novembre 2026)

**Ce qui a été constaté.** Une relecture du reçu, menée le 23 novembre 2026, a soumis à la direction
neuf questions : qui certifie, articles cités, taux de 75 %, phrase de bas de page, mentions
obligatoires de l'organisme, organismes reconnus d'utilité publique, durée de conservation,
associations déjà actives sans signature, validation finale. La direction les a tranchées le même
jour ; elle valide elle-même la rédaction, sur la base de cette vérification.

**Ce qui a été mis en place.**
- *Certification.* Le reçu reprend la formule du Cerfa : « Le bénéficiaire certifie sur l'honneur
  que les dons et versements qu'il reçoit ouvrent droit à la réduction d'impôt prévue à
  l'article : », suivie de trois cases — « 200 du CGI » cochée, « 238 bis du CGI » et « 978 du
  CGI » jamais cochées (donateurs particuliers uniquement ; les associations d'intérêt général
  n'ouvrent pas droit à la réduction d'IFI). L'ancienne phrase par laquelle le donateur
  « attestait » l'intention libérale de son versement est supprimée : le donateur n'atteste rien
  sur le reçu. Le titre du modèle Cerfa (« Articles 200, 238 bis et 978 du code général des
  impôts ») est conservé tel quel.
- *Aucun pourcentage.* Le reçu n'indique plus de taux de réduction (66 % ou 75 %). Le courriel qui
  l'accompagne indique : « Ce reçu vous permet de bénéficier d'une réduction d'impôt sur le revenu,
  selon votre situation (article 200 du CGI). » Il ne cite plus les articles 238 bis et 978.
- *Bas de page.* « Reçu produit avec CommonLink. Ce document ne préjuge pas de l'éligibilité
  fiscale du bénéficiaire. » est remplacé par : « Reçu établi par [nom de l'association], qui en
  certifie le contenu sous sa seule responsabilité. Édité et transmis par CommonLink au nom et
  pour le compte de l'association, en vertu d'un mandat. » Le rappel de l'article 1740 A et de la
  déclaration annuelle (article 222 bis) est conservé.
- *Adresse du siège.* Le reçu imprime désormais l'adresse complète (voie, code postal et ville) ;
  auparavant, seule la voie figurait lorsqu'elle était renseignée.
- *Organismes reconnus d'utilité publique.* La catégorie est retirée du choix proposé à la
  signature du mandat : le reçu ne porte pas les dates du décret de reconnaissance et de sa
  publication au Journal officiel qu'exige le Cerfa pour cette catégorie. Restent « Organisme
  d'intérêt général » et « Organisme d'aide aux personnes en difficulté ». La base de données
  refuse la catégorie retirée.
- *Préalables à tout reçu.* Trois éléments sont exigés : une signature enregistrée (section 10),
  l'objet social et l'adresse du siège (voie, code postal et ville). Faute de l'un d'eux :
  - la signature du mandat fiscal est refusée ;
  - pour une association dont le mandat est déjà actif, **la collecte est suspendue** : son widget
    et sa page publique affichent la collecte comme indisponible, tout don est refusé avant la
    création d'un donateur invité et avant tout paiement, et la publication d'une campagne est
    refusée (motif journalisé `RECEIPT_PREREQUISITES_MISSING`, ou `FISCAL_MANDATE_MISSING` sans
    mandat actif) ;
  - l'onglet « Mandat fiscal » de l'association affiche la collecte comme suspendue et la liste
    des éléments manquants, avec un lien pour les compléter ; l'écran de publication les signale
    aussi. Les mêmes règles s'appliquent à l'écran et sur le serveur.
  - En dernier recours, si un paiement était malgré tout confirmé pour une telle association, le
    reçu **n'est pas émis** : l'erreur est consignée dans les journaux techniques et l'émission est
    retentée chaque minute jusqu'à ce que les éléments soient complétés. L'enregistrement du don
    sur la chaîne attend le reçu.
- *Signature en écriture manuscrite retirée.* Le mode « saisir » (nom du signataire imprimé dans
  une police manuscrite) est supprimé : ce n'est pas une signature. Seules subsistent l'image
  importée ou dessinée, accompagnée du nom et de la fonction du signataire en clair (section 10).
- *Conservation.* La politique de confidentialité indique désormais, pour les données du don et
  le reçu fiscal : « six ans à compter de l'établissement du reçu, puis suppression ». L'article 17
  des conditions générales des organismes reçoit la phrase : « CommonLink conserve les doubles des
  reçus pour le compte de l'organisme pendant six ans à compter de leur établissement, et les lui
  restitue sur demande. » Ces deux textes sont en vigueur au 23 novembre 2026 ; la modification
  des conditions générales en fait une nouvelle version, que chaque organisme accepte à nouveau.
- *Espace donateur.* L'estimation de la réduction d'impôt porte la mention « Estimation
  indicative, selon votre situation. »

**Constats vérifiés à cette occasion.**
- *Numérotation.* Chaque association a sa propre série, par année civile (`2026-0001`, puis
  `2027-0001`). Le numéro est attribué dans la même opération que l'enregistrement du reçu : un
  échec annule l'attribution, la série ne comporte pas de trou. Les numéros suivent l'ordre
  d'émission des reçus, et non la date du don : un reçu émis par la reprise automatique (au plus
  une minute plus tard) peut porter un numéro supérieur à celui d'un don payé juste après.
- *Date du versement.* Le reçu porte la date du paiement communiquée par le prestataire (pour
  HelloAsso, la date du paiement sur le compte HelloAsso de l'association), et non celle du
  virement vers son compte bancaire. À défaut, ou si cette date est incohérente, l'heure de
  confirmation du paiement.
- *Commission.* La commission est de 8 % HT pour les organismes établis en France (et non 10 %,
  chiffre retenu par erreur dans les sections 2 et 6 de la version précédente de cette fiche).
  Le donateur en est informé par les conditions générales donateur et la page Tarifs ; rien n'est
  ajouté au reçu.

**Les éléments de preuve.**

| Ce qui est vérifié |
|---|
| Le reçu porte la formule de certification et les trois cases, seule la case 200 étant cochée ; il ne contient ni pourcentage ni phrase d'attestation du donateur |
| Le bas de page nomme l'association et le mandat ; l'ancienne mention « ne préjuge pas » a disparu |
| Le courriel du reçu porte la nouvelle phrase et ne cite que l'article 200 |
| Avec les données d'essai, y compris un objet social sur deux lignes, la catégorie « personnes en difficulté », les éléments de naissance et une adresse sur deux lignes, le reçu tient sur une page |
| La signature du mandat est refusée tant qu'il manque la signature, l'objet social ou un élément de l'adresse du siège |
| Pour une association à mandat actif à laquelle il manque l'un de ces éléments : widget et page publique affichent la collecte indisponible, un don est refusé avant toute création, la publication est refusée avec un motif journalisé |
| Le reçu n'est pas émis sans mandat actif, sans l'un des éléments, ou avec une image de signature illisible ; rien n'est enregistré et la reprise automatique ne s'interrompt pas (le non-épuisement du numéro découle de l'opération unique et n'est pas contrôlé sur une base PostgreSQL réelle) |
| La catégorie « reconnu d'utilité publique » et le mode « saisir » sont refusés, par le serveur et par la base de données (base PostgreSQL réelle créée par les migrations) |

**Ce que cela ne couvre pas.**
- *Suppression au terme des six ans.* Elle est annoncée mais **non mise en œuvre** : aucun
  traitement ne supprime les reçus ni les données des dons. Le premier reçu concerné atteindra le
  terme en 2032 ; la purge doit être en place avant le 31 décembre de l'année des six ans. Les
  doubles sont conservés en base et accessibles au donateur depuis son espace ; l'association n'y
  a pas accès depuis le sien : la restitution « sur demande » prévue par les conditions générales
  n'a pas de fonction dédiée et se fait à la main.
- *Associations déjà actives.* Elles sont informées par le bandeau de leur espace ; le courriel
  qui doit les prévenir est envoyé à la main par l'équipe, l'application ne l'envoie pas.
- *Indicateurs non alignés.* Le badge de l'onglet « Mandat fiscal » reste « Actif » pendant que
  la collecte est suspendue, et l'onglet de diffusion du widget ne tient pas compte des éléments
  manquants ; la collecte reste néanmoins refusée par le serveur.
- *Conditions générales.* Le premier paragraphe de l'article 17 cite encore « les catégories
  mentionnées aux articles 200 et 238 bis du code général des impôts » ; il n'a pas été modifié.
- *Taux de 75 %.* L'estimation de l'espace donateur applique 75 % sans plafond ; le plafond légal
  (2 000 € pour les dons de 2026) n'y est pas pris en compte.
- *Remboursement d'un don après émission du reçu.* La direction a fixé la cible : reçu au statut
  « annulé », trace au journal de conformité, message au donateur, archivage avec les doubles.
  Rien de cela n'est mis en œuvre ; la section 6 reste exacte.
- *Déclaration annuelle (article 222 bis).* Cible fixée : export par association du nombre de
  reçus et du montant total, en janvier, avec un courriel de rappel de l'échéance. Non mis en
  œuvre.
- Un objet social très long peut encore faire passer la fin du reçu sur une seconde page.

**Éléments de traçabilité.**

| | |
|---|---|
| **Rédaction du reçu** | `api/src/main/kotlin/org/commonlink/service/ReceiptService.kt` |
| **Courriel** | `api/src/main/kotlin/org/commonlink/service/SmtpEmailService.kt` |
| **Préalables** | `api/src/main/kotlin/org/commonlink/service/ReceiptPrerequisiteService.kt` ; appliqués par `MandateService.kt` (signature du mandat), `PublicWidgetService.kt` (collecte), `CampaignService.kt` (publication), `ReceiptService.kt` (émission) |
| **Base de données** | migration `V98__association_signature.sql` (contraintes de la signature et des catégories du mandat), retour arrière `U98__association_signature.sql` |
| **Textes publiés** | `landing-page/messages/fr.json` : `legal.cguAssociations` (article 17) et `legal.politiqueConfidentialite.articles[4].table` (article « Combien de temps ») |
| **Écrans** | `app/src/components/settings/MandateTab.tsx`, `SignatoryCard.tsx`, `app/src/components/campaign/PrePublishModal.tsx` |
| **Contrôles automatisés** | `ReceiptServiceTest.kt`, `SmtpEmailServiceTest.kt`, `MandateServiceTest.kt`, `MandateControllerTest.kt`, `PublicWidgetServiceTest.kt`, `PublicWidgetServiceIntegrationTest.kt`, `CampaignServiceTest.kt`, `DonationReceiptServiceTest.kt`, `AssociationSignatureRepositoryTest.kt`, `SignatoryServiceTest.kt` ; côté écran `MandateTab.test.tsx`, `SignatoryCard.test.tsx`, `PrePublishModal.test.tsx` |

---

*Document établi le 17 août 2026, complété le 1er septembre 2026, le 2 novembre 2026 (moyens de paiement HelloAsso), le 10 novembre 2026 (contrôle du montant payé, section 9) et le 23 novembre 2026 (signature des reçus, section 10 ; arbitrage de la direction, section 11). Il décrit l'état des corrections
arrêté à cette date et sera mis à jour si la rédaction du reçu évolue.*
