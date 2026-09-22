# Vérification du format et du titulaire d'un IBAN de bénéficiaire de paiement

**Fiche de contrôle — hors dispositif LCB-FT (voir avertissement au point 1)**

| | |
|---|---|
| **Entité** | CommonLink |
| **Nature du document** | Fiche descriptive d'un contrôle mis en place, destinée à la commission juridique |
| **Rattachement** | Aucun — ce contrôle protège l'intégrité des paiements sortants (virements aux bénéficiaires d'une campagne) ; il ne relève d'aucune des six responsabilités du dispositif LCB-FT et n'est pas une mesure de vigilance client. Voir `LCB-FT-compliance-overview.md`, section 5.3. |
| **Date de rédaction** | 6 septembre 2026 |
| **État** | Partiellement livré — voir point 4 pour le détail par environnement. **Non fonctionnel en l'état en production tant que le jeton d'API n'a pas été remplacé (point 4.3).** |
| **Rédacteur** | Équipe technique CommonLink |

---

## 1. Avertissement sur la portée de ce document

Ce contrôle porte sur les **bénéficiaires de paiement** (« payees ») qu'une association enregistre
pour recevoir des virements — factures, remboursements de frais, rémunérations — financés par une
campagne. Il est **sans rapport** avec l'identification de l'association elle-même, ses
représentants légaux ou ses bénéficiaires effectifs, qui font l'objet des fiches de l'épique E3.

Ce document ne prétend établir la conformité d'aucune des six responsabilités du dispositif
LCB-FT. Il est produit parce que le contrôle modifie le parcours de paiement d'une campagne, au
même titre que les contrôles déjà recensés en section 5.3 de `LCB-FT-compliance-overview.md`.

## 2. Le risque adressé

Une association peut enregistrer un IBAN de bénéficiaire, puis désigner ce bénéficiaire comme
destinataire d'un paiement financé par une campagne. Deux risques distincts se posent à ce moment :

1. une **erreur de saisie** de l'IBAN (transposition de chiffres, format incorrect) conduirait un
   virement vers un compte inexistant ou vers un tiers non voulu ;
2. le **nom du titulaire du compte** ne correspond pas au bénéficiaire déclaré — par erreur, ou par
   détournement délibéré d'un virement vers un compte contrôlé par un tiers.

## 3. Le contrôle mis en place

Le contrôle se déroule en deux étapes indépendantes, avant qu'un IBAN puisse être proposé comme
destinataire d'un paiement.

**Étape 1 — validation du format (mod-97).** Le format de l'IBAN (structure ISO 13616, clé de
contrôle mod-97) est vérifié côté serveur à l'ajout de l'IBAN
(`PayeeService.addIban` / `isValidIban`), quel que soit le canal d'appel. Depuis ce travail, la même
vérification est également effectuée côté interface avant l'envoi (`lib/iban.ts`,
`isValidIbanFormat`), pour un retour immédiat sans aller-retour serveur — mais le contrôle serveur
reste seul déterminant : toute requête peut être rejouée en contournant l'interface.

**Étape 2 — vérification du titulaire (VOP, Verification of Payee).** Une fois le format validé,
l'association peut déclencher une vérification du nom du titulaire auprès de la banque destinataire,
via l'API **Mollie Verify Payee** (`POST /v2/business-accounts/payee-verifications` — fonctionnalité
en **bêta** chez Mollie à la date de rédaction). Quatre résultats sont possibles : correspondance
exacte, correspondance approximative (avec nom suggéré par la banque), absence de correspondance, ou
vérification impossible (banque non participante, service indisponible). Seule la correspondance
exacte fait passer l'IBAN au statut *vérifié* ; il devient alors sélectionnable pour un paiement.

**Un IBAN non vérifié n'apparaît pas dans le formulaire de paiement.** La liste des bénéficiaires
proposée lors de la création d'un paiement ne présente que les bénéficiaires possédant au moins un
IBAN au statut vérifié et actif — un bénéficiaire sans IBAN vérifié est simplement absent de la
liste, plutôt que sélectionnable puis refusé après coup.

