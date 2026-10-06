import type { Page } from '@/types/payment';
import type { BudgetVariance } from '@/types/reporting';
import type { CampaignCause, CampaignStatus, MilestoneDto } from '@/types/campaign';

/**
 * Read model for a donor's profile as returned by `GET`/`PATCH /api/donor/me`.
 */
export interface DonorProfileDto {
  /** Unique profile identifier (UUID). */
  id: string;
  /** Civil first name. Distinct from `displayName`, the public pseudonym. */
  firstName: string | null;
  /** Civil last name. */
  lastName: string | null;
  /** Optional public display name chosen by the donor. */
  displayName: string | null;
  /** When true, the donor's identity is hidden from public donation records. */
  anonymous: boolean;
  /** Whether the donor receives the monthly impact report by email. */
  notifyMonthlyReport: boolean;
  /** Whether the donor is notified when a supported association publishes a payout. */
  notifyNewPayout: boolean;
  /** Whether the donor is notified when a supported campaign reaches its goal. */
  notifyGoalReached: boolean;
  /** Whether the donor accepts campaign suggestions by email. */
  notifySuggestions: boolean;
}

/**
 * Payload for `PATCH /api/donor/me`.
 * All fields are optional — only provided fields are updated.
 */
export interface UpdateDonorProfileRequest {
  firstName?: string;
  lastName?: string;
  displayName?: string;
  anonymous?: boolean;
  notifyMonthlyReport?: boolean;
  notifyNewPayout?: boolean;
  notifyGoalReached?: boolean;
  notifySuggestions?: boolean;
}

/** One row of the donor's donation history. */
export interface DonorDonationDto {
  id: string;
  /** ISO instant — the donation's `confirmedAt`. */
  donatedAt: string;
  amount: number;
  campaignId: string;
  campaignName: string;
  campaignEmoji: string;
  associationId: string;
  associationName: string;
  /** Whether a receipt has been generated for this donation. */
  receiptAvailable: boolean;
  /** Internal Cerfa number, for display only — never used as a URL segment. */
  receiptNumber: string | null;
  /** Portion of this donation already allocated (FIFO) to a confirmed payout. */
  usedAmount: number;
  /** Portion of this donation not yet allocated to any confirmed payout. */
  remainingAmount: number;
}

/** Headline figures of the "My donations" page. */
export interface DonorStatsDto {
  totalDonated: number;
  donationCount: number;
  associationCount: number;
  /** Estimate only — never a guaranteed amount. See §2.7 of the sprint spec. */
  estimatedTaxReduction: number;
}

/** An association offered in the donation history filter. */
export interface AssociationOptionDto {
  id: string;
  name: string;
}

/** Values available in the history filter selectors. */
export interface DonorDonationFiltersDto {
  associations: AssociationOptionDto[];
  years: number[];
}

/**
 * Coarse campaign-activity signal for a supported association's "freshness-tag" (sprint 5, L18).
 * Mirrors the backend `DonorCampaignStatus` enum.
 */
export const DonorCampaignStatus = {
  LIVE: 'LIVE',
  COMPLETED: 'COMPLETED',
  NONE: 'NONE',
} as const;
export type DonorCampaignStatus = typeof DonorCampaignStatus[keyof typeof DonorCampaignStatus];

/** A supported association, as shown on the "My associations" page. */
export interface DonorAssociationDto {
  associationId: string;
  name: string;
  /** Category of the campaign the donor funded most recently for this association. */
  category: CampaignCause | null;
  totalDonated: number;
  /** Confirmed payouts of the association, all campaigns combined. */
  publishedPayoutCount: number;
  campaignCount: number;
  /** ISO instant, or null if never donated (should not happen in practice). */
  lastDonationAt: string | null;
  /**
   * Absolute public donation URL, or null when the association's widget isn't currently
   * reachable. Points at the association's *current* live campaign, not necessarily the one the
   * donor funded — never render a link when null.
   */
  donationUrl: string | null;
  /** Coarse activity signal for the association's current widget-destination campaign. */
  campaignStatus: DonorCampaignStatus;
  /** Id of the campaign `campaignStatus` describes, or null when `campaignStatus` is `NONE`. */
  campaignId: string | null;
  /** Name of the campaign `campaignStatus` describes, or null when `campaignStatus` is `NONE`. */
  campaignName: string | null;
}

