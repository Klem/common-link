import axios from 'axios';

const rawApiUrl = process.env.NEXT_PUBLIC_API_URL || 'http://localhost:8080';
const apiBaseURL = rawApiUrl.startsWith('http') ? rawApiUrl : `https://${rawApiUrl}`;

/** Public (unauthenticated) Axios client — no withCredentials, no auth interceptor. */
const publicApi = axios.create({ baseURL: apiBaseURL });

export interface PublicWidgetDto {
  associationName: string;
  campaignId: string;
  campaignName: string;
  campaignEmoji: string;
  campaignDescription: string | null;
  goal: number;
  raised: number;
  campaignCoverImage: string | null;
  currency: string;
  widgetAllowedOrigin: string | null;
  /**
   * Amount the campaign may still accept (collection cap minus confirmed donations minus amounts
   * held by open payment sessions). 0 = the campaign is full. Mirror of
   * `DonationCapService.remainingCapacity`; the backend re-checks it on submit.
   */
  remainingCapacity: number;
  /** Google Tag Manager container ID for Ad Grants tracking. Null means no GTM injection. */
  gtmContainerId: string | null;
}

export interface CreateGuestDonationRequest {
  amount: number;
  donorEmail: string;
  donorFullName: string;
  /** Facultative (ISO yyyy-MM-dd) — utilisée pour le filtrage gel, jamais conservée. */
  donorBirthDate?: string;
  donorAddressLine1: string;
  donorAddressLine2?: string;
  donorPostalCode: string;
  donorCity: string;
  donorCountry: string;
  anonymousDisplay: boolean;
  consent: boolean;
  sourceSite?: string | null;
  /** UI locale (e.g. "fr", "en"). Used by the backend to build locale-prefixed redirect URLs. */
  locale?: string;
}

export interface CreateGuestDonationResponse {
  checkoutUrl: string;
  paymentId: string;
  /**
   * Opaque correlation id also embedded in the Mollie redirect URL (`ref` query param). Use it as
   * the GA4 `transaction_id` on the `begin_checkout` push made before redirecting, so it matches
   * the `purchase` push made on the return page.
   */
  publicRef: string;
}

export const getWidget = (token: string): Promise<PublicWidgetDto> =>
  publicApi.get<PublicWidgetDto>(`/api/public/widget/${token}`).then((r) => r.data);

export const createGuestDonation = (
  token: string,
  payload: CreateGuestDonationRequest,
): Promise<CreateGuestDonationResponse> =>
  publicApi
    .post<CreateGuestDonationResponse>(`/api/public/widget/${token}/donations`, payload)
    .then((r) => r.data);

export const DonationReturnStatus = {
  PENDING: 'PENDING',
  CONFIRMED: 'CONFIRMED',
} as const;
export type DonationReturnStatus = (typeof DonationReturnStatus)[keyof typeof DonationReturnStatus];

export interface DonationStatusResponse {
  status: DonationReturnStatus;
  /** Payment method used by the donor (e.g. `creditcard`, `banktransfer`). Set only when CONFIRMED. */
  method?: string;
}

/**
 * Polls donation confirmation status by the opaque `ref` carried on the Mollie return URL —
 * not the Mollie payment id, not any internal donor/campaign id.
 */
export const getDonationStatus = (ref: string): Promise<DonationStatusResponse> =>
  publicApi
    .get<DonationStatusResponse>(`/api/public/widget/donations/${ref}/status`)
    .then((r) => r.data);

export interface LandingBudgetPostDto {
  label: string;
  amount: number;
  percentage: number;
}

export interface MilestoneDto {
  id: string;
  title: string;
  transparencyCommitment: string | null;
  status: 'LOCKED' | 'CURRENT' | 'REACHED';
  sortOrder: number;
}

// Palette enum defined once in the domain types — the settings tab writes it, the landing reads it.
export { LandingTheme } from '@/types/association';
import type { LandingTheme } from '@/types/association';

export interface PublicLandingDto {
  associationName: string;
  associationRna: string;
  addressLine1: string | null;
  city: string | null;
  postalCode: string | null;
  legalObject: string | null;
  creationYear: number | null;
  taxReductionRate: number;
  campaignId: string;
  campaignName: string;
  campaignEmoji: string;
  campaignDescription: string | null;
  campaignReason: string | null;
  campaignImpactGoals: string | null;
  campaignCategory: string | null;
  goal: number;
  raised: number;
  currency: string;
  /** ISO 8601 (YYYY-MM-DD), null when the association left the calendrier unset. */
  startDate: string | null;
  endDate: string | null;
  coverImage: string | null;
  budget: LandingBudgetPostDto[];
  budgetHash: string | null;
  milestones: MilestoneDto[];
  widgetAllowedOrigin: string | null;
  /** Palette chosen by the association; drives the `--lp-*` token overrides. */
  landingTheme: LandingTheme;
  /** Public serving path of the association logo, null when none was uploaded. */
  landingLogo: string | null;
  /**
   * False only in preview mode on a campaign that is not LIVE: the donation endpoint would refuse
   * the payment, so the form must be rendered disabled rather than fail on submit.
   */
  donationsEnabled: boolean;
  /** Same figure and purpose as {@link PublicWidgetDto.remainingCapacity}. */
  remainingCapacity: number;
  /** Google Tag Manager container ID for Ad Grants tracking. Null means no GTM injection. */
  gtmContainerId: string | null;
}

/**
 * Fetches landing page data.
 *
 * @param token Public widget token.
 * @param preview Optional preview token; lets the owning association render a campaign that is not
 *   yet LIVE. Ignored by the backend when it does not belong to that association.
 */
export const getLanding = (token: string, preview?: string | null): Promise<PublicLandingDto> =>
  publicApi
    .get<PublicLandingDto>(`/api/public/landing/${token}`, {
      params: preview ? { preview } : undefined,
    })
    .then((r) => r.data);
