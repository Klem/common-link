# CommonLink — Glossary

> **Audience:** All team members (Tech, Sales, Marketing, Product, Ops)
> **Version:** 1.0 — March 2026
> **Format:** Each entry includes a definition and one or more tags indicating its domain.

**Tags legend:**
- `technical` — Engineering & infrastructure concepts
- `functional` — Product features & user-facing behavior
- `blockchain` — Web3, crypto & on-chain concepts
- `business` — Commercial, legal & strategic concepts
- `security` — Authentication, data protection & compliance

---

## A

### Access Token
A short-lived credential (valid 15 minutes) that authenticates a user's requests to the backend. It is a JWT (JSON Web Token) containing the user's ID, email, and role. Issued after login and automatically refreshed in the background.
`technical` `security`

### Account Merge
The mechanism that ensures one email address always corresponds to a single account. When a user who registered via email later signs in with Google (or vice versa), the two authentication methods are linked to the same account — no duplicates are ever created.
`functional` `security`

### Anonymous Donation
A toggle available to donors at the time of donation. When enabled, the donor's identity is hidden from the association and the public leaderboard, while the on-chain certification remains intact.
`functional`

### API (Application Programming Interface)
The "gateway" through which the frontend and backend communicate. The CommonLink API is a REST service that handles authentication, campaigns, donations, and profiles. All endpoints are documented via Swagger UI at `/api/docs`.
`technical`

### Annuaire des Entreprises (French Business Directory)
The official French government API (`api.annuaire-entreprises.data.gouv.fr`) used during association signup to verify SIREN numbers and retrieve organization data (name, city, legal status). Only entities with legal category 9220 (loi 1901 associations) are eligible.
`business` `functional`

### Apple Pay
One of the supported payment methods for donations. Allows one-tap payment using Touch ID or Face ID.
`functional`

### Association
One of the two user types on CommonLink. An association is a French non-profit organization (loi 1901) that creates fundraising campaigns, manages budgets, tracks milestones, and reports impact to donors. Identified by its SIREN number.
`business` `functional`

### Association Status
Compliance status of an association (`ACTIVE`, `ALERT`, `SUSPENDED`), separate from `VerificationStatus` (KYC). `ACTIVE` is normal. `ALERT` means a campaign report is open and awaiting compliance review — internal only, does not gate donations or public visibility. `SUSPENDED` means a report was confirmed founded; it blocks every campaign of the association from accepting donations until a compliance officer reactivates it. Reactivation does not reopen the `ComplianceAlert` that caused the suspension — that alert stays `CLOSED` with its `SUSPICIOUS` decision as the historical record; reactivation is a separate, later fact (`ASSOCIATION_REACTIVATED` journal entry).
`functional` `security`

### Association Card
The UI element displayed in the public discovery grid (landing page). Each card shows the association's name, cause, location, verification status, live campaign progress, key stats (total raised, donor count), and badges.
`functional`

### Association Profile
The extended profile attached to an association account. Contains: legal name, SIREN, city, postal code, contact person, description, and verification status. Pre-filled from the Annuaire des Entreprises during signup.
`functional`

### Autosave
A behavior in campaign editing tabs (Info, Budget, Milestones) where changes are saved automatically to the backend after a short pause in user input (debounce). The association does not need to press a save button; a subtle saving indicator confirms each write. Implemented with a debounced `setTimeout` that resets on every keystroke.
`functional` `technical`

---

## B

### Bénéficiaire
An organization that receives funds collected by an association. A beneficiary is identified by its SIREN (and optionally its SIRET) and can have multiple IBANs associated with it. Each IBAN can be verified via the VOP (Verification of Payee) service to ensure that the account holder corresponds to the registered organization.
`functional` `business`

### Badge (NFT Badge)
A non-transferable digital attestation awarded to donors and associations for reaching specific achievements. Badges are minted as soulbound tokens on-chain. They serve as proof of engagement, not as speculative assets. Examples include "Première Graine" (first donation), "Donateur Fidèle" (loyal donor), "Organisation Vérifiée" (verified org).
`functional` `blockchain`

### Badge Scope
Badges are categorized by scope: **org** (tied to the association overall, e.g. "100K Club"), **campaign** (tied to a specific campaign, e.g. "Objectif Atteint"), or **transparency** (tied to financial accountability, e.g. "Budget Équilibré").
`functional`

### BCrypt
The password hashing algorithm used by the backend (strength 12). Passwords are never stored in plain text — only their BCrypt hash is persisted.
`security` `technical`

### Blockchain
A distributed, immutable digital ledger. CommonLink uses blockchain as a certification tool — not as a payment method. Think of it as a "public accounting book" where donation proofs are permanently recorded.
`blockchain`

### Budget (Campaign Budget)
The detailed financial plan of a campaign, structured in the French accounting format (charges vs. produits). Includes line items such as purchases, external services, personnel, grants, and in-kind contributions. Associations can publish a balanced budget to earn the "Budget Équilibré" badge.
`functional` `business`

### Budget prévisionnel
The forward-looking accounting plan for a campaign, split into two sides: **Charges** (expenses) and **Produits** (income/revenue). Stored as ordered sections with line items, each carrying a label and an estimated amount. The full structure is bulk-replaced on each save (no partial update). Exposed via `PUT /api/association/campaigns/{id}/budget`.
`functional` `technical`

### BudgetSide
Enum that indicates which side of the budget a section belongs to: `EXPENSE` (Charges) or `REVENUE` (Produits). French display labels: _Charges_ and _Produits_.
`technical`

