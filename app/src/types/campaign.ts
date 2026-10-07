/**
 * Possible statuses for a fundraising campaign — mirrors the backend `CampaignStatus` enum
 * exactly (`api/.../entity/Enums.kt`).
 * - DRAFT: not yet published, only visible to the association
 * - LIVE: published and accepting donations
 * - PAUSED: temporarily suspended by the association
 * - REVERT_REQUESTED: association asked to revert to DRAFT; awaiting CURATOR on-chain execution
 * - CANCELLED: cancelled before completion
 * - COMPLETED: reached its goal and completed
 * - ENDED: collection period is over (legacy terminal state)
 */
export const CampaignStatus = {
  DRAFT: 'DRAFT',
  LIVE: 'LIVE',
  PAUSED: 'PAUSED',
  REVERT_REQUESTED: 'REVERT_REQUESTED',
  CANCELLED: 'CANCELLED',
  COMPLETED: 'COMPLETED',
  ENDED: 'ENDED',
} as const;
export type CampaignStatus = typeof CampaignStatus[keyof typeof CampaignStatus];

/**
 * Cause of a campaign — what it funds. Mirrors the backend `CampaignCause` enum exactly
 * (`api/.../entity/Enums.kt`, CHECK constraint in V82). `AUTRE` can be chosen in the editor but
 * is never displayed on public discovery.
 */
export const CampaignCause = {
  SOLIDARITE: 'SOLIDARITE',
  ALIMENTATION: 'ALIMENTATION',
  SANTE: 'SANTE',
  ENFANCE_EDUCATION: 'ENFANCE_EDUCATION',
  ANIMAUX: 'ANIMAUX',
  HANDICAP: 'HANDICAP',
  ENVIRONNEMENT: 'ENVIRONNEMENT',
  CULTURE: 'CULTURE',
  SPORT: 'SPORT',
  DROITS_CITOYENNETE: 'DROITS_CITOYENNETE',
  AUTRE: 'AUTRE',
} as const;
export type CampaignCause = typeof CampaignCause[keyof typeof CampaignCause];

/**
 * Kind of place of action — mirrors the backend `ActionPlaceType` enum.
 * - COMMUNE: French commune (INSEE code) · DEPARTEMENT: French department (code)
 * - FRANCE: whole of France (no code) · PAYS: foreign country (ISO 3166-1 alpha-2)
 */
export const ActionPlaceType = {
  COMMUNE: 'COMMUNE',
  DEPARTEMENT: 'DEPARTEMENT',
  FRANCE: 'FRANCE',
  PAYS: 'PAYS',
} as const;
export type ActionPlaceType = typeof ActionPlaceType[keyof typeof ActionPlaceType];

/** Reach of a campaign, always derived from its place of action — mirrors backend `CampaignScope`. */
export const CampaignScope = {
  LOCALE: 'LOCALE',
  NATIONALE: 'NATIONALE',
  INTERNATIONALE: 'INTERNATIONALE',
} as const;
export type CampaignScope = typeof CampaignScope[keyof typeof CampaignScope];

/** Place of action as returned by the API (`ActionPlaceDto`). Label and coordinates are server-resolved. */
export interface ActionPlaceDto {
  type: ActionPlaceType;
  /** INSEE commune code, department code or ISO country code; null for FRANCE. */
  code: string | null;
  /** Display label, e.g. `Vallauris (06)`. */
  label: string;
  /** Derived scope. */
  scope: CampaignScope;
  /** Commune centre latitude; null unless COMMUNE. */
  latitude: number | null;
  /** Commune centre longitude; null unless COMMUNE. */
  longitude: number | null;
}

/** Place of action sent by the editor (`ActionPlaceRequest`): kind + code only. */
export interface ActionPlaceRequest {
  type: ActionPlaceType;
  /** Code for that kind; omitted for FRANCE. */
  code?: string;
}

/**
 * Side of the budget table — charges (expenses) or produits (income).
 */
export const BudgetSide = {
  EXPENSE: 'EXPENSE',
  REVENUE: 'REVENUE',
} as const;
export type BudgetSide = typeof BudgetSide[keyof typeof BudgetSide];

/**
 * Possible statuses for a campaign milestone.
 * - LOCKED: not yet reached
 * - CURRENT: the active milestone being worked towards
 * - REACHED: goal amount has been met
 */
