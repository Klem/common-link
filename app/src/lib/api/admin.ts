import api from '@/lib/api';
import type { VerificationStatus } from '@/types/association';
import type {
  AdminVerificationSummaryDto,
  AdminVerificationDetailDto,
  BeneficialOwnerDto,
  AddBeneficialOwnerRequest,
  RegistryPreCheckDto,
  VigilanceMeasuresDto,
} from '@/types/admin';
import type { Page } from '@/types/payment';

/**
 * Lists association dossiers filtered by verification status.
 * `GET /api/admin/verifications?status=PENDING&page=0&size=20`
 */
export const listVerifications = (
  status: VerificationStatus,
  page = 0,
  size = 20,
): Promise<Page<AdminVerificationSummaryDto>> =>
  api
    .get<Page<AdminVerificationSummaryDto>>('/api/admin/verifications', {
      params: { status, page, size },
    })
    .then((r) => r.data);

/**
 * Fetches full dossier for one association.
 * `GET /api/admin/verifications/{associationId}`
 */
export const getVerificationDetail = (associationId: string): Promise<AdminVerificationDetailDto> =>
  api
    .get<AdminVerificationDetailDto>(`/api/admin/verifications/${associationId}`)
    .then((r) => r.data);

/**
 * Approves the association's KYC dossier.
 * `POST /api/admin/verifications/{associationId}/approve` → 204
 * Throws 409 if the dossier is not PENDING.
 */
export const approveVerification = (associationId: string): Promise<void> =>
  api.post(`/api/admin/verifications/${associationId}/approve`).then(() => undefined);

/**
 * Rejects the association's KYC dossier with a mandatory reason.
 * `POST /api/admin/verifications/{associationId}/reject` → 204
 * Throws 409 if not PENDING, 422 if reason is blank or over 1000 chars.
 */
export const rejectVerification = (associationId: string, reason: string): Promise<void> =>
  api
    .post(`/api/admin/verifications/${associationId}/reject`, { reason })
    .then(() => undefined);

/**
 * Fetches the latest persisted registry pre-check without contacting any external registry.
 * `GET /api/admin/verifications/{associationId}/registry-precheck`
 * Returns `null` (HTTP 204) if the association has never been scanned.
 */
export const getRegistryPreCheck = (associationId: string): Promise<RegistryPreCheckDto | null> =>
  api
    .get<RegistryPreCheckDto>(`/api/admin/verifications/${associationId}/registry-precheck`)
    .then((r) => (r.status === 204 ? null : r.data));

/**
 * Runs a fresh registry pre-check scan and persists it (append-only audit trail).
 * `POST /api/admin/verifications/{associationId}/registry-precheck`
 * Informational only — each source degrades gracefully.
 */
export const scanRegistryPreCheck = (associationId: string): Promise<RegistryPreCheckDto> =>
  api
    .post<RegistryPreCheckDto>(`/api/admin/verifications/${associationId}/registry-precheck`)
    .then((r) => r.data);

/**
 * Fetches the vigilance measures applicable to the association's current risk level.
 * `GET /api/admin/verifications/{associationId}/vigilance`
 */
export const getVigilanceMeasures = (associationId: string): Promise<VigilanceMeasuresDto> =>
  api
    .get<VigilanceMeasuresDto>(`/api/admin/verifications/${associationId}/vigilance`)
    .then((r) => r.data);

/**
 * Lists all beneficial owners for an association (retained and discarded).
 * `GET /api/admin/verifications/{associationId}/beneficial-owners`
 */
export const listBeneficialOwners = (associationId: string): Promise<BeneficialOwnerDto[]> =>
  api
    .get<BeneficialOwnerDto[]>(`/api/admin/verifications/${associationId}/beneficial-owners`)
    .then((r) => r.data);

/**
 * Adds a beneficial owner confirmed by the curator.
 * `POST /api/admin/verifications/{associationId}/beneficial-owners`
 */
export const addBeneficialOwner = (
  associationId: string,
  request: AddBeneficialOwnerRequest,
): Promise<BeneficialOwnerDto> =>
  api
    .post<BeneficialOwnerDto>(`/api/admin/verifications/${associationId}/beneficial-owners`, request)
    .then((r) => r.data);

/**
 * Marks a beneficial owner as discarded.
 * `POST /api/admin/verifications/{associationId}/beneficial-owners/{ownerId}/discard` → 204
 */
export const discardBeneficialOwner = (associationId: string, ownerId: string): Promise<void> =>
  api
    .post(`/api/admin/verifications/${associationId}/beneficial-owners/${ownerId}/discard`)
    .then(() => undefined);

/**
 * Downloads a verification document as a Blob.
 * `GET /api/admin/verifications/{associationId}/documents/{docId}/content`
 * Returns the blob and the filename extracted from the Content-Disposition header.
 */
export const downloadVerificationDocument = async (
  associationId: string,
  docId: string,
): Promise<{ blob: Blob; fileName: string }> => {
  const response = await api.get(
    `/api/admin/verifications/${associationId}/documents/${docId}/content`,
    { responseType: 'blob' },
  );
  const disposition: string = response.headers['content-disposition'] ?? '';
  const match = disposition.match(/filename[^;=\n]*=((['"]).*?\2|[^;\n]*)/);
  const fileName = match ? match[1].replace(/['"]/g, '') : `document-${docId}`;
  return { blob: response.data as Blob, fileName };
};
