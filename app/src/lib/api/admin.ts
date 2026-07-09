import api from '@/lib/api';
import type { VerificationStatus } from '@/types/association';
import type { AdminVerificationSummaryDto, AdminVerificationDetailDto, RegistryPreCheckDto } from '@/types/admin';
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
 * Queries French public registries to check legal existence of the association.
 * `GET /api/admin/verifications/{associationId}/registry-precheck`
 * Informational only — each source degrades gracefully.
 */
export const getRegistryPreCheck = (associationId: string): Promise<RegistryPreCheckDto> =>
  api
    .get<RegistryPreCheckDto>(`/api/admin/verifications/${associationId}/registry-precheck`)
    .then((r) => r.data);

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
