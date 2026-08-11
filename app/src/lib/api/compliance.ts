import api from '@/lib/api';
import type { Page } from '@/types/payment';
import type {
  ComplianceAlertSummaryDto,
  ComplianceAlertDetailDto,
  CloseAlertRequest,
} from '@/types/compliance';

/**
 * Lists freeze-hit compliance alerts, most recent first.
 * `GET /api/compliance/alerts?page=0&size=20`
 */
export const listAlerts = (page = 0, size = 20): Promise<Page<ComplianceAlertSummaryDto>> =>
  api
    .get<Page<ComplianceAlertSummaryDto>>('/api/compliance/alerts', { params: { page, size } })
    .then((r) => r.data);

/**
 * Fetches the full detail of a compliance alert, including freeze-screening history.
 * `GET /api/compliance/alerts/{alertId}`
 */
export const getAlert = (alertId: string): Promise<ComplianceAlertDetailDto> =>
  api.get<ComplianceAlertDetailDto>(`/api/compliance/alerts/${alertId}`).then((r) => r.data);

/**
 * Takes a compliance alert in charge (PENDING → IN_REVIEW).
 * `POST /api/compliance/alerts/{alertId}/take-in-charge`
 */
export const takeInCharge = (alertId: string): Promise<ComplianceAlertSummaryDto> =>
  api
    .post<ComplianceAlertSummaryDto>(`/api/compliance/alerts/${alertId}/take-in-charge`)
    .then((r) => r.data);

/**
 * Closes a compliance alert with a decision and mandatory rationale.
 * Treasury fields are mandatory when decision = SUSPICIOUS.
 * `POST /api/compliance/alerts/{alertId}/close`
 */
export const closeAlert = (
  alertId: string,
  request: CloseAlertRequest,
): Promise<ComplianceAlertSummaryDto> =>
  api
    .post<ComplianceAlertSummaryDto>(`/api/compliance/alerts/${alertId}/close`, request)
    .then((r) => r.data);
