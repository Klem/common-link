# Acceptation des conditions générales et de la politique de confidentialité — textes définitifs, versions et preuve

**Fiche de contrôle — quel texte est accepté, par qui, quand, et comment la preuve en est conservée**

| | |
|---|---|
| **Entité** | CommonLink |
| **Nature du document** | Fiche descriptive du mécanisme d'acceptation des conditions générales et de la politique de confidentialité, destinée à la commission juridique |
| **Responsabilité concernée** | Preuve de l'acceptation expresse des conditions générales (notice ACPR applicable à la collecte ouverte au public ; art. 1740 A du code général des impôts pour la garantie que l'association donne sur les reçus fiscaux) ; depuis le 24 novembre 2026, preuve de l'acceptation de la politique de confidentialité. **Hors dispositif LCB-FT** — cette fiche ne relève d'aucune des six responsabilités du dispositif interne. |
| **Référence de suivi interne** | *Aucune tâche de suivi identifiée dans le référentiel de projet* |
| **Origine** | Mise en production prochaine : les fenêtres d'acceptation n'affichaient qu'un texte provisoire (« PLACEHOLDER ») enregistré le 26 août 2026 ; les textes définitifs étaient publiés sur le site vitrine mais absents de la plateforme |
| **Date de mise en place** | 16 novembre 2026 ; politique de confidentialité ajoutée le 24 novembre 2026 |
| **État** | CGU et CGV (16 novembre 2026) : réalisé, vérifié par contrôles automatisés (tests backend et frontend, migration rejouée sur une base PostgreSQL vierge), intégré à la branche principale. Politique de confidentialité (24 novembre 2026) : réalisée, vérifiée par contrôles automatisés (tests backend et frontend) ; la migration V99 est écrite mais n'a été rejouée sur aucune base PostgreSQL ni couverte par un test ; non commitée, non déployée. |
| **Rédacteur** | Équipe technique CommonLink |

---

## 1. Avertissement sur la portée de ce document

Ce document décrit comment la plateforme présente les conditions générales et la politique de confidentialité, et conserve la preuve de leur acceptation. Il ne porte sur aucun contrôle LCB-FT et aucune conclusion sur le dispositif LCB-FT ne peut en être tirée.

**Il ne se prononce pas sur le contenu juridique des textes**, qui relève de la commission juridique. Il garantit seulement que le texte affiché et accepté sur la plateforme est, mot pour mot, celui publié sur le site vitrine à la date indiquée.

**La vérification n'est pas une inspection visuelle des pages déployées** : elle porte sur ce que le code produit (contenu enregistré, texte rendu par les composants), au moyen de tests automatisés.

## 2. Les trois textes

Les textes définitifs sont ceux du site vitrine :

| Texte du site vitrine | Nom dans la plateforme | Date de version actuelle |
|---|---|---|
| Conditions générales d'utilisation applicables aux donateurs (`/conditions-generales-utilisation`) | « CGU » | 24 novembre 2026 (article 14 : remboursement par l'organisme depuis son compte HelloAsso ou Mollie) |
| Conditions générales d'utilisation applicables aux organismes bénéficiaires (`/conditions-generales-utilisation-associations`), avec leurs annexes | « CGV » | 24 novembre 2026 (article 17 : conservation des doubles des reçus fiscaux, voir `E5-recu-fiscal-mentions.md`, section 11 ; article 22 : commission facturée en fin de mois, réglée par virement, sans mandat de prélèvement) |
| Politique de confidentialité (`/politique-confidentialite`) | « PRIVACY » | 24 novembre 2026 (première version enregistrée dans la plateforme) |

Le nom « CGV » est historique : il n'existe pas de conditions générales de vente distinctes. Il désigne désormais les conditions applicables aux associations.

**Une seule source.** Le texte n'est saisi qu'à un endroit : le fichier de textes du site vitrine. La plateforme le reprend tel quel à chaque mise en production. Les liens internes des textes (politique de confidentialité, politique cookies, réclamations, contrat type) pointent vers les pages correspondantes du site vitrine.

