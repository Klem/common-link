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
}

export interface CreateGuestDonationRequest {
  amount: number;
  donorEmail: string;
  donorFullName: string;
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
}

export const getDonationStatus = (paymentId: string): Promise<DonationStatusResponse> =>
  publicApi
    .get<DonationStatusResponse>(`/api/public/widget/donations/${paymentId}/status`)
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

/** Visual palette of a landing page. Mirrors the Kotlin `LandingTheme` enum. */
export const LandingTheme = {
  DEFAULT: 'DEFAULT',
  WARM: 'WARM',
  TRUST: 'TRUST',
  NATURE: 'NATURE',
  SOBER: 'SOBER',
} as const;
export type LandingTheme = (typeof LandingTheme)[keyof typeof LandingTheme];

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
  coverImage: string | null;
  budget: LandingBudgetPostDto[];
  budgetHash: string | null;
  milestones: MilestoneDto[];
  widgetAllowedOrigin: string | null;
  /** Palette chosen by the association; drives the `--lp-*` token overrides. */
  landingTheme: LandingTheme;
  /** Public serving path of the association logo, null when none was uploaded. */
  landingLogo: string | null;
  showProject: boolean;
  showTransparency: boolean;
  showTrust: boolean;
  /**
   * False only in preview mode on a campaign that is not LIVE: the donation endpoint would refuse
   * the payment, so the form must be rendered disabled rather than fail on submit.
   */
  donationsEnabled: boolean;
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
