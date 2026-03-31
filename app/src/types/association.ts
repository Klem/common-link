/**
 * Read model for an association's profile as returned by `GET /api/association/me`.
 * The `verified` flag indicates whether the association has been verified on-chain.
 */
export interface AssociationProfileDto {
  /** Unique profile identifier (UUID). */
  id: string;
  /** Official name of the association. */
  name: string;
  /** SIREN / official registration identifier. */
  identifier: string;
  city: string | null;
  postalCode: string | null;
  contactName: string | null;
  description: string | null;
  /** Whether the association has received on-chain verification. */
  verified: boolean;
}

/**
 * Payload for `PATCH /api/association/me`.
 * All fields are optional — only provided fields are updated.
 */
export interface UpdateAssociationProfileRequest {
  contactName?: string;
  city?: string;
  postalCode?: string;
  description?: string;
}