### Budget Variance (Écart)
The difference between a planned budget line and the corresponding actual spending at the end of a campaign. Displayed in the Reporting tab as `Écart = Réalisé − Prévisionnel` with a percentage column. A negative écart on charges means underspending (favorable); a positive écart means overspending. Computed per section row from the `ReportingSectionDto` returned by `GET /api/association/campaigns/{id}/reporting`.
`functional` `business`

---

## C

### Campaign (Campagne)
A time-bound fundraising initiative created by an association. Each campaign has a name, emoji, financial goal, start/end dates, a budget prévisionnel, milestones, a smart contract address, and a progress bar (raised vs. goal). Campaigns can be in status: `DRAFT`, `LIVE`, or `ENDED`.
`functional` `business`

### Campaign Completion Score
A percentage (0–100%) displayed in the campaign editor hero bar, representing how many of the five required readiness steps are complete: **Info** (name, goal, dates filled), **Budget** (balanced charges = produits), **Milestones** (at least one defined), **Payments** (at least one beneficiary configured), and **Reporting** (reporting data available). Clicking a step chip navigates directly to the corresponding tab. The score is computed client-side from the current `CampaignDto` on every render — no extra API call.
`functional`

### CampaignStatus
Enum representing the lifecycle of a campaign. Transitions are one-directional and strictly enforced by the backend: `DRAFT` → `LIVE` → `ENDED`. No backwards transition is allowed; `ENDED` is terminal. The transition is validated in `CampaignService` and throws a 422 if violated.
`technical` `functional`

### Campaign Report
A public, unauthenticated report of a campaign's content, submitted via a "Report this campaign" popup on the landing page (message + optional reporter e-mail, no account required). Raises a `CAMPAIGN_REPORT` `ComplianceAlert` on the owning association and moves its `AssociationStatus` to `ALERT`. Each submission writes its own append-only journal entry (`CAMPAIGN_REPORTED`), so a second report received while the first alert is still open is not lost. If the compliance officer rules the report founded (`SUSPICIOUS`), the association moves to `SUSPENDED`; otherwise it clears back to `ACTIVE` provided no other report is still open.
`functional` `security`

### Campaign Category (Cause)
The thematic classification of an association. Categories include: Environnement, Social, Éducation, Santé, Culture, Animal, Humanitaire. Used for filtering in the discovery grid.
`functional`

### Card Payment
The default payment method for donations. Supports Visa and Mastercard via a standard card flow.
`functional`

### Collection Cap (Plafond de collecte)
The maximum a campaign may collect: `goal × (1 + margin)`, where the margin is a platform-wide setting (`app.donation.cap.margin-percent`, default 10%). Checked at payment initiation, before the provider is called — a donation above the cap is **refused, never refunded**, since by the time a payment is captured the fiscal receipt is numbered, hashed on-chain and emailed. Amounts held by payment sessions still open count against the cap for `app.donation.cap.reservation-ttl` (default 30 min), so simultaneous checkouts cannot collectively overshoot. Exposed to the widget as `remainingCapacity`; enforced by `DonationCapService`, which raises a 409 carrying `code: COLLECTION_CAP_EXCEEDED`.
`functional` `business` `technical`

### Certification (On-Chain)
The act of recording a donation proof on the blockchain. This includes the amount, date, and campaign reference. Once recorded, the certification is permanent, tamper-proof, and publicly verifiable. The donor does not interact with the blockchain directly — certification happens automatically in the backend.
`blockchain` `functional`

### Contribution Donut
A visual chart in the donor dashboard showing the proportion of the donor's contribution relative to the total raised amount for a given campaign.
`functional`

### CORS (Cross-Origin Resource Sharing)
A security mechanism that restricts which domains can communicate with the backend API. Only the authorized frontend URL is allowed — no other website can make requests to the API.
`technical` `security`

---

## D

### Disbursement (Paiement sortant)
A payment made by an association to a third-party provider (e.g., a supplier, contractor) from the funds raised. Each disbursement is tracked, documented, and certified on-chain to ensure full financial transparency. Visible in the "Paiements" tab of a live campaign.
`functional` `business`

### Discovery Grid
The main public page where visitors browse associations. Displays association cards in a filterable, searchable grid layout. Filters include cause category, geographic scope, and campaign status.
`functional`

### Donation Modal
The UI popup that opens when a user clicks "Donate" on an association's page. It includes donation type selection (one-time, monthly, re-donate), amount presets, custom amount input, anonymous toggle, payment method selection, gas estimate (for wallet), and confirmation button.
`functional`

### Donation Type
The frequency model of a donation: **One-time** (don unique), **Monthly** (mensuel, recurring), or **Re-donate** (redonner, repeat to the same campaign).
`functional`

### Donor
One of the two user types on CommonLink. A donor is an individual or entity that makes donations to association campaigns. Donors can track their impact, earn NFT badges, and appear on leaderboards.
`business` `functional`

### Donor Aggregate
A consolidated view of a single donor's activity within one campaign. Aggregates all confirmed transactions into: total amount donated, transaction count, and date of last donation. Anonymous donors (`DonorProfile.anonymous = true`) appear as "Anonyme" — identity is masked at the service layer, on-chain proof (`providerRef`) is always retained. Backed by `CampaignDonorDto` from `GET /api/campaigns/{campaignId}/donors` (`DonorAggregateService`, Step 6).
`functional` `technical`

### Donor Profile
The profile attached to a donor account. Contains: display name, avatar, and anonymity preference. Created automatically (empty) at registration.
`functional`

---

## E

