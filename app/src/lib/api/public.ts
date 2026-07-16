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