**Réévaluation côté serveur, indépendamment de l'interface.** Le statut vérifié de l'IBAN est
recontrôlé par le serveur à la création du paiement *et* à sa confirmation — un IBAN dévérifié ou
désactivé entre les deux bloque la confirmation, même si la création avait réussi.

## 4. Ce qui est réellement actif, environnement par environnement

Le contrôle de format (étape 1) est **inconditionnel** dans les trois environnements. La
vérification du titulaire (étape 2), en revanche, dépend d'un commutateur (`app.vop.demo-mode`) et
d'un jeton d'API dont l'état diffère.

### 4.1 Local et staging — mode démo

`demo-mode: true`. Aucun appel n'est fait à Mollie : `VopService.verify` retourne
inconditionnellement une correspondance exacte (*MATCH*), quel que soit le contenu de l'IBAN ou du
nom du bénéficiaire. Ce mode permet de dérouler le parcours de bout en bout sans dépendre d'un
compte Mollie réel, mais **ne constitue en aucun cas une preuve que l'appel réel fonctionne, ni une
vérification quelconque du titulaire** : dans ces deux environnements, seul le contrôle de format
(étape 1, mod-97) protège réellement contre une erreur de saisie ou un IBAN inventé. L'étape 2
(déclenchement du bouton « Vérifier ») y reste présente dans le parcours, pour ne pas diverger de
la production, mais elle y est un no-op qui réussit toujours.

### 4.2 Production — mode réel, mais jeton non remplacé

`demo-mode: false` : la production est configurée pour appeler réellement l'API Mollie. C'est
l'état déclaré, mais **le jeton actuellement positionné (`VOP_API_TOKEN`) est l'ancien jeton d'un
autre fournisseur (Qonto), conservé le temps de cette migration** — voir point 4.3. Un appel réel
échouera donc par un rejet d'authentification jusqu'à son remplacement.

### 4.3 Action requise avant que ce contrôle soit réellement opérant en production

Le remplacement du jeton n'a **pas** été effectué dans le cadre de ce travail — il suppose
l'obtention, par un humain habilité, d'un jeton d'accès Mollie (avancé ou clé d'API) portant le
scope `business-account-payee-verifications.write`, ce que ce document ne peut ni générer ni
deviner. Tant que ce remplacement n'est pas fait :

