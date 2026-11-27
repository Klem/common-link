/** How the authorised signer's signature is produced on fiscal receipts (Cerfa 11580). */
export const SignatureMode = {
  /** PNG/JPEG image uploaded by the association (re-encoded client-side). */
  UPLOAD: 'UPLOAD',
  /** Drawn by hand on a signature pad, exported as PNG. */
  DRAWN: 'DRAWN',
} as const;
export type SignatureMode = (typeof SignatureMode)[keyof typeof SignatureMode];

/** Authorised signer state returned by `GET` / `PUT /api/association/me/signatory`. */
export interface SignatoryDto {
  signerName: string | null;
  signerRole: string | null;
  /** Saved signature mode, or null when no signature has been saved yet. */
  mode: SignatureMode | null;
  hasSignature: boolean;
  /** ISO timestamp of the last attestation, or null. */
  attestedAt: string | null;
  /** ISO timestamp of the last save, or null. */
  updatedAt: string | null;
}

/** Payload of `PUT /api/association/me/signatory` (sent as multipart/form-data). */
export interface SaveSignatoryRequest {
  signerName: string;
  signerRole: string;
  mode: SignatureMode;
  /** Signature image (PNG/JPEG), always required. */
  file: Blob;
}

/** Validation bounds mirrored from the backend `SignatoryService`. */
export const SIGNATORY_LIMITS = {
  NAME_MAX: 255,
  ROLE_MAX: 100,
  IMAGE_MAX_BYTES: 204_800,
  IMAGE_MAX_WIDTH: 1200,
  IMAGE_MAX_HEIGHT: 600,
} as const;

/** Image MIME types accepted by the backend (sniffed server-side). */
export const SIGNATURE_IMAGE_TYPES: readonly string[] = ['image/png', 'image/jpeg'];