/** Parameters accepted by `GET /api/donor/me/donations`. */
export interface DonorDonationsQuery {
  page?: number;
  size?: number;
  associationId?: string;
  year?: number;
}

export type DonorDonationsPage = Page<DonorDonationDto>;

/** One of the 4 steps of a donation's journey — see `dashboard.donor.journey.*`. */
export const JourneyStep = {
  RECEIVED: 'RECEIVED',
  RECORDED: 'RECORDED',
  SPENT: 'SPENT',
  IMPACT_REPORTED: 'IMPACT_REPORTED',
} as const;
export type JourneyStep = (typeof JourneyStep)[keyof typeof JourneyStep];

/** Status of a single step in the donation journey timeline. */
export interface JourneyStepDto {
  step: JourneyStep;
  reached: boolean;
  reachedAt: string | null;
}

/** Portion of one confirmed payout funded by a given donation — FIFO allocation share. */
export interface FundedPayoutShareDto {
  payoutId: string;
  label: string;
  amountImputed: number;
  confirmedAt: string;
}

/**
 * The 4-step journey of one donation, for the "parcours de votre don" timeline and the
 * traceability modal.
 *
 * `usedAmount`/`remainingAmount`/`fundedPayouts` mirror the same fields already present on
 * `DonorDonationDto` — repeated here so the traceability modal (opened from a single donation,
 * without necessarily having the full history page in memory) is self-sufficient.
 */
export interface DonorDonationJourneyDto {
  donationId: string;
  steps: JourneyStepDto[];
  /** For the "précédent / suivant" navigation between the donor's own donations. */
  previousDonationId: string | null;
  nextDonationId: string | null;
  usedAmount: number;
  remainingAmount: number;
  fundedPayouts: FundedPayoutShareDto[];
}

/** One confirmed payout line of a campaign, as shown in the donor's "bilan de campagne". */
export interface CampaignPayoutLineDto {
  payoutId: string;
  label: string;
  amount: number;
  payeeName: string;
  confirmedAt: string;
  /** Budget section code this payout belongs to — matches `variance.charges[].sectionCode`. */
  sectionCode: string;
}

/** One of the viewing donor's own donations that helped fund a payout (Sprint 6). */
export interface PayoutFundingLineDto {
  donationId: string;
  confirmedAt: string;
  amount: number;
}

/**
 * Per-payout funding breakdown ("Voir la répartition"), from
 * `GET /api/donor/me/campaigns/{campaignId}/payouts/{payoutId}/breakdown` — the inverse of
 * `FundedPayoutShareDto`: for one payout, which of the viewing donor's own donations funded it.
 *
 * Privacy: `myLines` is always full detail (the viewing donor's own money). Every other donor's
 * contribution is folded into `othersTotal` (the amount), which is `null` when fewer than 3 other
 * donations contributed — below that floor, even the aggregate would reveal (or let a viewer
 * deduce by subtraction) an individual donor's exact amount.
 *
 * `othersDonationCount` is **always** populated, including `0` — a count carries no monetary
 * information, so it's never withheld. This is what distinguishes "no other donor at all"
 * (`othersDonationCount === 0`) from "some did, but too few to show safely"
 * (`othersDonationCount` in 1..2, `othersTotal === null`) — never render an amount when
 * `othersTotal` is null, but the "funded by others too" message is only accurate when
 * `othersDonationCount > 0`.
 */
export interface PayoutFundingBreakdownDto {
  payoutId: string;
  payoutLabel: string;
  payoutAmount: number;
  myLines: PayoutFundingLineDto[];
  myTotal: number;
  othersTotal: number | null;
  othersDonationCount: number;
}

