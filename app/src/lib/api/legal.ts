import api from '@/lib/api';
import { LEGAL_VERSION_OUTDATED, type LegalAcceptanceStateDto, type LegalDocumentType } from '@/types/legal';

/** Whether the authenticated association already accepted the current version of [documentType]. */
export const getLegalAcceptanceState = (documentType: LegalDocumentType): Promise<LegalAcceptanceStateDto> =>
  api.get<LegalAcceptanceStateDto>(`/api/association/legal-acceptance/${documentType}`).then((r) => r.data);

/**
 * Records the authenticated association's acceptance of `version` of `documentType` — the post-login
 * gate. Answers 409 `LEGAL_VERSION_OUTDATED` (see {@link isLegalVersionOutdated}) when `version` is no
 * longer current.
 */
export const acceptLegalDocument = (documentType: LegalDocumentType, version: string): Promise<LegalAcceptanceStateDto> =>
  api.post<LegalAcceptanceStateDto>(`/api/association/legal-acceptance/${documentType}`, { version }).then((r) => r.data);

/** Whether `err` is the 409 answered when the accepted CGU/CGV version is no longer the current one. */
export function isLegalVersionOutdated(err: unknown): boolean {
  const response =
    err && typeof err === 'object' && 'response' in err
      ? (err as { response?: { status?: number; data?: { code?: string } } }).response
      : undefined;
  return response?.status === 409 && response.data?.code === LEGAL_VERSION_OUTDATED;
}