### Email + Password Authentication
One of the three login methods. Users register with an email and a password (minimum 8 characters). The password is hashed with BCrypt before storage. Users who registered via Magic Link or Google have no password set — they can optionally set one later.
`functional` `security`

---

## F

### Fiat
Traditional government-issued currency (EUR in our case). On CommonLink, donors pay in euros — the platform is not a crypto payment system. The blockchain layer is used for certification, not for transferring funds.
`business` `blockchain`

### Flyway
A database migration tool that manages the evolution of the database schema through versioned SQL files (V1, V2, V3…). Ensures the database structure is consistent across all environments.
`technical`

---

## G

### Guest Donor
A donor who makes a donation via the [[Donation Widget]] without having a CommonLink account. On form submission, `GuestDonorService.findOrCreateGuestDonor(email)` creates a `User` (role DONOR, `guest=true`, no usable password) and an associated `DonorProfile(anonymous=true)` — idempotent by email. The on-chain flow is identical to a registered donor: `DonationService.confirmDonation` derives a deterministic EVM wallet address via `DonorAddressGenerator.generate(donorProfile.id)` (HMAC-SHA256, no private key) and enqueues a `RECORD_DONATION` job. The fiscal identity (name, address) is captured in the donation form and stored as a snapshot on the `Donation` row — independent of `anonymousDisplay`, which only controls the public leaderboard.
`functional` `technical`

### Gel des avoirs (Asset Freeze)
A regulatory measure ordering the immediate blocking of all funds and economic resources belonging to or controlled by a designated person or entity. In France, the applicable measures are published by the **Direction générale du Trésor (DG Trésor)** in a consolidated national register, which integrates French national decisions, EU regulations, and UN Security Council resolutions. CommonLink queries this register via an internal screening service before entering into a relationship with an association or processing a donation.
`security` `business`

### Gas Fee
The small transaction cost for recording data on a blockchain network. On CommonLink, gas fees apply only when using a crypto wallet for payment (Polygon network). The estimated gas cost is displayed in the donation modal (e.g., ~€0.08).
`blockchain`

### Geographic Scope (Zone)
An association's operational range: **locale** (one city/region), **nationale** (country-wide), or **internationale** (multiple countries). Used as a filter in the discovery grid.
`functional` `business`

### Goal-Budget Constraint
A business rule enforced in the campaign Info tab: the fundraising goal ("objectif de la collecte") cannot exceed the sum of all Revenue (Produits) lines in the campaign budget. Validation runs on save — if the goal exceeds `totalProduits`, the save is blocked and an inline error is shown. Ensures that the published fundraising target is always backed by a realistic revenue plan.
`functional` `business`

### Google OAuth
One of the three login methods. Users click "Continue with Google" → a popup opens → Google returns an identity token (idToken) → the backend verifies it and creates/links the account. No Google Client Secret is used on the frontend.
`functional` `security`

---

## I

### IBAN (International Bank Account Number)
Standardized unique identifier for a bank account at the international level (ISO 13616 standard). Composed of 2 country code letters, 2 check digits and the BBAN (Basic Bank Account Number), on a maximum of 34 characters. On CommonLink, beneficiary IBANs are validated via the mod-97 algorithm and can be verified via the VOP service.
`technical` `functional`

### INSEE Sirene
Official national directory of French businesses and establishments, managed by INSEE (National Institute of Statistics and Economic Studies). The Sirene 3.11 API allows you to search for an organization by its SIREN or SIRET and retrieve its legal information (name, address, NAF code, active/inactive status). Used by CommonLink for beneficiary selection.
`business` `technical`

### i18n (Internationalization)
The system that makes the entire UI available in multiple languages. CommonLink defaults to French (`fr`) with English (`en`) as a secondary language. All user-visible text is stored in translation files — zero hardcoded strings in the code.
`technical` `functional`

### Impact
The concrete, measurable outcomes of a campaign's milestones. Displayed as metrics in the donor dashboard (e.g., "5 school kits funded", "8 families heated"). Impact data links donations to real-world results.
`functional` `business`

### IPFS (InterPlanetary File System)
A decentralized file storage protocol. Used by CommonLink to anchor campaign final reports ("Rapport Certifié" badge). Files stored on IPFS are permanent and tamper-proof, complementing on-chain certification.
`blockchain` `technical`

---

## J

### JWT (JSON Web Token)
A digitally signed token that the backend issues upon successful authentication. Contains the user's ID, email, role, and expiration time. Sent with every API request as `Authorization: Bearer <token>`. Valid for 15 minutes.
`technical` `security`

---

## K

### KYC (Know Your Customer)
The identity verification process for associations. Involves validating the SIREN number through the Annuaire des Entreprises and confirming the RNA (Répertoire National des Associations) registration. Earning the "Organisation Vérifiée" badge proves KYC completion.
`business` `security`

---

## L

### Leaderboard
A ranking of donors by total cumulative donations for a given association. Visible in the donor dashboard. Donors can opt out by enabling anonymous donations. The leaderboard encourages engagement through gamification.
`functional`

### Loi 1901
The French law governing non-profit associations. Only organizations registered under this legal framework (legal category 9220) can create an account on CommonLink.
`business`

---

## M

### Magic Link
A passwordless login method. The user enters their email → receives a link valid for 15 minutes → clicks it → is instantly authenticated. The link contains an opaque token whose SHA-256 hash is stored in the database. Each link can only be used once. Rate-limited to 3 requests per email per 10 minutes.
`functional` `security`