/** The donor-facing "bilan de campagne" page, from `GET /api/donor/me/campaigns/{id}/report`. */
export interface DonorCampaignReportDto {
  campaignId: string;
  campaignName: string;
  campaignEmoji: string;
  associationName: string;
  status: CampaignStatus;
  goal: number;
  raised: number;
  /** This donor's own total confirmed amount on this campaign — never a share of others'. */
  donorContribution: number;
  milestones: MilestoneDto[];
  confirmedPayouts: CampaignPayoutLineDto[];
  variance: BudgetVariance;
  /** Static, validated D2 wording — never build this string ad hoc in the frontend. */
  registryBannerText: string;
  /** Null = not yet published — the front renders the "not yet published" placeholder (D5). */
  story: CampaignStoryDto | null;
}

/** One year's fiscal recap, from `GET /api/donor/me/receipts`. */
export interface DonorReceiptYearDto {
  year: number;
  donationCount: number;
  totalAmount: number;
  /** Estimate only — never a guaranteed amount, same caveat as `DonorStatsDto.estimatedTaxReduction`. */
  estimatedDeduction: number;
}

/**
 * A campaign's impact story, association-authored, free text only (D5). Shared by the donor's
 * "bilan de campagne" (published only) and the association's own story editor (draft included).
 */
export interface CampaignStoryDto {
  /** Sanitized rich-text HTML — safe to render with `dangerouslySetInnerHTML`. */
  storyText: string;
  /** Plain text, no HTML — what the impact gallery and the share card render. */
  storySummary: string;
  /** ISO instant, or null when still draft — never shown to the donor. */
  publishedAt: string | null;
}

/** One campaign card of the "Impact de mes dons" gallery, from `GET /api/donor/me/impacts`. */
export interface DonorImpactDto {
  campaignId: string;
  campaignName: string;
  campaignEmoji: string;
  associationName: string;
  /** Cause — the filter facet. Null when the campaign carries none. */
  category: CampaignCause | null;
  /** Free-text impact description written by the association at campaign creation. */
  impactGoals: string | null;
  /** Published story's plain-text summary, null if none or still draft. Never the rich-text HTML. */
  storySummary: string | null;
  /** Absolute public donation URL, or null when the association's widget isn't currently reachable. */
  donationUrl: string | null;
}

/** Kind of event surfaced by the donor engagement feed — see `dashboard.donor.engagement.feed.*`. */
export const DonorFeedItemType = {
  PAYOUT_CONFIRMED: 'PAYOUT_CONFIRMED',
  MILESTONE_REACHED: 'MILESTONE_REACHED',
  CAMPAIGN_COMPLETED: 'CAMPAIGN_COMPLETED',
} as const;
export type DonorFeedItemType = (typeof DonorFeedItemType)[keyof typeof DonorFeedItemType];

/**
 * One event of the donor's "Depuis votre dernière visite" home block, from
 * `GET /api/donor/me/feed`.
 */
export interface DonorFeedItemDto {
  type: DonorFeedItemType;
  campaignId: string;
  campaignName: string;
  associationName: string;
  /** ISO instant. */
  occurredAt: string;
  /**
   * Fully-resolved sentence built server-side — render as-is, never recompose a wording around a
   * payout event (D2-validated wording, never claims on-chain registration of the expense).
   */
  label: string;
}

/**
 * One recommended campaign for "Projets recommandés" and the home "Pour vous" block (D8, option A),
 * from `GET /api/donor/me/recommendations`. `donationUrl` is always non-null here.
 */
export interface DonorRecommendationDto {
  campaignId: string;
  campaignName: string;
  campaignEmoji: string;
  associationName: string;
  category: CampaignCause | null;
  coverImage: string | null;
  goal: number;
  raised: number;
  donationUrl: string;
  /** Non-null when explained by a cause the donor already funds — show the reason to the donor. */
  matchedCategory: CampaignCause | null;
}