export const MilestoneStatus = {
  LOCKED: 'LOCKED',
  CURRENT: 'CURRENT',
  REACHED: 'REACHED',
} as const;
export type MilestoneStatus = typeof MilestoneStatus[keyof typeof MilestoneStatus];

/**
 * A single line item within a budget section.
 */
export interface BudgetItemDto {
  /** Unique identifier (UUID). */
  id: string;
  /** Human-readable label for the item. */
  label: string;
  /** Amount in euros (cents precision on backend, stored as number here). */
  amount: number;
  /** Display order within the section. */
  sortOrder: number;
}

/**
 * A budget section grouping related budget items on one side of the budget.
 */
export interface BudgetSectionDto {
  /** Unique identifier (UUID). */
  id: string;
  /** Which side of the budget this section belongs to. */
  side: BudgetSide;
  /** Short code identifying the section (e.g. "PERSONNEL"). */
  code: string;
  /** Human-readable name of the section. */
  name: string;
  /** Display order among sections. */
  sortOrder: number;
  /** Line items belonging to this section. */
  items: BudgetItemDto[];
}

/**
 * A milestone marking a fundraising threshold or achievement.
 */
export interface MilestoneDto {
  /** Unique identifier (UUID). */
  id: string;
  /** Emoji representing the milestone. */
  emoji: string;
  /** Short title of the milestone. */
  title: string;
  /** Impact description — what this milestone enables for donors. */
  description: string | null;
  /** Editorial transparency commitment (how proof will be shared). */
  transparencyCommitment: string | null;
  /** Amount in euros at which this milestone is triggered. */
  targetAmount: number;
  /** Current status of this milestone. */
  status: MilestoneStatus;
  /** Display order among milestones. */
  sortOrder: number;
  /** ISO-8601 timestamp when the milestone was reached, or null. */
  reachedAt: string | null;
  /** ISO-8601 creation timestamp. */
  createdAt: string;
}

/**
 * Full campaign data including budget and milestones.
 */
export interface CampaignDto {
  /** Unique identifier (UUID). */
  id: string;
  /** Campaign name. */
  name: string;
  /** Emoji representing the campaign. */
  emoji: string;
  /** Optional campaign description. */
  description: string | null;
  /** Fundraising goal in euros. */
  goal: number;
  /** Amount raised so far in euros. */
  raised: number;
  /** Current status of the campaign. */
  status: CampaignStatus;
  /** ISO date when the campaign starts, or null. */
  startDate: string | null;
  /** ISO date when the campaign ends, or null. */
  endDate: string | null;
  /** Cause of the campaign, or null until chosen. */
  category: CampaignCause | null;
  /** Place of action, or null until set (recommended, not required to publish). */
  actionPlace: ActionPlaceDto | null;
  /** Why the association is launching this campaign, or null. */
  reason: string | null;
  /** Concrete expected outcomes, or null. */
  impactGoals: string | null;
  /** URL or path of the cover image, or null. */
  coverImage: string | null;
  /** Budget sections (charges and produits). */
  budgetSections: BudgetSectionDto[];
  /** Campaign milestones. */
  milestones: MilestoneDto[];
  /** ISO-8601 creation timestamp. */
  createdAt: string;
  /** ISO-8601 last update timestamp. */
  updatedAt: string;
}

/**
 * Lightweight campaign summary used in the list view.
 */
export interface CampaignSummaryDto {
  /** Unique identifier (UUID). */
  id: string;
  /** Campaign name. */
  name: string;
  /** Emoji representing the campaign. */
  emoji: string;
  /** Optional campaign description. */
  description: string | null;
  /** Fundraising goal in euros. */
  goal: number;
  /** Amount raised so far in euros. */
  raised: number;
  /** Current status of the campaign. */
  status: CampaignStatus;
  /** ISO date when the campaign starts, or null. */
  startDate: string | null;
  /** ISO date when the campaign ends, or null. */
  endDate: string | null;
  /** Total number of milestones defined for this campaign. */
  milestoneCount: number;
  /** URL or path of the cover image, or null. */
  coverImage: string | null;
  /** ISO-8601 creation timestamp. */
  createdAt: string;
  /** ISO-8601 last update timestamp — pass to `campaignCoverUrl` as the cache-busting version. */
  updatedAt: string;
}

