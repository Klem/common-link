/**
 * Possible statuses for a fundraising campaign.
 * - DRAFT: not yet published, only visible to the association
 * - LIVE: published and accepting donations
 * - ENDED: closed, no longer accepting donations
 */
export const CampaignStatus = {
  DRAFT: 'DRAFT',
  LIVE: 'LIVE',
  ENDED: 'ENDED',
} as const;
export type CampaignStatus = typeof CampaignStatus[keyof typeof CampaignStatus];

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
  /** Optional longer description. */
  description: string | null;
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
  /** On-chain contract address once deployed, or null. */
  contractAddress: string | null;
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
  /** ISO-8601 creation timestamp. */
  createdAt: string;
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
  /** On-chain contract address. */
  contractAddress?: string;
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
  /** Longer description. */
  description?: string;
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
