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
}

export const getLanding = (token: string): Promise<PublicLandingDto> =>
  publicApi.get<PublicLandingDto>(`/api/public/landing/${token}`).then((r) => r.data);
