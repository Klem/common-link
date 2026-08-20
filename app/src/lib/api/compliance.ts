import api from '@/lib/api';
import type { Page } from '@/types/payment';
import type {
  AuditLogEntryDto,
  ComplianceAlertSummaryDto,
  ComplianceAlertDetailDto,
  ComplianceRegistryScanSummaryDto,
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
 * Returns the number of alerts still awaiting treatment (PENDING or IN_REVIEW).
 *
 * Distinct from the total of {@link listAlerts}, which counts closed alerts too.
 * `GET /api/compliance/alerts/open-count`
 */
export const countOpenAlerts = (): Promise<number> =>
  api.get<{ count: number }>('/api/compliance/alerts/open-count').then((r) => r.data.count);

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

/**
 * Lists the latest registry scan per association, sorted by most-recent scan date descending.
 * `GET /api/compliance/registry-scans?page=0&size=10`
 */
export const listRegistryScans = (
  page = 0,
  size = 10,
): Promise<Page<ComplianceRegistryScanSummaryDto>> =>
  api
    .get<Page<ComplianceRegistryScanSummaryDto>>('/api/compliance/registry-scans', { params: { page, size } })
    .then((r) => r.data);

/**
 * Returns the twenty most recent compliance audit journal entries.
 * `GET /api/compliance/audit-log/recent`
 */
export const listAuditLog = (): Promise<AuditLogEntryDto[]> =>
  api.get<AuditLogEntryDto[]>('/api/compliance/audit-log/recent').then((r) => r.data);
