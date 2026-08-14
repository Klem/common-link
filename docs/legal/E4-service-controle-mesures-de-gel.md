# Service de contrôle des mesures de gel des avoirs

**Fiche de contrôle — dispositif de lutte contre le blanchiment de capitaux et le financement du terrorisme (LCB-FT)**

| | |
|---|---|
| **Entité** | CommonLink |
| **Nature du document** | Fiche descriptive d'un contrôle mis en place, destinée à la commission juridique |
| **Responsabilité concernée** | Gel des avoirs *(responsabilité n° 4 du dispositif interne, épique E4)* |
| **Référence de suivi interne** | Tâche Asana n° `1216210853624504` — « Service de screening sanctions/gel (DG Trésor + UE/ONU) » |
| **Lien de suivi** | https://app.asana.com/1/1213718564226627/project/1213723193546726/task/1216210853624504 |
| **Priorité assignée** | P0 (la plus élevée) |
| **Date de livraison** | 10 août 2026 |
| **État** | Moyen de contrôle livré et vérifié par contrôles automatisés. Non encore branché sur les parcours métier — les points d'accroche sur l'entrée en relation et sur les dons feront l'objet de fiches ultérieures. Non encore déployé — la mise en production de la plateforme est conditionnée à l'achèvement de l'ensemble du dispositif. |
| **Rédacteur** | Équipe technique CommonLink |

---

## 1. Avertissement sur la portée de ce document

Ce document décrit **un seul composant** du dispositif de gel des avoirs : le service de
vérification qui consulte le registre national et retourne les correspondances potentielles.

