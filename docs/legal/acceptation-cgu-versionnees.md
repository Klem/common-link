# Acceptation des conditions générales — textes définitifs, versions et preuve

**Fiche de contrôle — quel texte est accepté, par qui, quand, et comment la preuve en est conservée**

| | |
|---|---|
| **Entité** | CommonLink |
| **Nature du document** | Fiche descriptive du mécanisme d'acceptation des conditions générales, destinée à la commission juridique |
| **Responsabilité concernée** | Preuve de l'acceptation expresse des conditions générales (notice ACPR applicable à la collecte ouverte au public ; art. 1740 A du code général des impôts pour la garantie que l'association donne sur les reçus fiscaux). **Hors dispositif LCB-FT** — cette fiche ne relève d'aucune des six responsabilités du dispositif interne. |
| **Référence de suivi interne** | *Aucune tâche de suivi identifiée dans le référentiel de projet* |
| **Origine** | Mise en production prochaine : les fenêtres d'acceptation n'affichaient qu'un texte provisoire (« PLACEHOLDER ») enregistré le 26 août 2026 ; les textes définitifs étaient publiés sur le site vitrine mais absents de la plateforme |
| **Date de mise en place** | 16 novembre 2026 |
| **État** | Réalisé et vérifié par contrôles automatisés (tests backend et frontend, migration rejouée sur une base PostgreSQL vierge). Non commité, non déployé. |
| **Rédacteur** | Équipe technique CommonLink |

---

## 1. Avertissement sur la portée de ce document

Ce document décrit comment la plateforme présente les conditions générales et conserve la preuve de leur acceptation. Il ne porte sur aucun contrôle LCB-FT et aucune conclusion sur le dispositif LCB-FT ne peut en être tirée.

**Il ne se prononce pas sur le contenu juridique des textes**, qui relève de la commission juridique. Il garantit seulement que le texte affiché et accepté sur la plateforme est, mot pour mot, celui publié sur le site vitrine à la date indiquée.

**La vérification n'est pas une inspection visuelle des pages déployées** : elle porte sur ce que le code produit (contenu enregistré, texte rendu par les composants), au moyen de tests automatisés.

## 2. Les deux textes

Les textes définitifs sont ceux du site vitrine :

| Texte du site vitrine | Nom dans la plateforme | Date de version actuelle |
|---|---|---|
| Conditions générales d'utilisation applicables aux donateurs (`/conditions-generales-utilisation`) | « CGU » | 17 novembre 2026 |
| Conditions générales d'utilisation applicables aux organismes bénéficiaires (`/conditions-generales-utilisation-associations`), avec leurs annexes | « CGV » | 23 novembre 2026 (article 17 : conservation des doubles des reçus fiscaux, voir `E5-recu-fiscal-mentions.md`, section 11) |

Le nom « CGV » est historique : il n'existe pas de conditions générales de vente distinctes. Il désigne désormais les conditions applicables aux associations.

**Une seule source.** Le texte n'est saisi qu'à un endroit : le fichier de textes du site vitrine. La plateforme le reprend tel quel à chaque mise en production. Les liens internes des textes (politique de confidentialité, politique cookies, réclamations, contrat type) pointent vers les pages correspondantes du site vitrine.

## 3. Versions et historique

**Chaque modification du texte crée une nouvelle version**, enregistrée en base au démarrage de la plateforme, si et seulement si le texte a changé. Une version déjà enregistrée n'est jamais modifiée ni supprimée : l'historique complet reste en base.

Le numéro de version combine la date « Version en vigueur au … » du texte et une empreinte numérique de son contenu (par exemple `2026-09-09-a1b2c3d4e5f6`). Deux corrections publiées le même jour donnent donc deux versions distinctes. Si deux textes différents produisaient le même numéro (cas théorique), la plateforme refuse de démarrer plutôt que de laisser un numéro désigner deux textes.

**Une nouvelle version est en vigueur dès sa mise en production.** Il n'est pas possible de programmer une date d'entrée en vigueur ultérieure. Revenir à une version antérieure du logiciel ne remet pas en vigueur l'ancien texte : la version la plus récemment enregistrée reste en vigueur. De même, remettre mot pour mot un texte déjà publié (par exemple annuler une correction) ne le remet pas en vigueur : il faut lui donner une nouvelle date « Version en vigueur au … ». La plateforme le signale dans ses journaux techniques au démarrage.