/**
 * Payload for `POST /api/association/campaigns`.
 */
export interface CreateCampaignRequest {
  /** Campaign name (required). */
  name: string;
  /** Emoji representing the campaign. */
  emoji?: string;
  /** Optional description. */
  description?: string;
  /** Fundraising goal in euros. */
  goal?: number;
  /** ISO date string for campaign start. */
  startDate?: string;
  /** ISO date string for campaign end. */
  endDate?: string;
}

/**
 * Payload for `PUT /api/association/campaigns/:id`.
 */
export interface UpdateCampaignRequest {
  /** Campaign name. */
  name?: string;
  /** Emoji representing the campaign. */
  emoji?: string;
  /** Campaign description. */
  description?: string;
  /** Fundraising goal in euros. */
  goal?: number;
  /** Campaign status. */
  status?: CampaignStatus;
  /** ISO date string for campaign start. */
  startDate?: string;
  /** ISO date string for campaign end. */
  endDate?: string;
  /** Cause of the campaign. */
  category?: CampaignCause;
  /** Place of action; validated and resolved server-side. */
  actionPlace?: ActionPlaceRequest;
  /** Why the association is launching this campaign. */
  reason?: string;
  /** Concrete expected outcomes. */
  impactGoals?: string;
  /** URL or path of the cover image. */
  coverImage?: string;
}

/**
 * Payload for `PUT /api/association/campaigns/:id/budget`.
 */
export interface SaveBudgetRequest {
  /** Full list of sections to save (replaces existing budget). */
  sections: SaveBudgetSectionRequest[];
}

/**
 * A budget section within a {@link SaveBudgetRequest}.
 */
export interface SaveBudgetSectionRequest {
  /** Which side of the budget. */
  side: BudgetSide;
  /** Short section code. */
  code: string;
  /** Human-readable section name. */
  name: string;
  /** Display order. */
  sortOrder: number;
  /** Line items for this section. */
  items: SaveBudgetItemRequest[];
}

/**
 * A budget line item within a {@link SaveBudgetSectionRequest}.
 */
export interface SaveBudgetItemRequest {
  /** Human-readable label. */
  label: string;
  /** Amount in euros. */
  amount: number;
  /** Display order within the section. */
  sortOrder: number;
}

/**
 * Payload for `POST /api/association/campaigns/:id/milestones`.
 */
export interface CreateMilestoneRequest {
  /** Milestone title (required). */
  title: string;
  /** Emoji for the milestone. */
  emoji?: string;
  /** Optional longer description. */
  description?: string;
  /** Amount in euros at which this milestone is triggered. */
  targetAmount?: number;
  /** Display order among milestones. */
  sortOrder?: number;
}

/**
 * Payload for `PUT /api/association/campaigns/:id/milestones/:milestoneId`.
 */
export interface UpdateMilestoneRequest {
  /** Milestone title. */
  title?: string;
  /** Emoji for the milestone. */
  emoji?: string;
  /** Impact description — what this milestone enables for donors. */
  description?: string;
  /** Editorial transparency commitment (how proof will be shared). */
  transparencyCommitment?: string;
  /** Amount in euros at which this milestone is triggered. */
  targetAmount?: number;
  /** Milestone status. */
  status?: MilestoneStatus;
  /** Display order among milestones. */
  sortOrder?: number;
}

/**
 * Payload for `PUT /api/association/campaigns/:id/milestones/reorder`.
 */
export interface ReorderMilestonesRequest {
  /** Ordered list of milestone UUIDs defining the new sort order. */
  milestoneIds: string[];
}

/** Payload for `PUT /api/association/campaigns/:id/story`. */
export interface UpsertCampaignStoryRequest {
  /** Sanitized rich-text HTML. */
  storyText: string;
  /** Plain text, max 220 chars -- what the impact gallery and the share card render. */
  storySummary: string;
  /** One-directional: `false` never unpublishes an already-published story. */
  publish?: boolean;
}

/** One uploaded story image -- what `RichTextEditor` inserts as `<img src>`. */
export interface CampaignStoryImageDto {
  id: string;
  url: string;
}