## 3. Versions et historique

**Chaque modification du texte crée une nouvelle version**, enregistrée en base au démarrage de la plateforme, si et seulement si le texte a changé. Une version déjà enregistrée n'est jamais modifiée ni supprimée : l'historique complet reste en base.

Le numéro de version combine la date « Version en vigueur au … » du texte et une empreinte numérique de son contenu (par exemple `2026-09-09-a1b2c3d4e5f6`). Deux corrections publiées le même jour donnent donc deux versions distinctes. Si deux textes différents produisaient le même numéro (cas théorique), la plateforme refuse de démarrer plutôt que de laisser un numéro désigner deux textes.

**Une nouvelle version est en vigueur dès sa mise en production.** Il n'est pas possible de programmer une date d'entrée en vigueur ultérieure. Revenir à une version antérieure du logiciel ne remet pas en vigueur l'ancien texte : la version la plus récemment enregistrée reste en vigueur. De même, remettre mot pour mot un texte déjà publié (par exemple annuler une correction) ne le remet pas en vigueur : il faut lui donner une nouvelle date « Version en vigueur au … ». La plateforme le signale dans ses journaux techniques au démarrage.

Les deux textes provisoires du 26 août 2026 restent dans l'historique. En production, aucune acceptation n'a été enregistrée sur ces textes (constat du 16 novembre 2026). La politique de confidentialité n'a pas eu de texte provisoire : sa première version enregistrée est celle du 24 novembre 2026.

## 4. Qui accepte quoi, et quand

### 4.1 Donateur

À **chaque don**, le donateur coche une case d'acceptation des **CGU donateurs**. Le texte s'ouvre dans une fenêtre depuis le formulaire. Depuis le 16 novembre 2026, la case relative aux « CGV » a été retirée, ce texte ne le concernant pas.

Le formulaire comporte une seconde case obligatoire, relative à la **politique de confidentialité**. Elle existait déjà, mais rien n'en était enregistré. Depuis le 24 novembre 2026, son libellé est « J'ai pris connaissance de la politique de confidentialité », un lien ouvre le texte, et son acceptation est enregistrée avec la version affichée, comme celle des CGU donateurs. Le donateur accepte donc deux textes à chaque don.

### 4.2 Association

L'association accepte **les conditions qui lui sont destinées** (texte nommé « CGV », voir section 2) et, depuis le 24 novembre 2026, **la politique de confidentialité** :

