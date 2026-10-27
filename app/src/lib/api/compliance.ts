import api from '@/lib/api';
import type { Page } from '@/types/payment';
import type { CampaignSummaryDto } from '@/types/campaign';
import type { LegalAcceptanceDto, LegalAcceptanceSubjectType } from '@/types/legal';
import type {
  AuditLogEntryDto,
  ComplianceAlertSummaryDto,
  ComplianceAlertDetailDto,
  ComplianceAssociationDetailDto,
  ComplianceAssociationSummaryDto,
  ComplianceRegistryScanSummaryDto,
  CloseAlertRequest,
  DonorLegalAcceptanceGroupDto,
  OnchainControlEntryDto,
  OnchainFailedJobDto,
  OnchainOutboxHealthDto,
  OnchainPauseResultDto,
  OnchainRationaleRequest,
  OnchainRegistryStatusDto,
  OnchainUnpauseResultDto,
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

/**
 * Lists campaign-report compliance alerts (IC-44), most recent first.
 * `GET /api/compliance/alerts/campaign-reports?page=0&size=20`
 */
export const listCampaignReportAlerts = (page = 0, size = 20): Promise<Page<ComplianceAlertSummaryDto>> =>
  api
    .get<Page<ComplianceAlertSummaryDto>>('/api/compliance/alerts/campaign-reports', { params: { page, size } })
    .then((r) => r.data);

/**
 * Returns the number of campaign-report alerts still awaiting treatment (PENDING or IN_REVIEW).
 * `GET /api/compliance/alerts/campaign-reports/open-count`
 */
export const countOpenCampaignReportAlerts = (): Promise<number> =>
  api.get<{ count: number }>('/api/compliance/alerts/campaign-reports/open-count').then((r) => r.data.count);

/**
 * Lifts a SUSPENDED association back to ACTIVE (voies de contestation, IC-44). Does not reopen
 * the closed alert that caused the suspension.
 * `POST /api/compliance/associations/{associationId}/reactivate`
 */
export const reactivateAssociation = (associationId: string, rationale: string): Promise<void> =>
  api
    .post<void>(`/api/compliance/associations/${associationId}/reactivate`, { rationale })
    .then(() => undefined);

/**
 * Lists every association, sorted by name, independent of whether it has an open alert or a
 * registry scan on file.
 * `GET /api/compliance/associations?page=0&size=20`
 */
export const listAssociations = (
  page = 0,
  size = 20,
): Promise<Page<ComplianceAssociationSummaryDto>> =>
  api
    .get<Page<ComplianceAssociationSummaryDto>>('/api/compliance/associations', { params: { page, size } })
    .then((r) => r.data);

/**
 * Full compliance dossier of one association — status, KYB standing, legal-identity fields.
 * `GET /api/compliance/associations/{associationId}`
 */
export const getAssociationDetail = (associationId: string): Promise<ComplianceAssociationDetailDto> =>
  api.get<ComplianceAssociationDetailDto>(`/api/compliance/associations/${associationId}`).then((r) => r.data);

/**
 * Every campaign of one association, most recent first.
 * `GET /api/compliance/associations/{associationId}/campaigns`
 */
export const listAssociationCampaigns = (associationId: string): Promise<CampaignSummaryDto[]> =>
  api
    .get<CampaignSummaryDto[]>(`/api/compliance/associations/${associationId}/campaigns`)
    .then((r) => r.data);

/**
 * A campaign's publish-attempt history (CAMPAIGN_REVIEW_RETAINED / CAMPAIGN_REVIEW_REFUSED +
 * motif) — not a general campaign status history, see the E6 fiche.
 * `GET /api/compliance/campaigns/{campaignId}/review-history`
 */
export const getCampaignReviewHistory = (campaignId: string): Promise<AuditLogEntryDto[]> =>
  api
    .get<AuditLogEntryDto[]>(`/api/compliance/campaigns/${campaignId}/review-history`)
    .then((r) => r.data);

/**
 * Donor CGU/CGV acceptance proof for one campaign, grouped by donor.
 * `GET /api/compliance/campaigns/{campaignId}/donor-legal-acceptances`
 */
export const getCampaignDonorAcceptances = (campaignId: string): Promise<DonorLegalAcceptanceGroupDto[]> =>
  api
    .get<DonorLegalAcceptanceGroupDto[]>(`/api/compliance/campaigns/${campaignId}/donor-legal-acceptances`)
    .then((r) => r.data);

/**
 * Full CGU/CGV acceptance proof history for one account (notice ACPR ; art. 1740 A CGI).
 * `GET /api/compliance/legal-acceptances?subjectType=&subjectId=`
 */
export const listLegalAcceptances = (
  subjectType: LegalAcceptanceSubjectType,
  subjectId: string,
): Promise<LegalAcceptanceDto[]> =>
  api
    .get<LegalAcceptanceDto[]>('/api/compliance/legal-acceptances', { params: { subjectType, subjectId } })
    .then((r) => r.data);

/**
 * State of the on-chain registry: simulated mode, chain, address, pause state, resume right.
 * `GET /api/compliance/onchain/status`
 */
export const getOnchainStatus = (): Promise<OnchainRegistryStatusDto> =>
  api.get<OnchainRegistryStatusDto>('/api/compliance/onchain/status').then((r) => r.data);

/**
 * Freezes every write of the on-chain registry. Silent: the page shows the backend reason (409 simulated mode,
 * 422 rationale, 502 chain failure) instead of the generic toast.
 * `POST /api/compliance/onchain/pause`
 */
export const pauseOnchainRegistry = (rationale: string): Promise<OnchainPauseResultDto> =>
  api
    .post<OnchainPauseResultDto>('/api/compliance/onchain/pause', { rationale } satisfies OnchainRationaleRequest, {
      silentErrors: true,
    })
    .then((r) => r.data);

/**
 * Resumes the on-chain registry. Silent, like {@link pauseOnchainRegistry} (409 also when the platform lost the
 * resume right).
 * `POST /api/compliance/onchain/unpause`
 */
export const unpauseOnchainRegistry = (rationale: string): Promise<OnchainUnpauseResultDto> =>
  api
    .post<OnchainUnpauseResultDto>('/api/compliance/onchain/unpause', { rationale } satisfies OnchainRationaleRequest, {
      silentErrors: true,
    })
    .then((r) => r.data);

/**
 * The fifty most recent freezes and resumes requested from the platform, newest first, with the officer's email.
 * `GET /api/compliance/onchain/history`
 */
export const listOnchainControlHistory = (): Promise<OnchainControlEntryDto[]> =>
  api.get<OnchainControlEntryDto[]>('/api/compliance/onchain/history').then((r) => r.data);

/**
 * Outbox counts (pending, running, failed, blocked) for the current chain.
 * `GET /api/compliance/onchain/outbox`
 */
export const getOnchainOutboxHealth = (): Promise<OnchainOutboxHealthDto> =>
  api.get<OnchainOutboxHealthDto>('/api/compliance/onchain/outbox').then((r) => r.data);

/**
 * The hundred most recent failed registry writes of the current chain, read-only.
 * `GET /api/compliance/onchain/failed-jobs`
 */
export const listOnchainFailedJobs = (): Promise<OnchainFailedJobDto[]> =>
  api.get<OnchainFailedJobDto[]>('/api/compliance/onchain/failed-jobs').then((r) => r.data);