### Milestone (Palier)
A funding threshold within a campaign that unlocks a specific deliverable. Each milestone has a title, emoji, description, target amount, status, and sort order. Status (`MilestoneStatus`) can be: `LOCKED` (not yet funded), `CURRENT` (actively being targeted), or `REACHED` (funded). Milestones connect financial goals to measurable, concrete impact (e.g., "€5,000 → heating equipment for 8 families"). Managed via `POST /PUT /DELETE /api/association/campaigns/{id}/milestones`.
`functional` `business`

### MilestoneStatus
Enum for the progress state of a milestone: `LOCKED` (below target), `CURRENT` (the active milestone being tracked), `REACHED` (target met, with a `reachedAt` timestamp). Only one milestone should be `CURRENT` at a time.
`technical`

### Mock/simulation mode
A local-development mode for the blockchain layer. Activated by setting `onchain.mock: true` in `application.yml`. When active, `MockOnchainRegistry` replaces `OnchainRegistryClient` as the `OnchainRegistry` bean: every on-chain write call returns a synthetic `TransactionReceipt` (random tx hash, auto-incremented block number, status `0x1`) with no RPC call, no private key, and no network I/O. The outbox worker (`onchain_jobs`) drains normally — jobs move from `PENDING` to `DONE` — so the full flow can be exercised locally without a running blockchain node. When mock mode is on, `Web3jConfig` is also excluded from the context (`@ConditionalOnProperty(onchain.mock=false)`) — otherwise its signing-credential beans would be built from the empty `recorder-pk` / `curator-pk` prod defaults and abort startup with `Zero length BigInteger`; `@EnableScheduling` therefore lives in `SchedulingConfig`, not `Web3jConfig`. Temporarily accepted in production until the blockchain component is deployed: `application-prod.yml` sets `mock: true`, `OnchainMockGuard` only logs a WARN for the `prod` + `mock=true` combination (its fail-fast is commented out behind a TODO), and both matching tests (`OnchainMockGuardTest.throws when prod active and mock true`, `ProdConfigSecurityTest.onchain mock is disabled in prod`) are `@Disabled` until it goes live.
`technical` `onchain` `dx`

### Mollie Connect

- **`app.mollie.connect.mock`** — Full bypass. When `true`, `buildAuthorizationUrl` skips Mollie
  entirely and creates a local COMPLETED connection (no popup, no client-link). All-or-nothing;
  used to develop the app with no Mollie credentials at all.

- **`app.mollie.connect.allow-fake-completion`** — Narrow dev/staging escape hatch. When `true`,
  registers `MollieConnectMockController` (`POST /api/mollie/connect/dev/complete`), which flips an
  **already-existing** real connection to COMPLETED (`canReceivePayments`/`canReceiveSettlements`
  true, `state` ACTIVE) — as if Mollie had just validated the KYC. Unlike `mock`, it keeps the real
  OAuth popup + client-link / partner creation intact: you go through the genuine flow, then simulate
  only the final validation Mollie has no dashboard button for. Gated declaratively via
  `@ConditionalOnProperty` (same style as `MockOnchainRegistry`): in production the flag is `false`,
  the controller bean never exists, and the route returns **404**. Once COMPLETED,
  `refreshOnboardingStatusIfStale` never re-polls Mollie, so the state is durable.

- **Mollie test mode** — In Mollie's sandbox, payment methods are active immediately even while KYC
  shows "pending"; card test payments work without a validated onboarding. There is no dashboard
  button to force KYC approval. Business-operations APIs (Settlements/Payouts/Balances) do NOT
  support test mode — the SEPA settlement → Monerium vIBAN leg cannot be validated in sandbox.
`technical` `integration` `association`

### Mollie Client Link
A Mollie API construct created via `POST /v2/client-links` authenticated with a Bearer Advanced access token carrying the `clients.write` permission (HTTP Basic is only valid on the `/oauth2/tokens` endpoints). It bundles account creation with pre-filled organization data (name, address, legal entity `fr-association`, owner contact, and the SIREN as `registrationNumber` — only when known: Mollie validates that field against the chamber-of-commerce registry, so the RNA identifier is never sent there) and OAuth authorization into a single popup URL. CommonLink appends `client_id`, `state`, `scope`, and `approval_prompt=force` query parameters before sending the user to the URL. On completion, Mollie redirects to the registered `redirect_uri` with `code` and `state`. A client link expires 30 days after creation.
`technical` `integration`

### Mollie Connect Token Lifecycle
The OAuth `access_token` is short-lived (`expires_in` = 3600s) and refreshed silently by `MollieConnectTokenManager` when within 60s of expiry, under a pessimistic write lock (guards against concurrent double-refresh → `invalid_grant`). Per Mollie's spec the **refresh token does not expire** — it only becomes invalid if the merchant revokes the app, or a re-authorization with `approval_prompt=force` revokes the prior grant. Each refresh **rotates** both tokens (new access + new refresh), and both are persisted. On a rejected refresh the connection is flipped to `BROKEN` (`MollieConnectionState`); the association profile page then shows a red badge + reconnect button (passive signal — no proactive notification). Both tokens are encrypted at rest via the shared [[Token Encryption Key]]. NB: donation payments currently authenticate with the single platform `MOLLIE_API_KEY`, **not** the per-association OAuth token — so a `BROKEN` connection today only stalls KYC status polling, not incoming donations. That changes if/when payments are routed through each association's Mollie account.
`technical` `integration` `association`

