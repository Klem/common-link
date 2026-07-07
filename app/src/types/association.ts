/**
 * Read model for an association's profile as returned by `GET /api/association/me`.
 * The `verified` flag indicates whether the association has been verified on-chain.
 */
export interface AssociationProfileDto {
  /** Unique profile identifier (UUID). */
  id: string;
  /** Official name of the association. */
  name: string;
  /** SIREN / official registration identifier. */
  identifier: string;
  city: string | null;
  postalCode: string | null;
  contactName: string | null;
  description: string | null;
  /** Whether the association has received on-chain verification. */
  verified: boolean;
}

/**
 * Payload for `PATCH /api/association/me`.
 * All fields are optional — only provided fields are updated.
 */
export interface UpdateAssociationProfileRequest {
  contactName?: string;
  city?: string;
  postalCode?: string;
  description?: string;
}

/** Activity event types surfaced in the dashboard recent-activity feed. */
export const ActivityType = {
  DONATION: 'DONATION',
  MILESTONE_REACHED: 'MILESTONE_REACHED',
  PAYMENT: 'PAYMENT',
} as const;
export type ActivityType = (typeof ActivityType)[keyof typeof ActivityType];

/** One monthly bucket in the 6-month donations chart. */
export interface MonthlyPoint {
  /** ISO year-month string, e.g. "2026-01". */
  month: string;
  amount: number;
}

/** One entry in the recent activity feed. */
export interface ActivityItem {
  type: ActivityType;
  /** Human-readable label, e.g. "Marie L. a donné 50€". */
  label: string;
  amount?: number;
  occurredAt: string;
}

/** Closest upcoming milestone across all LIVE campaigns. */
export interface NextMilestoneInfo {
  label: string;
  remainingAmount: number;
}

/** Response shape for `GET /api/association/dashboard`. */
export interface DashboardStats {
  totalRaisedActive: number;
  activeCampaignCount: number;
  nextMilestone: NextMilestoneInfo | null;
  /** Average raised/goal ratio across LIVE campaigns, 0.0–1.0. */
  avgProgress: number;
  donations6Months: MonthlyPoint[];
  recentActivity: ActivityItem[];
}
