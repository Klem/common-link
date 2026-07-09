import type { VerificationStatus } from '@/types/association';
import type { VerificationDocType } from '@/types/verification';

/** Maps a VerificationStatus to the corresponding badge CSS classes. */
export const STATUS_BADGE_CLASS: Record<VerificationStatus, string> = {
  PENDING: 'badge badge-warning',
  VERIFIED: 'badge badge-success',
  REJECTED: 'badge badge-error',
  UNVERIFIED: 'badge badge-neutral',
};

/**
 * Maps a required VerificationDocType to its i18n key path (under `admin.docType.*`).
 * Used in VerificationDocumentRow and the detail screen to display human labels.
 */
export const DOC_TYPE_I18N_KEY: Record<VerificationDocType, string> = {
  VERIF_STATUTS: 'docType.verifStatuts',
  VERIF_RNA_RECEIPT: 'docType.verifRnaReceipt',
  VERIF_REPRESENTATIVE_ID: 'docType.verifRepresentativeId',
};