### Token Encryption Key
The `MONERIUM_TOKEN_ENC_KEY` env var (base64, 32 bytes = AES-256). Despite its Monerium-centric name it is a **shared** key: the same `MoneriumTokenConverter` encrypts both Monerium and Mollie Connect OAuth tokens at rest (AES-256-GCM). When unset the converter is a no-op (plaintext) — intended for dev/staging, **must be set in prod**. ⚠ Rotating or removing it breaks decryption of every stored token for both integrations (old rows throw → 500 on status read); any change requires a re-encryption migration. A neutrally-named key is deferred to keep the blast radius minimal.
`technical` `security`

### Mollie Capabilities API
Beta Mollie API (`GET /v2/capabilities`) that replaced the deprecated `GET /v2/onboarding/me` for reading KYC/merchant status. Returns a list of `capability` objects at the organization level. Each has `name` ("payments", "settlements"), `status` (`unrequested` | `enabled` | `disabled` | `pending`), `statusReason` (`onboarding-information-needed` | `requirement-past-due` | null), and `requirements[]` (each with `id`, `status`, and `_links.dashboard` deep link). CommonLink maps this to `MollieOnboardingStatus`: payments `enabled` → COMPLETED; pending with action required → NEEDS_DATA; pending with no immediate action → IN_REVIEW. The dashboard URL comes from the first currently-due requirement's `_links.dashboard`. Status changes detected during polling fire a `MollieOnboardingStatusChangedEvent` for async email delivery.
`technical` `integration`

### Mollie Onboarding Status
The KYC approval state derived from the [[Mollie Capabilities API]]. Three values: `NEEDS_DATA` (action required — Mollie needs information or documents), `IN_REVIEW` (dossier submitted, Mollie reviewing), `COMPLETED` (merchant approved, payments enabled). CommonLink polls at most once every 5 minutes per association (throttled re-fetch triggered by status reads). Stored as `MollieOnboardingStatus` enum in DB.
`technical` `integration` `association`

### Mollie Hosted Onboarding
Mollie's own onboarding wizard, reached via the `_links.dashboard` deep link from the [[Mollie Capabilities API]] requirements array. It is the only way to complete KYC steps requiring human review (identity verification, UBO declaration, bank account verification). CommonLink stores the link on `MollieConnection.onboardingDashboardUrl`, exposes it as `dashboardUrl` in the KYC status DTO, and the association profile page shows a "Complete my onboarding" button opening it in a new tab while the status is `NEEDS_DATA`. There is no redirect back to CommonLink; completion surfaces through the throttled capabilities poll.
`technical` `integration` `association`

### Monorepo
A single Git repository containing both the frontend (`app/`) and backend (`api/`) projects. Each project is fully autonomous — there is no build tool at the root level.
`technical`

---

## N

### NFT (Non-Fungible Token)
A unique digital token recorded on a blockchain. On CommonLink, NFTs are used exclusively as badges — non-transferable attestations of achievements. They are NOT speculative assets and cannot be resold.
`blockchain` `functional`

---

## O

### On-Chain
Recorded on a blockchain. When a donation is "certified on-chain," it means the proof of that donation is permanently and publicly stored in an immutable ledger. This is the core value proposition of CommonLink.
`blockchain`

### Opaque Token
A cryptographically random string (32 bytes, 64 hex characters) used for Magic Links and Refresh Tokens. The raw token is sent to the user; only its SHA-256 hash is stored in the database. This ensures that even if the database is compromised, tokens cannot be reused.
`security` `technical`

---

## P

### Payment Method
The means by which a donor completes a donation. Supported methods: **Card** (Visa/Mastercard), **SEPA transfer** (instant bank transfer), **Crypto Wallet** (Polygon/USDC), and **Apple Pay**.
`functional`

### Mode de versement (Payment Method on the Receipt)
The line on the fiscal receipt stating how the donor actually paid. The provider's method code is captured when the payment is confirmed (`donations.payment_method`) and mapped to a French label — _Carte bancaire_, _Virement bancaire_, _Prélèvement SEPA_, _Virement bancaire en ligne_. An unrecognised provider code is printed verbatim rather than forced into a category, and a donation confirmed without a provider payload prints _Non précisé_. Previously the receipt printed the list of accepted methods, which was false for every individual donation. See `docs/legal/E5-recu-fiscal-mentions.md`.
`functional` `business`

### Polygon
A Layer 2 blockchain network used by CommonLink for on-chain certifications and wallet-based donations. Polygon offers low gas fees (~€0.08 per transaction) and fast confirmation times, making it suitable for micro-certifications.
`blockchain` `technical`

### Post-Publish Locking
The behavior where certain campaign editor tabs become read-only once a campaign transitions from `DRAFT` to `LIVE`. Tabs marked `postPublish: false` are hidden or disabled after publishing to protect data integrity (e.g., Info fields that affect the smart contract). Tabs marked `postPublish: true` (Donors, Transactions, Reporting) only become accessible after publishing. The lock state is derived from `campaign.status === 'LIVE' || campaign.status === 'ENDED'`.
`functional` `technical`

### ProblemDetail (RFC 9457)
The standardized error response format used by the API. Every error includes a type, title, status code, detail message, and optionally a machine-readable code (e.g., `PASSWORD_NOT_SET`, `TOKEN_EXPIRED`) to help the frontend display the right message.
`technical`

### Progress Bar
The visual indicator showing how much of a campaign's goal has been reached. Live campaigns show an animated gradient bar (green to cyan). The bar appears both on association cards in the discovery grid and in campaign detail views.
`functional`

### Public Page (Espace public)
A customizable, publicly visible page for each association. Showcases campaigns, milestones, impact metrics, budget transparency, badges, and payment history. Associations can toggle section visibility and customize their tagline.
`functional` `business`

---

## R

