import { isAxiosError } from 'axios';
import api from '@/lib/api';
import { FundsErrorCode } from '@/types/campaignFunds';
import type { CampaignFunds, CashOutReceiptResponse, FundsNotAvailableBody } from '@/types/campaignFunds';

/**
 * Funds calls of the Payments tab (role ASSOCIATION). All silent (`silentErrors`): the tab explains every failure
 * itself, and the funds are re-read in the background (tab opening, after a payment) where a generic toast would be
 * noise.
 */

const base = (campaignId: string) => `/api/association/campaigns/${campaignId}/funds`;

/** Funds of the campaign, no side effect. `GET /api/association/campaigns/{campaignId}/funds`. */
export const getCampaignFunds = (campaignId: string): Promise<CampaignFunds> =>
  api.get<CampaignFunds>(base(campaignId), { silentErrors: true }).then((r) => r.data);

/**
 * Asks HelloAsso for news then returns the funds (`POST …/funds/refresh`). The server allows one pass per association
 * per interval and always answers 200: a request over the limit gets the known state and `nextRefreshAvailableAt`.
 */
export const refreshCampaignFunds = (campaignId: string): Promise<CampaignFunds> =>
  api.post<CampaignFunds>(`${base(campaignId)}/refresh`, null, { silentErrors: true }).then((r) => r.data);

/**
 * "Transfer received" for HelloAsso payout [idCashOut] (`POST /api/association/cash-outs/{idCashOut}/receipt`). The
 * server re-reads every donation at HelloAsso; refusals carry a `code` (see {@link fundsErrorCode}).
 */
export const confirmCashOutReceipt = (idCashOut: number): Promise<CashOutReceiptResponse> =>
  api
    .post<CashOutReceiptResponse>(`/api/association/cash-outs/${idCashOut}/receipt`, null, { silentErrors: true })
    .then((r) => r.data);

/**
 * `code` of a funds refusal (ProblemDetail), compare with {@link FundsErrorCode}; null for any other error.
 *
 * @param error rejection of a call above or of a payout creation / confirmation.
 */
export function fundsErrorCode(error: unknown): string | null {
  if (!isAxiosError(error)) return null;
  const code = (error.response?.data as { code?: unknown } | undefined)?.code;
  return typeof code === 'string' ? code : null;
}

/** Body of a `409 FUNDS_NOT_AVAILABLE` payout refusal, null for any other error. */
export function fundsNotAvailableBody(error: unknown): FundsNotAvailableBody | null {
  if (fundsErrorCode(error) !== FundsErrorCode.FUNDS_NOT_AVAILABLE || !isAxiosError(error)) return null;
  const data = error.response?.data as Partial<FundsNotAvailableBody>;
  return {
    code: FundsErrorCode.FUNDS_NOT_AVAILABLE,
    disbursable: Number(data.disbursable ?? 0),
    inTransit: Number(data.inTransit ?? 0),
    atProvider: Number(data.atProvider ?? 0),
  };
}