- **à la première publication d'une campagne** suivant chaque nouvelle version de l'un ou l'autre texte : la fenêtre de publication présente une case par texte, et un texte déjà accepté dans sa version en vigueur apparaît coché et n'est pas redemandé (contrôle vérifié par le serveur : tous les textes en attente sont vérifiés avant qu'aucune acceptation ne soit enregistrée ; s'il en manque un, la publication est refusée et le refus est journalisé, voir fiche E6) ;
- **à sa connexion** : lorsqu'une nouvelle version de l'un ou l'autre texte a été publiée depuis sa dernière acceptation, une fenêtre qu'elle ne peut pas fermer lui présente les textes en attente, l'un après l'autre (les conditions qui lui sont destinées d'abord, puis la politique de confidentialité). Pour les conditions, la case indique « J'ai lu et j'accepte ce texte au nom de l'association » ; pour la politique de confidentialité, qui s'informe et ne s'accepte pas, « J'ai pris connaissance de ce texte au nom de l'association », comme la case de publication (« J'ai pris connaissance de la politique de confidentialité ») et celle du don. Dans les trois cas, la case cochée est enregistrée comme une acceptation, avec la version affichée.

Seule la version en vigueur est présentée. Si le texte a changé plusieurs fois entre deux connexions, l'association n'accepte qu'une fois, la version la plus récente.

Jusqu'au 16 novembre 2026, l'association acceptait le texte nommé « CGU » ; ce texte étant devenu celui des donateurs, elle accepte depuis cette date celui des associations (décision du 16 novembre 2026). Les acceptations déjà enregistrées gardent le nom du texte accepté : en production, il n'y en avait aucune (section 3).

## 5. La preuve conservée

Chaque acceptation enregistre : qui (donateur ou association), le texte, **la version exacte affichée à l'écran**, la date et l'heure, et le nom et l'adresse électronique du signataire tels qu'ils étaient au moment de l'acceptation (ils ne sont pas relus ensuite depuis un profil modifiable). Pour un donateur, le don concerné ; pour une association, la campagne dont la publication a déclenché l'acceptation (aucune pour une acceptation à la connexion). Une acceptation est enregistrée par texte : deux à chaque don (CGU donateurs et politique de confidentialité), une par texte en attente à la publication d'une campagne ou à la connexion d'une association.

**Version lue = version acceptée.** Si une nouvelle version est publiée pendant qu'une personne a le formulaire ouvert, son acceptation est refusée : le nouveau texte lui est présenté et la case est décochée. Une preuve ne peut donc jamais désigner un texte que la personne n'a pas pu lire.

Le responsable de la conformité consulte ces preuves depuis son espace (historique d'un compte, acceptations des donateurs d'une campagne). Le texte exact de chaque version est conservé en base ; il n'existe pas d'écran pour relire une version ancienne (décision du 16 novembre 2026).

## 6. Ce que ce contrôle ne couvre pas

1. **La fenêtre d'acceptation à la connexion est une invitation, pas un verrou.** Tant que l'association ne publie pas de nouvelle campagne, elle peut utiliser le reste de son espace sans avoir accepté la nouvelle version ; seul le serveur, à la publication, bloque. Une campagne déjà en ligne continue de collecter.
2. **Aucune information préalable des associations** par courrier électronique avant l'entrée en vigueur d'une nouvelle version n'est mise en place.
3. **Pas d'entrée en vigueur différée** : un texte est en vigueur dès la mise en production (section 3).
4. **Toute modification, même une coquille, crée une nouvelle version** et redemande l'acceptation aux associations.
5. **Aucune acceptation à la connexion pour les autres comptes** : un donateur disposant d'un espace personnel, le responsable de la conformité et les administrateurs ne se voient présenter aucun texte à leur connexion. Le donateur accepte les CGU donateurs et la politique de confidentialité à chaque don (section 4.1).

### Suivi des travaux restants

| Point | Tâche de suivi |
|---|---|
| 6.1 Blocage limité à la publication | *Aucune tâche identifiée* |
| 6.2 Information préalable par courrier électronique | *Aucune tâche identifiée* |

## 7. Procédure : modifier un texte

1. Modifier le texte dans le fichier de textes du site vitrine (`landing-page/messages/fr.json`) et mettre à jour sa date « Version en vigueur au … ».
2. Mettre en production la plateforme (serveur et application) et le site vitrine ensemble. Au démarrage, la plateforme enregistre la nouvelle version, qui entre aussitôt en vigueur.
3. Une modification du texte des associations leur est présentée à leur prochaine connexion ou à leur prochaine publication ; une modification du texte des donateurs s'applique à chaque nouveau don ; une modification de la politique de confidentialité s'applique aux deux.

**Première mise en production de la politique de confidentialité (24 novembre 2026).** La migration de base `V99__legal_document_privacy.sql` (retour arrière `U99__legal_document_privacy.sql`) doit être appliquée avant le démarrage du serveur : sans elle, la base refuse l'enregistrement de ce troisième texte. Le serveur et l'application doivent être mis en production ensemble : un formulaire de don de l'ancienne version, qui n'envoie pas la version de la politique de confidentialité affichée, voit le don refusé. À sa connexion suivante, chaque association se voit présenter la fenêtre d'acceptation (conditions du 24 novembre 2026 et première version de la politique de confidentialité).