### Registre national des gels d'avoirs (DG Trésor register)
The authoritative French register of all persons and entities subject to an asset-freeze measure. Published by the Direction générale du Trésor in XML format via a public API. CommonLink uses this register as its **sole screening source** for asset-freeze compliance — it already consolidates French, EU (CFSP), and UN sanctions applicable in France, so no additional source needs to be ingested separately. The register is downloaded in full on each ingestion; entries absent from a new publication are removed from the internal table (the measure has been lifted).
`security` `business`

### Rate Limiting
A security mechanism that restricts the number of certain actions within a time window. Example: a maximum of 3 Magic Link requests per email per 10 minutes. Returns HTTP 429 with a `Retry-After` header.
`security` `technical`

### Refresh Token
A long-lived credential (valid 30 days) stored in memory on the client side. Used to obtain a new Access Token without requiring the user to log in again. The raw token is held by the client; only its SHA-256 hash is stored in the database. Can be revoked (e.g., on logout).
`technical` `security`

### Reporting
The association-facing analytics section. Includes three tabs: **Impact report** (milestones, concrete outcomes), **Budget realized vs planned** (actual spending vs. forecast), and **Payment journal** (full disbursement history with dates, recipients, and amounts).
`functional` `business`

### RGPD (GDPR)
The European General Data Protection Regulation. CommonLink processes personal data in compliance with RGPD: minimal data collection, secure storage, no data sold to third parties.
`business` `security`

### Risk Level (Niveau de risque LCB-FT)
The AML/CFT (LCB-FT) risk classification assigned to an association or a donation. Three levels: **LOW** (simplified due diligence may apply), **STANDARD** (baseline due diligence, default for all new records), **HIGH** (enhanced due diligence required). The mapping from a level to the corresponding due-diligence measures is defined in a separate, versioned classification document — not in code. The level is stored alongside the version of that document in force at assessment time (`riskClassificationVersion`), so that a past assessment remains interpretable after the document is revised. On donations, the level is an immutable snapshot of the risk at the time of the donation, independent of any later reassessments of the association.
`security` `business`

### RNA (Répertoire National des Associations)
The official French registry of associations. Used in conjunction with the SIREN to verify the legal identity and legitimacy of an association during KYC.
`business`

### Role
A user's type on the platform. Set at registration and determines the dashboard layout, available features, and profile structure. Cannot be changed after account creation.

| Role | Spring authority | On-chain actions | Notes |
|---|---|---|---|
| `DONOR` | `ROLE_DONOR` | — | Browses campaigns, makes donations |
| `ASSOCIATION` | `ROLE_ASSOCIATION` | — | Creates and manages campaigns |
| `CURATOR` | `ROLE_CURATOR` | verify/revoke/restore association; pause/unpause/cancel/complete/revert campaign | Platform moderator; accesses `/api/admin/onchain/**` |
| `ADMIN` | `ROLE_ADMIN` | same as CURATOR | Super-admin; `ADMIN` is not a `UserRole` enum value — granted by external identity provider |
| SYSTEM | — | createCampaign, publishCampaign, recordDonation, markMilestoneReached, updateCampaignBudget | Backend service itself; never exposed over HTTP |

`functional` `technical`

---

## S

### SEPA Transfer
A bank transfer payment method available in the donation modal. Uses the SEPA (Single Euro Payments Area) instant transfer protocol, typically processed within 1–2 business days.
`functional`

### Screening Correspondence (Correspondance de criblage)
One recorded pairing between a screened name and an entry of the national asset-freeze register, stored in `freeze_screening_match`. It carries the register entry's public reference (`id_registre`), a frozen snapshot of its name, nature, sanctions programme and date of birth, the similarity score, the threshold and algorithm applied, and the register version screened against. It is the evidence a compliance officer's decision rests on: the compliance journal records only aggregates (match count, top score), which prove a control took place but do not allow a ruling. Snapshots rather than joins, so the evidence survives the register entry being delisted. Retained five years (art. L.561-12 CMF).
`functional` `compliance` `security`

### Screened Normalized Name (Valeur criblée normalisée)
The string actually compared during a freeze screening, after `NameNormalizer` strips accents, uppercases and collapses punctuation — the association "TECHNO +" is compared as "TECHNO". It is frozen at screening time on each Screening Correspondence, because it is the value that produced the score and must survive a later correction of the dossier name. Showing only the raw name leaves a high score against a dissimilar-looking register entry unexplained.
`functional` `compliance`

### SHA-256
A cryptographic hash function used to store sensitive tokens (Magic Links, Refresh Tokens) in the database. Only the hash is persisted — the original token cannot be reconstructed from the hash.
`security` `technical`

### SIREN
A 9-digit identification number assigned to each French company or association (Business Directory Identification System). Used on CommonLink to identify associations during registration (via the Business Directory) and to identify beneficiaries when added by an association (via the INSEE Sirene API).
`business` `technical`

### SIREN-only Signup (Inscription sans RNA)
The sign-up path for an association that holds a SIREN but no RNA, and is therefore absent from the RNA (JOAFE) search the standard flow relies on. The user types a SIREN, the browser queries the Recherche d'entreprises open registry directly — keyless and CORS-open, exactly as the standard flow queries the JOAFE dataset for RNA numbers — and the returned name and registered-office address are displayed for confirmation — read-only, as in the RNA flow. The SIREN is written to both `identifier` (the mandatory primary identifier) and `siren`, since Mollie's `registrationNumber`, the Cerfa receipt and the mandate PDF read `siren` directly. No schema change: `identifier` stays mandatory, and its "RNA or SIREN" semantics predate this path. Eligibility is unaffected — a non-association or inactive SIREN is flagged to the user but never blocked at sign-up, the ruling staying with the curator registry pre-check at KYC.
`functional` `business`

