import api from '@/lib/api';
import type { CreatePayoutRequest, Page, PayoutDto, PayoutSummaryDto } from '@/types/payment';

const base = (campaignId: string) => `/api/campaigns/${campaignId}/payments`;

/**
 * Creates a new PENDING payout for a campaign.
 * Calls `POST /api/campaigns/{campaignId}/payments`.
 */
export const createPayment = (
  campaignId: string,
  data: CreatePayoutRequest,
): Promise<PayoutDto> =>
  api.post<PayoutDto>(base(campaignId), data).then((r) => r.data);

/**
 * Transitions a PENDING payout to CONFIRMED and enqueues an on-chain job.
 * Calls `POST /api/campaigns/{campaignId}/payments/{payoutId}/confirm`.
 */
export const confirmPayment = (campaignId: string, payoutId: string): Promise<PayoutDto> =>
  api.post<PayoutDto>(`${base(campaignId)}/${payoutId}/confirm`).then((r) => r.data);

/**
 * Returns a paginated list of payouts for a campaign, newest first.
 * Calls `GET /api/campaigns/{campaignId}/payments`.
 */
export const listPayments = (
  campaignId: string,
  page = 0,
  size = 20,
): Promise<Page<PayoutDto>> =>
  api
    .get<Page<PayoutDto>>(base(campaignId), { params: { page, size } })
    .then((r) => r.data);

/**
 * Returns aggregated KPIs for the Payments tab.
 * Calls `GET /api/campaigns/{campaignId}/payments/summary`.
 */
export const getPaymentSummary = (campaignId: string): Promise<PayoutSummaryDto> =>
  api.get<PayoutSummaryDto>(`${base(campaignId)}/summary`).then((r) => r.data);
