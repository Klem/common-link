import api from '@/lib/api';
import type { PaymentStatusResponse } from '@/types/paymentProvider';

/** Options of the payment-status calls. */
export interface PaymentStatusRequestOptions {
  /** Background call the user did not trigger: no global error toast (see `silentErrors` in `lib/api.ts`). */
  silent?: boolean;
}

/**
 * Reads the association's provider-neutral payment status (`GET /api/association/payment/status`). The server
 * refreshes a stale status from the provider first (at most once every 5 minutes) and always answers with the
 * stored status.
 *
 * @param options `silent` for automatic re-reads (no error toast).
 */
export const getPaymentStatus = (options?: PaymentStatusRequestOptions): Promise<PaymentStatusResponse> =>
  api
    .get<PaymentStatusResponse>('/api/association/payment/status', { silentErrors: options?.silent === true })
    .then((r) => r.data);