### SIRET
14-digit identification number for a French establishment, composed of the SIREN (9 digits) followed by the NIC — Internal Classification Number (5 digits). Identifies a specific establishment of an organization (headquarters, branch, subsidiary). On CommonLink, the SIRET can be used instead of the SIREN to select a beneficiary with more precision.
`business` `technical`

### Smart Contract
An autonomous program deployed on a blockchain that executes predefined rules automatically. On CommonLink, smart contracts handle donation certification and NFT badge minting without human intervention. Each campaign is linked to a smart contract address (e.g., `0x4f8a…3c2e`).
`blockchain` `technical`

### Soulbound Token
A type of NFT that cannot be transferred or sold. All CommonLink badges are soulbound — they are permanent attestations attached to the recipient's account. This prevents badge trading and ensures authenticity.
`blockchain`

### Stateless Session
The authentication model used by CommonLink. The server does not store session data — authentication state is carried entirely by JWT tokens sent with each request. This makes the system scalable and eliminates session hijacking risks.
`technical` `security`

### Swagger UI
The interactive API documentation interface accessible at `/api/docs`. Allows developers (and curious team members) to browse all endpoints, see request/response schemas, and test API calls directly from a browser.
`technical`

---

## T

### Testcontainers
A testing library that spins up real database instances (PostgreSQL) in Docker containers for integration tests. Ensures tests run against a real database rather than mocks.
`technical`

### Token Hash Service
An internal utility that generates cryptographically random tokens and computes their SHA-256 hashes. Used for both Magic Links and Refresh Tokens to ensure that only hashes — never raw tokens — are stored in the database.
`technical` `security`

### Transaction
A recorded donation event. Each transaction has: a unique ID, a blockchain hash (for on-chain verification), the donor reference, campaign reference, amount, status (`confirmed`, `pending`, or `failed`), and date. Transactions are displayed in the campaign detail view.
`functional` `blockchain`

### Transparency Badge
A badge category specifically tied to financial accountability. Examples: "Budget Équilibré" (published a balanced budget), "Transparence Totale" (all outgoing payments documented), "Rapport Certifié" (end-of-campaign report anchored on IPFS).
`functional` `blockchain`

### Two-Step Delete
A UX safety pattern used throughout the campaign editor (budget lines, milestones, beneficiaries) where a first click replaces the delete button with a confirmation prompt ("Confirmer ?"), and only a second click within a short window triggers the actual deletion. Prevents accidental data loss. After the timeout the button reverts to its default state without deleting.
`functional`

---

## U

### USDC
A stablecoin (digital currency pegged 1:1 to the US Dollar) used on the Polygon network for crypto wallet donations. Donors who choose the wallet payment method transact in USDC on Polygon.
`blockchain`

### UserDto
The data object representing a user in API responses. Contains: id, email, role, display name, avatar URL, auth provider, email verification status, and creation date. Entities are never exposed directly — they are always mapped to DTOs.
`technical`

---

## V

### VOP (Verification of Payee)
Service de vérification de l'identité du titulaire d'un compte bancaire via son IBAN. Permet de confirmer que le nom enregistré sur le compte correspond bien à l'organisation bénéficiaire déclarée. Sur CommonLink, le service VOP est intégré via l'API Qonto SEPA VOP. Résultats possibles : **MATCH** (correspondance exacte), **CLOSE_MATCH** (correspondance approximative avec nom suggéré), **NO_MATCH** (pas de correspondance), **NOT_POSSIBLE** (la banque ne supporte pas VOP pour cet IBAN). Un mode démo simule les résultats selon le dernier chiffre de l'IBAN.
`technical` `functional` `security`

### Verified Association
An association that has completed the KYC process: SIREN validated via the Annuaire des Entreprises, RNA confirmed, and identity reviewed. Verified associations display a green checkmark badge on their card and receive the "Organisation Vérifiée" NFT.
`functional` `business`

---

## W

### Widget Token
A cryptographically random public identifier (`clk_` prefix + 24 bytes Base64url = 32 chars, 192 bits of entropy) that authorises a donation widget embed. Scoped to one association; the backend resolves the token to the association and its destination campaign on every request. Rotatable/revocable at any time from the association dashboard — rotation is immediate and invalidates any existing embed snippets using the old token. Stored as `widgetToken VARCHAR UNIQUE` on `association_profiles`; generated via `POST /api/association/me/widget/token`.
`technical` `security`

### Wallet (Crypto Wallet)
A payment method for donors who prefer to donate via cryptocurrency. Transactions use the Polygon network with USDC. When selected, the donation modal displays the estimated gas fee. This is the only payment method that involves direct blockchain interaction by the user.
`functional` `blockchain`

---

## A

### Async Receipt
The asynchronous generation of a Cerfa 2041-RD tax receipt PDF after a donation is confirmed. Triggered by a `DonationConfirmedEvent` published synchronously at the end of `DonationService.confirmDonation`. The event listener (`DonationReceiptService.onDonationConfirmed`) is annotated `@TransactionalEventListener(AFTER_COMMIT)` + `@Async`: it fires only after the confirmation transaction commits, on a separate thread. Steps: generate PDF → compute `keccak256` hash → enqueue `RECORD_DONATION` on-chain job via `OnchainOutboxService` with `correlationKey="DONATION:<id>"`. A `@Scheduled` reconciler re-enqueues confirmed donations whose job is missing (crash-safe). The webhook response path is never blocked by PDF generation.
`technical` `blockchain`