Ce composant constitue le **moyen de contrôle** — la capacité technique d'interroger le registre.
Il n'est pas encore relié aux parcours d'utilisation de la plateforme (entrée en relation d'une
association, traitement d'un don). Ces branchements feront l'objet de fiches distinctes, à mesure
de leur réalisation.

Le dispositif LCB-FT de CommonLink est organisé en six responsabilités. Cette fiche ne porte que
sur la responsabilité n° 4. Les cinq autres — désignation des responsables, classification des
risques, entrée en relation et vérification de l'association, surveillance des opérations atypiques
et déclaration, conservation des documents et rapport annuel — sont **planifiées ou en cours**.
Aucune conclusion sur la conformité globale du dispositif ne peut être tirée de la présente fiche.

## 2. L'obligation constatée

L'article L.562-2 du Code monétaire et financier impose aux organismes financiers et assimilés
de vérifier que leurs clients, leurs bénéficiaires et toute personne agissant en leur nom ne
font pas l'objet d'une mesure de gel des avoirs. Cette obligation est **de résultat** et doit
être exécutée **sans délai**.

CommonLink n'était, à la date de ce document, doté d'**aucun mécanisme automatisé** permettant
d'effectuer cette vérification. Le registre national des mesures de gel n'était pas consulté,
ni lors de l'ouverture d'une collecte, ni lors de la réception d'un don.

Ce constat a été établi lors de l'audit de conformité LCB-FT mené en août 2026. Aucun incident
n'a été signalé : la plateforme n'encaisse aucun fonds réel à ce jour.

## 3. Pourquoi ce contrôle n'existait pas

CommonLink est une plateforme de collecte philanthropique dont les fonctionnalités de traitement
des dons ont été développées en priorité sur les fonctionnalités de conformité réglementaire.
Le contrôle du gel des avoirs, bien que requis par la loi, n'avait pas encore été mis en place
faute d'un composant technique dédié.

Contrairement à d'autres obligations — comme la vérification de l'identité de l'association,
qui pouvait s'appuyer sur des registres publics existants — le contrôle du gel exige une
infrastructure spécifique : un registre de référence, une procédure d'ingestion régulière de ce
registre, et un algorithme de rapprochement tolérant les écarts de transcription. Ces éléments
ont dû être construits de toutes pièces.

## 4. Le contrôle mis en place

### 4.1 La source retenue : le registre national publié par la Direction générale du Trésor

Le service interroge exclusivement le **registre national des personnes et entités faisant
l'objet d'une mesure de gel**, publié par la Direction générale du Trésor (DG Trésor).

Ce choix repose sur trois raisons :

**Le registre national consolide déjà l'ensemble des mesures applicables en France.** Il intègre
les mesures prononcées au niveau national, les mesures de l'Union européenne transposées en droit
français, et les mesures décidées par le Conseil de sécurité des Nations unies. Ingérer
séparément les listes européenne et onusienne n'apporterait aucune information supplémentaire
par rapport à ce registre.

**Une source unique est plus sûre qu'une agrégation de sources multiples.** Chaque source
supplémentaire est un point de défaillance potentiel : erreur de parsing, format changeant,
indisponibilité temporaire. En centralisant le contrôle sur le registre national, le dispositif
réduit sa surface de risque opérationnel et simplifie la surveillance de la qualité des données.

**La DG Trésor est l'autorité compétente.** Le registre est publié par l'administration
française responsable de l'application des mesures de gel. Il constitue la référence juridique
opposable en France. Toute divergence entre ce registre et d'autres sources doit être traitée
comme une anomalie de ces dernières, non comme un enrichissement à intégrer automatiquement.

### 4.2 L'ingestion et la mise à jour du registre

Le registre est téléchargé périodiquement depuis le point de publication public de la DG Trésor.
Chaque ingestion est une **synchronisation complète** :

- Les entrées présentes dans la nouvelle publication sont créées ou mises à jour (un identifiant
  unique attribué par la DG Trésor garantit l'idempotence).
- Les entrées absentes de la nouvelle publication — ce qui signifie que la mesure a été levée —
  sont **supprimées** du registre interne.

Ce mécanisme garantit que le registre interne ne conserve pas indéfiniment des mesures éteintes,
ce qui constituerait une source de faux positifs.

### 4.3 La normalisation des noms : un point unique, aucune asymétrie

Les noms présents dans le registre sont soumis à une normalisation avant stockage. La même
normalisation est appliquée au nom interrogé lors d'une vérification. Cette symétrie est
garantie par l'utilisation d'une **seule et unique fonction de normalisation**, appelée des
deux côtés sans duplication.

La normalisation effectue, dans l'ordre :
1. décomposition des caractères composés (séparation des lettres et de leurs diacritiques) ;
2. suppression des signes diacritiques (accents, cédilles, trémas, etc.) ;
3. passage en majuscules ;
4. remplacement de tout séparateur non alphanumérique (tiret, apostrophe, ponctuation) par une
   espace ;
5. suppression des espaces multiples et des espaces en début et fin de chaîne.

Pour chaque entrée du registre, le service stocke plusieurs variantes normalisées :
- le nom dans l'ordre « nom de famille prénom » ;
- le nom dans l'ordre inversé « prénom nom de famille » ;
- chacun des alias dans les deux ordres.

Cette multiplication des variantes stockées permet de retrouver une correspondance quelle que
soit la convention d'ordre retenue lors de la saisie du nom à vérifier.

### 4.4 Le rapprochement approché et le choix du seuil

Un rapprochement strictement exact — exigeant que le nom interrogé soit orthographié à l'identique
du nom figurant au registre — **ne remplit pas l'obligation légale**. Les noms peuvent être
translittérés de différentes façons selon leur langue d'origine, orthographiés phonétiquement,
ou simplement saisis avec une faute de frappe. Écarter ces cas au motif d'une divergence
orthographique reviendrait à vider le contrôle de sa substance.

Le service utilise donc un **algorithme de similarité** qui mesure la proximité entre deux
chaînes de caractères après normalisation. Plus le score est proche de 1, plus les deux noms
sont proches. Un seuil de déclenchement détermine à partir de quel score une correspondance est
signalée pour revue humaine.

**L'arbitrage retenu entre faux positifs et faux négatifs** est le suivant :

Un **faux positif** (signaler une correspondance pour une personne qui n'est pas listée) entraîne
une revue humaine. Ce coût est acceptable : la revue humaine est précisément le mécanisme prévu
pour lever les doutes, et il vaut mieux examiner une correspondance infondée que d'en manquer
une réelle.

Un **faux négatif** (ne pas signaler une correspondance pour une personne effectivement listée)
constitue un **manquement** à l'obligation de résultat. Ce cas est inacceptable au regard de
l'article L.562-2 CMF.

Le seuil est donc fixé à une valeur **basse et conservatrice**. Il est réglable par
configuration, ce qui permettra, une fois le service branché sur les parcours et observé en
conditions réelles, d'affiner la valeur si le nombre de faux positifs s'avérait excessif. Tout
relèvement du seuil devra être accompagné d'une vérification que les cas de variantes
d'orthographe sont toujours correctement identifiés.

### 4.5 La date de naissance comme discriminant de revue

La méthode d'interrogation accepte, en paramètre optionnel, la date de naissance du sujet à vérifier.
Cette information n'est **pas utilisée pour filtrer ou exclure des correspondances** : si la date inscrite
au registre est incomplète ou comporte une erreur de transcription, filtrer sur cette base produirait
un faux négatif — exactement le cas le plus grave au regard de l'obligation légale.

La date de naissance est en revanche **retournée dans chaque correspondance signalée**, à côté de
celle figurant au registre. Cette juxtaposition permet au relecteur humain de discriminer rapidement
entre une correspondance portant sur une personne au profil identique (même année, même mois) et une
correspondance homonyme peu probable (décennies d'écart). Le discriminant appartient ainsi à la
décision humaine, non à l'algorithme.

### 4.6 Ce que ce service fait et ce qu'il ne fait pas

Ce service **constate** des correspondances potentielles. Il **ne décide pas** et **ne bloque
pas** de lui-même. La décision sur la conduite à tenir face à une correspondance — vérification
approfondie, demande de pièces complémentaires, refus de la transaction — appartient aux procédures
métier, qui seront documentées dans les fiches des contrôles à l'entrée en relation et sur les dons.

## 5. Les éléments de preuve

Le service est accompagné d'une suite de **contrôles automatisés** réexécutés à chaque
modification du logiciel. Ces contrôles couvrent :

**Les variantes d'orthographe d'un même nom listé**, qui doivent toutes remonter une
correspondance :

| Variante testée | Nature de l'écart | Résultat attendu |
|---|---|---|
| Nom exact, casse identique | Aucun | Correspondance — vérifié |
| Nom en minuscules | Différence de casse | Correspondance — vérifié |
| Nom avec espaces multiples | Espaces superflus | Correspondance — vérifié |
| Nom avec accents ajoutés | Diacritiques | Correspondance — vérifié |
| Nom avec tiret en séparateur | Ponctuation alternative | Correspondance — vérifié |
| Nom avec casse mixte | Variation de casse | Correspondance — vérifié |
| Prénom puis nom (ordre inversé) | Ordre des composantes | Correspondance — vérifié |
| Alias connu de la personne | Identité alternative | Correspondance — vérifié |
| Alias en ordre inversé | Alias + ordre | Correspondance — vérifié |
| Nom sans la particule (ex. « de ») | Particule absente | Correspondance — vérifié |

**Les noms clairement distincts**, qui ne doivent pas déclencher de correspondance :

| Nom testé | Raison de l'absence de correspondance | Résultat attendu |
|---|---|---|
| Prénom et nom communs sans lien | Noms structurellement différents | Aucune correspondance — vérifié |
| Autre combinaison de noms courants | Noms structurellement différents | Aucune correspondance — vérifié |
| Nom d'entité morale versus personnes physiques | Nature différente, filtre appliqué | Aucune correspondance sur les PP — vérifié |

Ces deux séries de contrôles — l'une vérifiant que des variantes légitimes sont bien détectées,
l'autre que des noms distincts ne sont pas rapprochés — démontrent conjointement que le seuil
n'est ni trop strict (ce qui produirait des faux négatifs) ni trop laxiste (ce qui produirait
une liste de correspondances inexploitable).

D'autres contrôles portent sur le comportement de l'ingestion :
- une ingestion d'un extrait du registre de référence peuple la table interne avec le nombre
  d'entrées attendu ;
- une ré-ingestion du même extrait ne crée pas de doublons ;
- une ingestion d'un registre dans lequel une mesure a été levée supprime l'entrée correspondante.

L'ensemble des contrôles automatisés de la plateforme a été exécuté après la livraison. Aucune
régression n'a été constatée sur les fonctionnements existants.

## 6. Ce que ce contrôle ne couvre pas

Cette précision est apportée pour éviter toute lecture extensive de la présente fiche.

**Ce que ce composant ne couvre pas à ce stade :**

- Le **branchement sur les parcours métier** — ce service ne peut constater une correspondance
  que si on l'interroge. *Traité depuis par les fiches E4 — Contrôle de gel des avoirs à l'entrée
  en relation d'affaires et E4 — Contrôle de gel des avoirs à la création d'un don.*

- La **fréquence d'ingestion** — le service est capable d'ingérer le registre à la demande.
  *Le calendrier et le mécanisme de déclenchement périodique sont traités depuis par la fiche
  E4 — Mise à jour automatique du registre national des mesures de gel des avoirs.*

- La **vérification sur les donateurs** — à ce stade, le contrôle porte sur l'association et
  ses représentants. *Traitée depuis par la fiche E4 — Contrôle de gel des avoirs à la création
  d'un don.*

- La **trace d'audit par vérification** — les correspondances trouvées lors d'une interrogation
  ne sont pas encore enregistrées dans le journal d'audit LCB-FT. *Traitée depuis par la fiche
  E4 — Journal des contrôles de gel des avoirs.*

- Le **re-criblage périodique automatique** des sujets déjà contrôlés — le service constate une
  correspondance au moment où il est interrogé ; **aucune reprise programmée** ne détecte qu'une
  mesure de gel a été prononcée après un contrôle favorable.

- L'**affinage du seuil de déclenchement** en conditions réelles, annoncé au point 4.4 — la
  plateforme n'étant pas déployée, aucune observation n'a encore pu être conduite. Le seuil
  effectivement retenu et son effet sur le rappel sont exposés par la fiche *E4 — Preuve de
  correspondance au registre de gel* ; toute modification est une décision juridique.

- La **surveillance des opérations atypiques** et la déclaration de soupçon.

- La **conservation des pièces justificatives** et le rapport annuel d'activité.

Chacun de ces points fait l'objet de travaux planifiés et ordonnancés, avec des dépendances
identifiées entre eux.

**Suivi des travaux restants.** Les éléments ci-dessus qui restent à réaliser sont suivis dans le
référentiel interne de gestion de projet aux références suivantes.

| Travail restant | Lien de suivi |
|---|---|
| Surveillance des opérations atypiques et déclaration de soupçon *(épique E5)* | [1216210853624511](https://app.asana.com/1/1213718564226627/project/1213723193546726/task/1216210853624511) · [1216210853624512](https://app.asana.com/1/1213718564226627/project/1213723193546726/task/1216210853624512) · [1216210853624513](https://app.asana.com/1/1213718564226627/project/1213723193546726/task/1216210853624513) · [1216210853624514](https://app.asana.com/1/1213718564226627/project/1213723193546726/task/1216210853624514) |
| Conservation des pièces justificatives et rapport annuel *(épique E6)* | [1216210853624518](https://app.asana.com/1/1213718564226627/project/1213723193546726/task/1216210853624518) · [1216210853624517](https://app.asana.com/1/1213718564226627/project/1213723193546726/task/1216210853624517) · [1216210853624520](https://app.asana.com/1/1213718564226627/project/1213723193546726/task/1216210853624520) |
| Re-criblage périodique automatique des sujets déjà contrôlés | *Aucune tâche de suivi identifiée dans le référentiel de projet* |
| Affinage du seuil de déclenchement en conditions réelles — décision juridique, à instruire après la mise en production | *Aucune tâche de suivi identifiée dans le référentiel de projet* |

## 7. Situation de ce contrôle dans le dispositif d'ensemble

| Responsabilité du dispositif | État au 10 août 2026 |
|---|---|
| 1 — Désignation des responsables et cloisonnement des accès | Planifié |
| 2 — Classification des risques et procédures écrites | Planifié — la rédaction des documents conditionne les développements correspondants |
| 3 — Entrée en relation et vérification de l'association | Partiellement réalisé — le verrou de validation avant collecte est livré ; d'autres composantes sont planifiées |
| **4 — Gel des avoirs** | **Moyen de contrôle livré (ce document). Non encore branché sur les parcours.** |
| 5 — Surveillance des opérations atypiques et déclaration | Planifié |
| 6 — Conservation des documents et rapport annuel | Planifié |

Il est à noter qu'une partie des travaux planifiés est **conditionnée à la remise de documents
non techniques** : la désignation signée des responsables, la classification des risques validée,
et les procédures écrites. Les développements correspondants ne peuvent être engagés avant, sous
peine de transcrire dans le logiciel des règles qui n'auraient pas été arrêtées.

## 8. Éléments de traçabilité

La livraison est enregistrée dans l'historique des modifications du logiciel, sur la ligne de
développement dédiée au dispositif LCB-FT (`app-legal-lcb-ft`). Elle comprend :

- la migration de base de données qui crée la table du registre interne ;
- le service d'ingestion du registre DG Trésor ;
- la fonction de normalisation des noms (point unique, sans duplication) ;
- le service de rapprochement approché ;
- les contrôles automatisés décrits à la section 5 ;
- la configuration du seuil de déclenchement et l'interdiction, vérifiée automatiquement,
  d'activer le mode données de test en production.

---

*Document établi le 10 août 2026. Une fiche de même nature sera produite pour chaque contrôle du
dispositif LCB-FT au fur et à mesure de sa réalisation.*
