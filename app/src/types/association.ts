/** KYC verification lifecycle states for an association. */
export const VerificationStatus = {
  UNVERIFIED: 'UNVERIFIED',
  PENDING: 'PENDING',
  VERIFIED: 'VERIFIED',
  REJECTED: 'REJECTED',
} as const;
export type VerificationStatus = typeof VerificationStatus[keyof typeof VerificationStatus];

/**
 * Compliance status of an association (IC-44 — canal de signalement de campagne).
 *
 * `ACTIVE` is normal. `ALERT` means a campaign report is open and awaiting compliance review —
 * internal only, does not gate donations or public visibility. `SUSPENDED` means a report was
 * confirmed founded and blocks every campaign of this association from accepting donations.
 */
export const AssociationStatus = {
  ACTIVE: 'ACTIVE',
  ALERT: 'ALERT',
  SUSPENDED: 'SUSPENDED',
} as const;
export type AssociationStatus = typeof AssociationStatus[keyof typeof AssociationStatus];

/**
 * Visual palette of an association's donation landing page.
 *
 * Single source of truth for both surfaces: the settings tab and the public landing page
 * (`@/lib/api/public` re-exports this). Entries mirror the Kotlin `LandingTheme` enum and the
 * `chk_association_landing_theme` CHECK constraint.
 */
export const LandingTheme = {
  DEFAULT: 'DEFAULT',
  WARM: 'WARM',
  TRUST: 'TRUST',
  NATURE: 'NATURE',
  SOBER: 'SOBER',
} as const;
export type LandingTheme = (typeof LandingTheme)[keyof typeof LandingTheme];

/** Every palette, in the order shown in the settings tab. */
export const LANDING_THEMES: readonly LandingTheme[] = [
  LandingTheme.DEFAULT,
  LandingTheme.WARM,
  LandingTheme.TRUST,
  LandingTheme.NATURE,
  LandingTheme.SOBER,
];

/**
 * Read model for an association's profile as returned by `GET /api/association/me`.
 */
export interface AssociationProfileDto {
  /** Unique profile identifier (UUID). */
  id: string;
  /** Official name of the association. */
  name: string;
  /** RNA / official registration identifier (W-number for JOAFE registrations, or SIREN for legacy). */
  identifier: string;
  city: string | null;
  postalCode: string | null;
  contactName: string | null;
  description: string | null;
  /** SIREN number (optional secondary identifier, nullable). */
  siren: string | null;
  creationYear: number | null;
  contactEmail: string | null;
  phone: string | null;
  /** KYC verification status replacing the former `verified` boolean. */
  verificationStatus: VerificationStatus;
  verificationRejectionReason: string | null;
  /** Public opaque widget token (e.g. `clk_…`), null if not generated yet. */
  widgetToken: string | null;
  /** UUID of the campaign configured as widget destination, null if not set. */
  widgetDestinationCampaignId: string | null;
  /** Allowed origin for post-payment redirects (scheme+host), null if not configured. */
  widgetAllowedOrigin: string | null;
  /** Full street address of the registered office. Null if not yet filled. */
  addressLine1: string | null;
  /** Official purpose / objet social. Null if not yet filled. */
  legalObject: string | null;
  /** Full name of the authorised receipt signer. Null if not yet filled. */
  signerName: string | null;
  /** Role/title of the authorised signer. Null if not yet filled. */
  signerRole: string | null;
  /** Visual palette of the donation landing page. */
  landingTheme: LandingTheme;
  /** Public serving path of the landing logo, null if none was uploaded. */
  landingLogo: string | null;
  /** Google Tag Manager container ID for Ad Grants tracking. Null if not configured. */
  gtmContainerId: string | null;
}

/**
 * Payload for `PATCH /api/association/me/landing`.
 * Every field is optional — an omitted field leaves the stored value untouched.
 */
export interface UpdateLandingConfigRequest {
  theme?: LandingTheme;
  /**
   * Google Tag Manager container ID (e.g. `GTM-XXXXXXX`). Omitted/undefined leaves the current
   * value unchanged; an empty string clears it.
   */
  gtmContainerId?: string;
}

/** Response shape for `POST /api/association/me/landing/preview-session`. */
export interface LandingPreviewToken {
  previewToken: string;
  /** ISO instant at which the token stops working. */
  expiresAt: string;
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
  siren?: string;
  creationYear?: number;
  contactEmail?: string;
  phone?: string;
  /** UUID of the campaign to set as widget donation destination, or null to unset. */
  widgetDestinationCampaignId?: string | null;
  /** Full street address of the registered office. */
  addressLine1?: string;
  /** Official purpose / objet social. */
  legalObject?: string;
  /** Full name of the authorised receipt signer. */
  signerName?: string;
  /** Role/title of the authorised signer (e.g. "Trésorier"). */
  signerRole?: string;
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