Les deux textes provisoires du 26 août 2026 restent dans l'historique. En production, aucune acceptation n'a été enregistrée sur ces textes (constat du 16 novembre 2026).

## 4. Qui accepte quoi, et quand

### 4.1 Donateur

À **chaque don**, le donateur coche une case d'acceptation des **CGU donateurs**. Le texte s'ouvre dans une fenêtre depuis le formulaire. Depuis le 16 novembre 2026, le donateur n'accepte plus que ce texte : la case relative aux « CGV » a été retirée, ce texte ne le concernant pas.

### 4.2 Association

L'association accepte **les conditions qui lui sont destinées** (texte nommé « CGV », voir section 2) :

- **à la première publication d'une campagne** suivant chaque nouvelle version (contrôle vérifié par le serveur : sans acceptation, la publication est refusée et le refus est journalisé, voir fiche E6) ;
- **à sa connexion** : lorsqu'une nouvelle version a été publiée depuis sa dernière acceptation, une fenêtre qu'elle ne peut pas fermer lui présente le texte en vigueur et lui demande de l'accepter.

Seule la version en vigueur est présentée. Si le texte a changé plusieurs fois entre deux connexions, l'association n'accepte qu'une fois, la version la plus récente.

Jusqu'au 16 novembre 2026, l'association acceptait le texte nommé « CGU » ; ce texte étant devenu celui des donateurs, elle accepte depuis cette date celui des associations (décision du 16 novembre 2026). Les acceptations déjà enregistrées gardent le nom du texte accepté : en production, il n'y en avait aucune (section 3).

## 5. La preuve conservée

Chaque acceptation enregistre : qui (donateur ou association), le texte, **la version exacte affichée à l'écran**, la date et l'heure, et le nom et l'adresse électronique du signataire tels qu'ils étaient au moment de l'acceptation (ils ne sont pas relus ensuite depuis un profil modifiable). Pour un donateur, le don concerné ; pour une association, la campagne dont la publication a déclenché l'acceptation (aucune pour une acceptation à la connexion).

**Version lue = version acceptée.** Si une nouvelle version est publiée pendant qu'une personne a le formulaire ouvert, son acceptation est refusée : le nouveau texte lui est présenté et la case est décochée. Une preuve ne peut donc jamais désigner un texte que la personne n'a pas pu lire.

Le responsable de la conformité consulte ces preuves depuis son espace (historique d'un compte, acceptations des donateurs d'une campagne). Le texte exact de chaque version est conservé en base ; il n'existe pas d'écran pour relire une version ancienne (décision du 16 novembre 2026).

## 6. Ce que ce contrôle ne couvre pas

1. **La fenêtre d'acceptation à la connexion est une invitation, pas un verrou.** Tant que l'association ne publie pas de nouvelle campagne, elle peut utiliser le reste de son espace sans avoir accepté la nouvelle version ; seul le serveur, à la publication, bloque. Une campagne déjà en ligne continue de collecter.
2. **Aucune information préalable des associations** par courrier électronique avant l'entrée en vigueur d'une nouvelle version n'est mise en place.
3. **Pas d'entrée en vigueur différée** : un texte est en vigueur dès la mise en production (section 3).
4. **Toute modification, même une coquille, crée une nouvelle version** et redemande l'acceptation aux associations.

### Suivi des travaux restants

| Point | Tâche de suivi |
|---|---|
| 6.1 Blocage limité à la publication | *Aucune tâche identifiée* |
| 6.2 Information préalable par courrier électronique | *Aucune tâche identifiée* |

## 7. Procédure : modifier un texte

1. Modifier le texte dans le fichier de textes du site vitrine (`landing-page/messages/fr.json`) et mettre à jour sa date « Version en vigueur au … ».
2. Mettre en production la plateforme (serveur et application) et le site vitrine ensemble. Au démarrage, la plateforme enregistre la nouvelle version, qui entre aussitôt en vigueur.
3. Une modification du texte des associations leur est présentée à leur prochaine connexion ou à leur prochaine publication ; une modification du texte des donateurs s'applique à chaque nouveau don.