---

## D

### Donation Widget
An embeddable iframe-based donation form that any association can drop onto their own website with a single line of HTML (`<script src="…/widget.js" data-widget-token="clk_…">`). The script (`app/public/widget.js`) injects a sandboxed `<iframe>` pointing to a CommonLink-hosted page (`/{locale}/embed/donate/{widgetToken}`). The donor fills in identity and payment details, is redirected to Mollie's hosted checkout, and lands on a CommonLink confirmation page. The association controls which campaign receives the donations via the [[Widget Token]] and [[Destination Campaign]]. No server-side code is required on the association's website.
`functional` `technical`

### Destination Campaign
The campaign configured by an association as the recipient of all donations received through their widget. Set in the association dashboard under **Profile → Widget** tab. Must be in `LIVE` status for the widget to accept donations — if the configured campaign is not live, the widget refuses to create donations until it goes live. Stored as `widgetDestinationCampaign` on `AssociationProfile`; set via `PATCH /api/association/me`. Changing the destination immediately redirects future donations; past donations are unaffected.
`functional` `technical`

### DashboardStats
Aggregate payload returned by `GET /api/association/dashboard`. Contains: `totalRaisedActive` (sum of confirmed donations on LIVE campaigns), `activeCampaignCount`, `nextMilestone` (closest unfinished milestone), `avgProgress` (mean raised/goal ratio across LIVE campaigns), `donations6Months` (six monthly buckets), and `recentActivity` (last 10 events).
`technical` `backend` `association`

### ActivityType
Enum classifying events in the dashboard activity feed: `DONATION`, `MILESTONE_REACHED`, `PAYMENT`. Initially only `DONATION` events are populated; others are added in Steps 4/6.
`technical` `backend`

### MonthlyPoint
A single data point in the 6-month fundraising chart: `month` (ISO string "YYYY-MM") and `amount` (euros raised that calendar month). Months with no donations are included with `amount = 0`.
`technical` `frontend` `backend`

### nextMilestone
Dashboard field identifying the campaign milestone closest to being reached — i.e. the unfinished milestone (status ≠ REACHED) across all LIVE campaigns with the smallest positive `targetAmount − campaign.raised`. Null when no such milestone exists.
`technical` `backend` `association`

### Donation Public Ref
An opaque UUID minted server-side (`PublicWidgetService.createDonation`) before the Mollie payment is created, since Mollie's own payment id does not exist yet at redirect-URL construction time. Carried as the `ref` query param on the Mollie success redirect URL alongside the dataLayer tracking payload (amount, currency, campaign, association, anonymous flag) — never on the cancel URL. Stored on `Donation.publicRef` (`donations.public_ref`, partial unique index). Looked up by the public, unauthenticated `GET /api/public/widget/donations/{ref}/status` endpoint, which the `/return` page polls to decide whether to push the GA4 `purchase` event. Leaks no internal data beyond PENDING/CONFIRMED and, once confirmed, the payment method.
`technical` `backend`

## G

### GA4 Ecommerce Donation Tracking
Google Analytics 4 ecommerce events pushed to `window.dataLayer` (via `app/src/lib/gtm.ts#pushDonationEvent`) to track the donation funnel for Ad Grants reporting. `begin_checkout` fires client-side right before the donor is redirected to Mollie's hosted checkout (`useGuestDonation`), using the [[Donation Public Ref]] as `transaction_id`. `purchase` fires on the `/return` page once `GET .../donations/{ref}/status` reports CONFIRMED, polled every 750ms bounded to the existing 3s auto-redirect window — never fired for a cancelled payment, and never guessed optimistically. Both events carry `items: [{item_id: campaignId, item_name: campaignName}]` and `affiliation: associationName`; `purchase` additionally carries the Mollie payment method once known. The push is a no-op with nothing to read it when the association has no `gtmContainerId` configured.
`technical` `functional`

## P (continued)

### Payout
An outgoing payment from a campaign to a payee (beneficiary). Created as `PENDING` when the association initiates it, then transitioned to `CONFIRMED` by the association which simultaneously enqueues a `RECORD_PAYOUT` on-chain job. Terminal failure state is `FAILED`.
`domain` `backend` `association`

### PayoutKind
High-level category of a payout used for budget variance reporting: `REMUNERATION` (personnel, category 64) or `EXPENSE` (all other operational charges, categories 60–65). Derived from the French plan comptable `typeCode`.
`technical` `backend`

### PayoutStatus
Lifecycle state of a payout: `PENDING` (created, awaiting confirmation) → `CONFIRMED` (approved by the association, on-chain job enqueued) or `FAILED` (terminal error).
`technical` `backend`

### typeCode (payout)
French plan comptable accounting code stored on a payout, e.g. `"60-mat"` (matières/fournitures), `"64-rem"` (rémunérations), `"65-ges"` (frais de gestion). Used in the Payments tab expense type dropdown and to map actual spending to budget lines in reporting (Step 8).
`technical` `backend` `association`

### RECORD_PAYOUT
14th `OnchainJobAction` variant, enqueued when a payout is confirmed. Carries `RecordPayoutPayload(payoutId, campaignId, amountCents)`. The Solidity function `recordPayout(bytes32, bytes32, uint256)` must be deployed to `CommonLinkRegistry` before the worker can dispatch it (currently stubs with `NotImplementedError`).
`technical` `onchain`

## Z

### Zustand
The state management library used in the frontend. Stores are organized by domain (auth, toast, etc.). The auth store holds access tokens, refresh tokens, and user data in memory — never in localStorage (to prevent XSS attacks).
`technical`
