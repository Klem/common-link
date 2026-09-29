import type { Page } from '@/types/payment';

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

/** A supported association, as shown on the "My associations" page. */
export interface DonorAssociationDto {
  associationId: string;
  name: string;
  /** Category of the campaign the donor funded most recently for this association. */
  category: string | null;
  totalDonated: number;
  /** Confirmed payouts of the association, all campaigns combined. */
  publishedPayoutCount: number;
  campaignCount: number;
  /** ISO instant, or null if never donated (should not happen in practice). */
  lastDonationAt: string | null;
}

/** Parameters accepted by `GET /api/donor/me/donations`. */
export interface DonorDonationsQuery {
  page?: number;
  size?: number;
  associationId?: string;
  year?: number;
}

export type DonorDonationsPage = Page<DonorDonationDto>;