- toute tentative de vérification VOP en production échouera (rejet d'authentification par Mollie) ;
- le contrôle de format (étape 1) reste pleinement opérant, lui, dans les trois environnements ;
- un IBAN ne pourra donc jamais atteindre le statut *vérifié* en production tant que ce point n'est
  pas résolu — ce qui, par construction (point 3), signifie qu'**aucun bénéficiaire ne serait
  sélectionnable pour un paiement en production** avant ce remplacement.

### 4.4 Aucune dérogation au statut *vérifié*

Il n'existe **aucun mécanisme, dans aucun environnement, permettant de porter un IBAN au statut
*vérifié* sans passer par l'action explicite décrite au point 3** (le bouton « Vérifier » reste
requis, aucun IBAN ne passe automatiquement de *format validé* à *vérifié* à l'ajout). En revanche,
ce que cette action vérifie effectivement diffère selon l'environnement : en local et en staging
(point 4.1), elle ne demande rien à une banque et réussit inconditionnellement — **le statut
*vérifié* obtenu dans ces deux environnements ne repose donc que sur le contrôle de format
(mod-97) et ne doit jamais être interprété comme une preuve que le nom du titulaire a été
confirmé.** Seule la production (une fois le point 4.3 résolu) fait réellement porter ce statut sur
une correspondance de nom constatée par une banque.

Un endpoint de forçage manuel a été envisagé pendant l'intégration de Bridge, pour contourner le
fait que l'IBAN de démonstration du sandbox Bridge se termine par `5` — caractère que la simulation
du mode démo mappe sur *correspondance approximative*, laquelle ne produit pas le statut *vérifié*.
Il a été **retiré** : le modèle d'initiation retenu (point 4.5) accepte n'importe quel IBAN comme
destination, il suffit donc d'utiliser un IBAN de test dont le dernier caractère est un chiffre pair
pour dérouler le parcours complet. Aucun contournement n'est nécessaire, donc aucun n'est conservé.

### 4.5 Ce que ce contrôle est devenu depuis l'intégration de Bridge

Le virement est désormais réellement exécuté, par **initiation de paiement Open Banking à
bénéficiaire dynamique** : l'association est le débiteur, elle autorise le virement auprès de sa
propre banque, et les fonds vont directement de son compte vers l'IBAN du bénéficiaire. L'IBAN
vérifié est transmis tel quel comme destination du virement ; il n'existe aucun enregistrement
préalable ailleurs, donc aucun contrôle supplémentaire entre la saisie de l'IBAN et le virement.

Deux conséquences à assumer explicitement :

- **la vérification du titulaire est le seul filtre** entre l'enregistrement d'un IBAN et un
  virement réel vers cet IBAN, ce qui rend le point 4.3 d'autant plus déterminant ;
- l'association conserve la maîtrise finale : aucun mouvement de fonds n'a lieu sans son
  authentification forte auprès de sa banque, laquelle lui réaffiche le montant et le destinataire.
  Un virement non autorisé par elle n'est jamais exécuté.

Par ailleurs, la destination effectivement enregistrée par Bridge est **relue et comparée** à celle
transmise avant que l'URL d'autorisation ne soit remise à l'association ; en cas d'écart, le
virement est refusé. Ce contrôle existe parce que Bridge substitue l'IBAN configuré dans son tableau
de bord lorsque aucun IBAN de bénéficiaire n'est fourni : une substitution silencieuse ne doit pas
pouvoir aboutir à un virement vers un compte que l'association n'a pas choisi.

Cette comparaison est **partielle par construction**. Les endpoints de lecture de Bridge ne
restituent l'IBAN enregistré que masqué (`FR76XXXXXXXXXXXXXXXXXXXX250` dans la documentation) :
seuls les caractères effectivement divulgués — en pratique le code pays, la clé de contrôle et les
derniers caractères — peuvent être confrontés à ceux transmis. Toute divergence sur un caractère
divulgué, comme toute longueur différente, refuse le virement ; une destination qui ne différerait
que sous le masque ne peut en revanche pas être écartée. Cette limite est reprise au point 6.

## 5. Traçabilité et audit

La réponse brute de Mollie (`PayeeIban.vopRawResponse`) est conservée intégralement pour chaque
tentative de vérification, avec l'horodatage (`verifiedAt`). Un IBAN passé au statut *vérifié* et
ayant déjà reçu au moins un paiement ne peut plus être supprimé — le point de menu correspondant est
remplacé par une **désactivation** (`active=false`), qui l'exclut de toute sélection future sans
effacer son historique ni la trace des paiements déjà émis à son bénéfice. Un IBAN vérifié n'ayant
encore reçu aucun paiement peut, lui, être soit désactivé soit supprimé.

## 6. Ce que ce contrôle ne couvre pas

- **Aucune revérification périodique** d'un IBAN déjà vérifié — si le compte est fermé ou renommé
  chez la banque après coup, CommonLink ne le détecte pas automatiquement.
- **En local et en staging, le statut *vérifié* ne résulte d'aucune vérification, même simulée,
  du titulaire** — l'action réussit inconditionnellement, sans considérer l'IBAN ni le nom déclaré
  (points 4.1 et 4.4). Seul le contrôle de format (mod-97) est réellement actif dans ces deux
  environnements.
- **Ce contrôle ne porte pas sur l'exécution du virement.** Depuis l'intégration de Bridge, l'IBAN
  vérifié est transmis tel quel comme destination du virement (bénéficiaire dynamique) : il n'y a
  aucun enregistrement préalable ailleurs, mais aucun contrôle supplémentaire non plus. La
  vérification du titulaire est donc le seul filtre entre la saisie d'un IBAN et un virement réel
  vers cet IBAN — ce qui renforce l'importance du point 4.3.
- **La relecture de la destination auprès de Bridge est une comparaison partielle** — Bridge ne
  restitue l'IBAN enregistré que masqué, seuls les caractères divulgués sont confrontés à ceux
  transmis (point 4.5). Une destination ne différant que sous le masque ne serait pas détectée par
  ce contrôle ; l'autorisation donnée par l'association auprès de sa propre banque reste le filtre
  final avant tout mouvement de fonds. Lorsque Bridge divulgue moins que ce que sa documentation
  décrit, le virement est refusé plutôt que réputé vérifié.
- **La vérification du titulaire ne garantit pas la légitimité du bénéficiaire** — elle établit que
  le nom déclaré correspond au nom tenu par la banque, non que ce bénéficiaire est autorisé ou
  attendu par l'association. Un compte associatif compromis pourrait toujours enregistrer un
  bénéficiaire dont le nom correspond exactement à son IBAN.
- **La fonctionnalité Mollie utilisée est en bêta** à la date de rédaction ; son comportement, sa
  disponibilité ou son contrat d'interface peuvent évoluer du côté de Mollie sans préavis.
- Ce contrôle ne se substitue à aucune vérification d'identité de l'association elle-même — voir
  l'avertissement du point 1.

## 7. Éléments de preuve

Dix scénarios automatisés couvrent ce travail : mode démo (deux cas — retour MATCH inconditionnel
quel que soit l'IBAN, y compris un IBAN qui aurait échoué à un contrôle réel), appel réel à l'API
Mollie mocké (transmission du corps de requête et du jeton,
correspondance exacte, correspondante approximative avec nom suggéré, absence de correspondance,
vérification impossible, échec réseau — six cas), blocage de la suppression d'un IBAN vérifié ayant
reçu un paiement et son inverse (deux cas ; désactivation testée séparément côté service). Le
blocage d'un paiement sur IBAN désactivé est couvert côté création et côté confirmation. L'ensemble
des tests concernés a été exécuté après la modification, sans régression.

S'y ajoutent, depuis l'intégration de Bridge, la vérification que l'IBAN vérifié est bien transmis
tel quel comme destination du virement (bénéficiaire dynamique), le refus du virement lorsque la
destination relue auprès de Bridge contredit celle transmise — sur la clé de contrôle, sur les
derniers caractères ou par une longueur différente — comme lorsqu'elle est masquée au point de ne
plus rien divulguer de vérifiable (cinq cas), son acceptation lorsque la forme masquée restituée est
compatible avec l'IBAN transmis (un cas), et la garantie qu'aucune attestation
on-chain n'est émise avant le règlement effectif constaté par la banque.

---

*Document établi le 6 septembre 2026, mis à jour le 8 septembre 2026 (points 4.4, 4.5, 6 et 7) lors
de l'intégration de Bridge API pour l'exécution réelle des virements par initiation de paiement, puis
le 22 septembre 2026 (points 4.5, 6 et 7) après le premier virement réel en sandbox : la destination
relue auprès de Bridge revient masquée, la comparaison est donc partielle et la fiche ne peut pas la
décrire comme une égalité. Ce contrôle a remplacé un
fournisseur de vérification antérieur (Qonto) par Mollie ; toutes les références à l'ancien
fournisseur ont été retirées du code, de la configuration applicative et du glossaire
(`docs/glossary.md`), à l'exception des fichiers d'environnement dont la rotation du jeton reste à
faire (point 4.3).*
