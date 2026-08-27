# Mentions obligatoires de la notice de collecte ouverte au public

**Fiche de contrôle — cinq éléments requis sur la page publique d'une campagne**

| | |
|---|---|
| **Entité** | CommonLink |
| **Nature du document** | Fiche descriptive d'une correction apportée à la page publique d'une campagne, destinée à la commission juridique |
| **Responsabilité concernée** | Notice ACPR applicable à toute collecte ouverte au public : le projet doit être décrit en termes d'objet, de montant cible de financement, de calendrier, de description chiffrée de l'utilisation prévue des fonds levés, et de résultat attendu. **Hors dispositif LCB-FT** — cette fiche ne relève d'aucune des six responsabilités du dispositif interne. |
| **Référence de suivi interne** | *Aucune tâche de suivi identifiée dans le référentiel de projet* |
| **Origine** | Vérification demandée le 26 août 2026 : la saisie des cinq éléments à la création de campagne avait été confirmée conforme le 4 août 2026 (objectif en euros, dates de début/fin, budget prévisionnel, objectifs d'impact) ; restait à vérifier qu'ils apparaissent sur la page **publique** lue par le donateur, et non seulement dans l'interface d'édition de l'association |
| **Date de correction** | 26 août 2026 |
| **État** | Corrigé et vérifié par contrôles automatisés (tests d'intégration backend, tests unitaires frontend). Non commité, non déployé. |
| **Rédacteur** | Équipe technique CommonLink |

---

## 1. Avertissement sur la portée de ce document

Ce document décrit une correction apportée à la page publique de campagne (`/lp/[widgetToken]`, ci-après « la landing page »). Il ne porte sur aucun contrôle LCB-FT. Aucune conclusion sur la conformité du dispositif LCB-FT ne peut en être tirée ; celle-ci fait l'objet des fiches E1 à E4 et du document de synthèse *LCB-FT — vue d'ensemble*.

**Il ne couvre pas le widget de don embarqué** (`/embed/donate/[widgetToken]`), une page distincte, plus compacte, destinée à être intégrée directement sur le site de l'association. Ce point est développé en section 6.

**La vérification n'est pas une inspection visuelle de la page déployée.** Comme pour la fiche *Rédaction du reçu fiscal* (E5-recu-fiscal-mentions.md), les contrôles portent sur ce que le code produit réellement — le contenu du DTO exposé par l'API et le texte rendu dans le DOM par les composants — et non sur une capture d'écran ou une navigation manuelle.

## 2. Ce qui a été constaté

La saisie des cinq éléments à la création de campagne était déjà conforme (vérifié le 4 août 2026 : objectif en euros, dates de début/fin, budget prévisionnel charges et produits, objectifs d'impact ; le budget est obligatoire pour publier, confirmé par Julian).

L'examen de la landing page (`app/src/app/[locale]/lp/[widgetToken]/`) a montré que deux des cinq éléments n'atteignaient jamais le donateur :

| Élément ACPR | Constat |
|---|---|
| Objet | Visible (`LandingHero.tsx` : nom et description de la campagne) |
| Résultat attendu | Visible, mais dans une section que l'association pouvait masquer (voir ci-dessous) |
| Description chiffrée de l'utilisation des fonds | Visible (budget par poste en pourcentage), même réserve |
| **Montant cible** | **Absent.** Le champ `goal` était bien renvoyé par l'API (`PublicLandingDto.goal`) mais n'était lu par aucun composant de la landing page |
| **Calendrier** | **Absent.** `Campaign.startDate` / `Campaign.endDate` existent en base mais n'étaient même pas exposés dans `PublicLandingDto` |

Un second constat, fait en cours de correction : les sections portant l'objet, le résultat attendu et la description chiffrée du budget (`ProjectSection.tsx`, `TransparencySection.tsx`) n'étaient rendues que si l'association avait laissé actives les préférences `landingShowProject` / `landingShowTransparency` dans ses réglages de landing page (`LandingTab.tsx`). Une association pouvait donc désactiver l'affichage de trois des cinq éléments obligatoires, y compris ceux qui l'étaient déjà avant cette correction.

Un troisième constat : `Campaign.startDate` / `Campaign.endDate` n'étaient pas requis pour publier une campagne — seul le budget prévisionnel équilibré et le résultat attendu l'étaient (voir *LCB-FT — vue d'ensemble*, §5.3, « bloquant depuis le 17 août 2026 »). Le formulaire de publication (`PrePublishModal.tsx`) affichait pourtant déjà un blocage `required.dates` côté interface — non répliqué côté serveur (`CampaignService.preparePublish`), en violation de la règle du projet selon laquelle toute validation frontend doit être répliquée côté backend (« every click is replayable »).

## 3. Décisions prises

Deux points ont été soumis à l'association technique (Klem) le 26 août 2026, faute d'être tranchables par la seule lecture du code :

1. **Les trois sections portant du contenu légalement obligatoire ne doivent plus pouvoir être masquées.** Décision : les rendre inconditionnelles sur la landing page. Les préférences `landingShowProject` / `landingShowTransparency` restent stockées et modifiables via `PATCH /api/associations/me/landing` mais ne sont plus honorées côté page publique. Les deux cases à cocher correspondantes ont été retirées de l'interface de réglages pour ne pas laisser croire à l'association qu'elle contrôle encore l'affichage de ces sections.

   *Mise à jour du 26 août 2026, plus tard le même jour :* `landingShowTrust` (bandeau de confiance, sans contenu ACPR-obligatoire) a reçu le même traitement, par cohérence plutôt que par contrainte légale — un toggle qui n'agit plus que sur un tiers du contenu de la carte « Sections » n'a plus de sens. `PublicWidgetService.getLanding` renvoie désormais `showTrust = true` en toute circonstance, la carte « Sections de la page » a été retirée de `LandingTab.tsx`, et les clés `settings.landing.sections.*` ont été supprimées de `fr.json`/`en.json`.
2. **Le calendrier doit être obligatoire pour publier, au même titre que le budget.** Décision : appliquée. `CampaignService.preparePublish` refuse désormais la publication si `startDate` ou `endDate` est absent, avec le même message d'erreur mirroré côté frontend (`PrePublishModal.tsx`, déjà en place).
3. **Puisque l'utilisateur ne peut plus piloter ces trois préférences nulle part, elles n'ont plus lieu d'exister en base.** Décision prise le 26 août 2026, encore plus tard le même jour, à la demande explicite de l'association technique : suppression complète (colonnes, entité, DTOs, service) plutôt que conservation d'un champ mort « pour ne pas rompre le contrat d'API » — ce contrat n'a jamais été consommé côté frontend (voir 4.2). Détail en 4.5.

## 4. Ce qui a été mis en place

### 4.1 Montant cible et calendrier exposés et affichés

- `PublicLandingDto` (backend) gagne `startDate: LocalDate?` et `endDate: LocalDate?`, peuplés depuis l'entité `Campaign` dans `PublicWidgetService.getLanding`.
- `ProjectSection.tsx` (frontend) affiche désormais l'objectif de collecte formaté (`Intl.NumberFormat`) et, lorsqu'au moins une date est renseignée, la période de collecte (`Intl.DateTimeFormat`, formats « Du X au Y », « Depuis le X », « Jusqu'au Y » selon ce qui est renseigné).
- Le calendrier n'est affiché que si au moins une date existe : une campagne déjà LIVE avant cette correction, sans dates, n'affiche pas de ligne calendrier plutôt qu'une ligne vide ou fausse. Voir section 6 sur la portée de cette omission — au 26 août 2026, cette situation ne concerne aucune campagne (section 5.2).

### 4.2 Sections légalement obligatoires rendues inconditionnelles

`page.tsx` ne conditionne plus le rendu de `ProjectSection`, `TransparencySection` ni `TrustSection` aux préférences de l'association — les trois sections sont désormais toujours rendues, inconditionnellement. La carte « Sections de la page », devenue vide de tout contrôle effectif, a été retirée de `LandingTab.tsx`. *(Les champs `showProject`/`showTransparency`/`showTrust` eux-mêmes n'existent plus dans `PublicLandingDto` depuis la suppression complète décrite en 4.5 — cette section documentait un état intermédiaire, où ils étaient encore présents mais figés à `true`.)*

### 4.3 Calendrier obligatoire à la publication

`CampaignService.preparePublish` refuse la transition DRAFT → LIVE si `startDate` ou `endDate` est nul, avec le message « Campaign start and end dates must be set before publishing ». Cette règle est mirrorée par le blocage `required.dates`, déjà présent côté interface (`PrePublishModal.tsx`) mais jusqu'ici non appliqué côté serveur.

### 4.4 Poste de budget à 0% retiré de l'affichage

Signalé pendant cette même correction : `buildBudgetProjection` arrondit chaque pourcentage à l'entier le plus proche (HALF_UP), si bien qu'un poste de faible montant peut s'afficher à « 0 % » — une barre vide, sans information utile pour le donateur. `TransparencySection.tsx` filtre désormais ces postes avant affichage (`visibleBudget = budget.filter(item => item.percentage > 0)`). Le poste reste inclus dans le calcul du total et des pourcentages des autres postes ; seul son affichage individuel disparaît. N'affecte pas les cinq éléments ACPR : la description chiffrée reste portée par les postes affichés, dont les pourcentages somment à un total inférieur à 100 % dans ce cas de figure plutôt que d'inclure une ligne à zéro.

### 4.5 Suppression complète de `landingShowProject` / `landingShowTransparency` / `landingShowTrust`

Les trois colonnes sont physiquement supprimées de `association_profiles` par la migration Flyway `V71__drop_landing_section_toggles.sql` (rollback `U71__drop_landing_section_toggles.sql`, sans récupération de données — cohérent avec la convention du repo pour les migrations de suppression de colonne, ex. `V32`/`U32`). Suppression en cascade sur les couches applicatives :

- Entité `AssociationProfile.kt` : les 3 propriétés `landingShow*` retirées.
- `AssociationProfileDto` (profil privé) et `UpdateLandingConfigRequest` (payload `PATCH`) : les 3 champs retirés de chaque côté.
- `PublicLandingDto` : `showProject`/`showTransparency`/`showTrust` retirés — plus aucune trace dans le contrat de la page publique.
- `AssociationLandingService.updateLandingConfig` : ne lit plus ces champs sur la requête.
- `PublicWidgetService.getLanding` : ne les assigne plus dans le DTO renvoyé.
- Frontend : types miroir `AssociationProfileDto`/`UpdateLandingConfigRequest` (`types/association.ts`) et `PublicLandingDto` (`lib/api/public.ts`) alignés — ces trois champs n'apparaissaient déjà plus nulle part dans l'UI.

`association_logos`, `landing_theme` et `landing_logo` (même migration d'origine `V48`) ne sont pas concernés : fonctionnalités actives, hors périmètre.

## 5. Les éléments de preuve

### 5.1 Contrôles automatisés

| Ce qui est vérifié | Où |
|---|---|
| `PublicLandingDto.goal` / `.startDate` / `.endDate` sont bien peuplés depuis l'entité `Campaign` | `PublicWidgetServiceIntegrationTest.getLanding exposes goal and campaign calendar for the ACPR public-collection notice` |
| Le montant cible formaté apparaît dans le DOM rendu | `ProjectSection.test.tsx` (« renders the formatted fundraising goal ») |
| Le calendrier complet, partiel (une seule date), ou absent (aucune date) est rendu correctement | `ProjectSection.test.tsx` (3 tests) |
| Le résultat attendu (objectifs d'impact) est rendu | `ProjectSection.test.tsx` |
| La publication est refusée si `startDate` ou `endDate` est absent | `CampaignServiceTest.publish - missing calendrier returns 422` |
| Les gates existants (budget, résultat attendu) continuent de fonctionner une fois le calendrier ajouté à `preparePublish` | `CampaignServiceTest` (54 tests, dont les gates de publication mis à jour pour fournir des dates) |
| Le réglage de landing page n'expose plus aucune case de section (carte « Sections de la page » retirée) | `LandingTab.test.tsx` (« no longer offers any section toggle ») |
| Un poste de budget à 0 % n'est pas affiché, seul ou parmi d'autres postes | `TransparencySection.test.tsx` (2 tests) |
| Le schéma après `V71` (colonnes supprimées) reste cohérent avec l'entité JPA (`ddl-auto=validate`) — migrations `V1`…`V71` rejouées sur une base vierge | `FlywayMigrationTest` |
| Aucune référence résiduelle aux 3 champs côté backend Kotlin (entité, DTOs, services, tests) — le compilateur l'aurait bloqué | `compileKotlin` / `compileTestKotlin` |

Suites exécutées intégralement lors de la suppression complète (colonnes + entité + DTOs + service) : `CampaignServiceTest` (54/54), `PublicWidgetServiceIntegrationTest` + `PublicWidgetControllerTest` (57/57 — le test de régression sur les 3 préférences, ajouté lors de la correction précédente, est devenu sans objet et a été retiré, les champs qu'il testait n'existant plus), `AssociationLandingServiceTest` (20/20), `AssociationControllerTest` (30/30), suite backend complète (1102 tests, 4 ignorés, 1 échec — `ProdConfigSecurityTest`, préexistant, sans rapport avec cette correction), `tsc --noEmit` (12 erreurs préexistantes dans des fichiers non touchés, sans rapport), suite frontend complète (577/577).

### 5.2 État de la base de production au 26 août 2026

Interrogation en lecture seule de la base de production, au moment de la correction :

```sql
SELECT count(*) FILTER (WHERE start_date IS NULL OR end_date IS NULL) AS live_sans_dates,
       count(*) AS live_total
FROM campaigns WHERE status = 'LIVE';
-- live_sans_dates = 0, live_total = 0

SELECT count(*) FROM association_profiles
WHERE landing_show_project = false OR landing_show_transparency = false;
-- count = 0
```

**Aucune campagne n'est en statut LIVE en production à ce jour**, et aucune association n'avait désactivé les sections concernées. La correction ne nécessite donc aucun rattrapage sur des campagnes déjà publiées : elle s'applique avant toute exposition réelle d'un donateur.

*Note : cette requête n'est plus rejouable telle quelle après la suppression des colonnes `landing_show_project`/`landing_show_transparency` (section 4.5, migration `V71`) — elle est conservée ici comme preuve historique de l'état constaté au moment de la correction initiale, pas comme requête de contrôle réutilisable.*

## 6. Ce que cette correction ne couvre pas

**Le widget de don embarqué (`/embed/donate/[widgetToken]`) n'affiche aucun des cinq éléments dans leur ensemble.** C'est une page distincte de la landing page, plus compacte, destinée à être intégrée directement sur le site de l'association (`EmbedDonateClient.tsx`). Elle affiche l'objet (nom et description de la campagne) et une barre de progression donnant le montant cible et le montant collecté, mais ni calendrier, ni répartition budgétaire, ni résultat attendu. Un donateur qui donne exclusivement via ce widget, sans jamais visiter la landing page, n'est donc pas exposé aux cinq éléments requis par la notice. Ce point n'a pas été tranché : il appelle un arbitrage de la commission juridique (Julian) pour déterminer si le widget embarqué constitue lui aussi une « collecte ouverte au public » au sens de la notice, ou si la landing page en est le point d'exposition légal unique.

**Le calendrier n'est pas re-vérifié au moment d'une reprise (PAUSED → LIVE).** `preparePublish` — et donc le nouveau contrôle du calendrier — ne s'exécute que sur la transition DRAFT → LIVE (`CampaignService.kt`, `previousStatus == CampaignStatus.DRAFT`). Une campagne mise en pause puis relancée ne repasse pas par ce contrôle. Ce n'est pas une régression introduite par cette correction : le contrôle du budget et celui du résultat attendu, déjà bloquants depuis le 17 août 2026, ont exactement la même portée. Le calendrier hérite du même point ouvert.

### Suivi des travaux restants

| Point ouvert | Tâche de suivi |
|---|---|
| Statut du widget embarqué au regard de la notice de collecte publique | *Aucune tâche identifiée — décision à demander à Julian* |
| Calendrier non re-vérifié sur reprise PAUSED → LIVE | *Aucune tâche identifiée — partagé avec les gates budget/résultat attendu, non traité pour eux non plus* |

## 7. Éléments de traçabilité

| | |
|---|---|
| **DTO public** | `api/src/main/kotlin/org/commonlink/dto/PublicLandingDto.kt` |
| **Projection landing page** | `api/src/main/kotlin/org/commonlink/service/PublicWidgetService.kt` (`getLanding`) |
| **Gate de publication** | `api/src/main/kotlin/org/commonlink/service/CampaignService.kt` (`preparePublish`) |
| **Landing page publique** | `app/src/app/[locale]/lp/[widgetToken]/page.tsx`, `ProjectSection.tsx`, `TransparencySection.tsx`, `landing.css` |
| **Réglages association** | `app/src/components/settings/LandingTab.tsx` |
| **Widget embarqué (hors périmètre, section 6)** | `app/src/app/[locale]/embed/donate/[widgetToken]/EmbedDonateClient.tsx` |
| **Contrôles automatisés** | `PublicWidgetServiceIntegrationTest.kt`, `PublicWidgetControllerTest.kt`, `CampaignServiceTest.kt`, `AssociationLandingServiceTest.kt`, `AssociationControllerTest.kt`, `FlywayMigrationTest.kt`, `ProjectSection.test.tsx`, `TransparencySection.test.tsx`, `LandingTab.test.tsx` |
| **Traductions** | `app/messages/fr.json`, `app/messages/en.json` (clés `landing.project.*`) |
| **Suppression complète (4.5)** | Migration `api/src/main/resources/db/migration/V71__drop_landing_section_toggles.sql` (+ rollback `U71`) ; `entity/AssociationProfile.kt` ; `dto/AssociationProfileDto.kt`, `dto/UpdateLandingConfigRequest.kt` ; `service/AssociationLandingService.kt` ; `types/association.ts`, `lib/api/public.ts` ; `api/.http/AssociationController.http` (scénario 11 corrigé, envoyait un champ supprimé) |

---

*Document établi le 26 août 2026. Il décrit l'état de la correction arrêté à cette date et sera mis à jour si le périmètre de la landing page ou du widget embarqué évolue.*
