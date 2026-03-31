/**
 * Read model for a donor's profile as returned by `GET /api/donor/me`.
 */
export interface DonorProfileDto {
  /** Unique profile identifier (UUID). */
  id: string;
  /** Optional public display name chosen by the donor. */
  displayName: string | null;
  /** When true, the donor's identity is hidden from public donation records. */
  anonymous: boolean;
}

/**
 * Payload for `PATCH /api/donor/me`.
 * All fields are optional — only provided fields are updated.
 */
export interface UpdateDonorProfileRequest {
  displayName?: string;
  anonymous?: boolean;
}
