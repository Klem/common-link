import { isAxiosError } from 'axios';
import api from '@/lib/api';
import type {
  HelloAssoConnectRequest,
  HelloAssoVerifyRequest,
  HelloAssoVerifyResponse,
} from '@/types/helloasso';

/**
 * HelloAsso link calls of the "Encaissement des dons" tab (`/api/association/helloasso/*`, role ASSOCIATION).
 *
 * The link state itself is read through the neutral `GET /api/association/payment/status` (`helloasso` field), never
 * here. Calls whose failure the screen explains itself are sent with `silentErrors` (no global toast).
 * Never log an error of [verifyCredentials]: its request config carries the client secret.
 */

/**
 * Creates the association's notification URL if it does not exist yet, and returns it (idempotent; the token never
 * changes). Called when the "Relier" screen opens without a `notificationUrl` in the status.
 */
export const ensureNotificationUrl = (): Promise<string> =>
  api
    .post<{ notificationUrl: string }>('/api/association/helloasso/notification-url', null, { silentErrors: true })
    .then((r) => r.data.notificationUrl);

/**
 * Checks the association's API keys with HelloAsso and compares the organization with the CommonLink KYB. Nothing is
 * persisted. Silent: the screen shows the outcome. Pass `signal` to cancel a superseded check.
 *
 * @param keys client id and client secret, trimmed.
 * @param signal aborts the request when the user edits the codes again.
 */
export const verifyCredentials = (keys: HelloAssoVerifyRequest, signal?: AbortSignal): Promise<HelloAssoVerifyResponse> =>
  api
    .post<HelloAssoVerifyResponse>('/api/association/helloasso/credentials/verify', keys, { silentErrors: true, signal })
    .then((r) => r.data);

/** Links the verified HelloAsso account with the accepted mandate (204). Silent: refusals are shown on the screen. */
export const linkAccount = (request: HelloAssoConnectRequest): Promise<void> =>
  api.post('/api/association/helloasso/connection', request, { silentErrors: true }).then(() => undefined);

/**
 * Donations HelloAsso has not paid out yet, every campaign (`GET /api/association/helloasso/disconnect-preview`), shown
 * in the disconnection warning: once disconnected they can no longer be tracked. Silent: on failure the warning is
 * simply not shown, the disconnection stays possible.
 */
export const getDisconnectPreview = (): Promise<{ notYetPaidOutCount: number; notYetPaidOutAmount: number }> =>
  api
    .get<{ notYetPaidOutCount: number; notYetPaidOutAmount: number }>('/api/association/helloasso/disconnect-preview', { silentErrors: true })
    .then((r) => r.data);

/** Disconnects the HelloAsso account: our tokens are erased, donation buttons pause (204, idempotent). */
export const disconnect = (): Promise<void> =>
  api.delete('/api/association/helloasso/connection').then(() => undefined);

/** Re-reads HelloAsso eligibility now (204; 429 when asked again within a minute). Silent: the screen shows a 429. */
export const refreshEligibility = (): Promise<void> =>
  api.post('/api/association/helloasso/eligibility/refresh', null, { silentErrors: true }).then(() => undefined);

/** Records that the association started the "no HelloAsso account yet" journey (D+1 / D+7 reminders). Best effort. */
export const markOnboardingStarted = (): Promise<void> =>
  api.post('/api/association/helloasso/onboarding-started', null, { silentErrors: true }).then(() => undefined);

/**
 * `code` property of a HelloAsso refusal (ProblemDetail, compare with [HelloAssoErrorCode]), null for any other error.
 *
 * @param error rejection of one of the calls above.
 */
export function helloAssoErrorCode(error: unknown): string | null {
  if (!isAxiosError(error)) return null;
  const code = (error.response?.data as { code?: unknown } | undefined)?.code;
  return typeof code === 'string' ? code : null;
}

/** HTTP status of a rejection, null when the request never got an answer (network error, cancelled). */
export function httpStatusOf(error: unknown): number | null {
  return isAxiosError(error) ? error.response?.status ?? null : null;
}
