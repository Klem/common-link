import api from '@/lib/api';
import type { LegalAcceptanceStateDto, LegalDocumentType } from '@/types/legal';

/** Whether the authenticated association already accepted the current version of [documentType]. */
export const getLegalAcceptanceState = (documentType: LegalDocumentType): Promise<LegalAcceptanceStateDto> =>
  api.get<LegalAcceptanceStateDto>(`/api/association/legal-acceptance/${documentType}`).then((r) => r.data);
