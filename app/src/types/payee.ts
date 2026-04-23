/**
 * Possible verification states for a payee IBAN.
 * - PENDING: just added, not yet validated
 * - FORMAT_VALID: IBAN format is syntactically correct
 * - VERIFIED: VOP verification matched the payee name
 * - CLOSE_MATCH: VOP returned a close but non-exact match
 * - NO_MATCH: VOP found no match for this IBAN + name combination
 * - NOT_POSSIBLE: VOP check could not be performed (bank not reachable, etc.)
 * - INVALID: IBAN format is invalid
 */
export const IbanVerificationStatus = {
  PENDING: 'PENDING',
  FORMAT_VALID: 'FORMAT_VALID',
  VERIFIED: 'VERIFIED',
  CLOSE_MATCH: 'CLOSE_MATCH',
  NO_MATCH: 'NO_MATCH',
  NOT_POSSIBLE: 'NOT_POSSIBLE',
  INVALID: 'INVALID',
} as const;
export type IbanVerificationStatus = typeof IbanVerificationStatus[keyof typeof IbanVerificationStatus];

/**
 * Result codes returned by the Verification of Payee (VOP) service.
 */
export const VopResult = {
  MATCH: 'MATCH',
  CLOSE_MATCH: 'CLOSE_MATCH',
  NO_MATCH: 'NO_MATCH',
  NOT_POSSIBLE: 'NOT_POSSIBLE',
} as const;
export type VopResult = typeof VopResult[keyof typeof VopResult];

/**
 * A single IBAN attached to a payee, including its VOP verification state.
 */
export interface PayeeIbanDto {
  /** Unique identifier (UUID). */
  id: string;
  /** The IBAN string. */
  iban: string;
  /** Current verification status of this IBAN. */
  status: IbanVerificationStatus;
  /** VOP result code, populated after a VOP check. */
  vopResult: VopResult | null;
  /** Suggested name returned by VOP when result is CLOSE_MATCH. */
  vopSuggestedName: string | null;
  /** ISO-8601 timestamp of the last VOP check. */
  verifiedAt: string | null;
}

/**
 * A payee (organisation or individual) linked to an association.
 * Each payee can have multiple IBANs.
 */
export interface PayeeDto {
  /** Unique identifier (UUID). */
  id: string;
  /** Display name of the payee. */
  name: string;
  /** SIREN (9 digits) — primary identifier. */
  identifier1: string;
  /** SIRET (14 digits) — optional secondary identifier. */
  identifier2: string | null;
  /** NAF activity code. */
  activityCode: string | null;
  /** Legal category (e.g. "PME"). */
  category: string | null;
  /** City of the payee. */
  city: string | null;
  /** Postal code of the payee. */
  postalCode: string | null;
  /** Whether the payee is currently active. */
  active: boolean;
  /** IBANs associated with this payee. */
  ibans: PayeeIbanDto[];
  /** ISO-8601 creation timestamp. */
  createdAt: string;
}

/**
 * Simplified result returned by the INSEE Sirene search proxy.
 * Contains only the fields needed for the payee creation flow.
 */
export interface SireneSearchResultDto {
  /** SIREN number (9 digits). */
  siren: string;
  /** SIRET number (14 digits), present only for SIRET searches. */
  siret: string | null;
  /** Legal name of the entity. */
  name: string;
  /** NAF/APE activity code. */
  nafCode: string | null;
  /** Legal category label. */
  category: string | null;
  /** City of the establishment. */
  city: string | null;
  /** Postal code of the establishment. */
  postalCode: string | null;
  /** Formatted street address of the establishment. */
  address: string | null;
  /** Whether the entity is administratively active. */
  active: boolean;
  /** True if this establishment is the registered head office (siège social). */
  isSiege: boolean;
  /** True if the entity belongs to the Économie Sociale et Solidaire sector. */
  isEss: boolean;
  /** ISO date of legal creation. */
  creationDate: string | null;
  /** Employee range code (tranche d'effectifs). */
  employeeRange: string | null;
}

/**
 * Payload for `POST /api/association/payees`.
 */
export interface CreatePayeeRequest {
  /** Display name of the payee. */
  name: string;
  /** SIREN (9 digits). */
  identifier1: string;
  /** SIRET (14 digits), optional. */
  identifier2?: string;
  /** NAF activity code, optional. */
  activityCode?: string;
  /** Legal category, optional. */
  category?: string;
  /** City, optional. */
  city?: string;
  /** Postal code, optional. */
  postalCode?: string;
  /** Whether the payee is active (defaults to true on the backend). */
  active?: boolean;
}

/**
 * Payload for `POST /api/association/payees/:id/ibans`.
 */
export interface AddIbanRequest {
  /** The IBAN string to add. */
  iban: string;
}

/**
 * Response returned by the VOP verification endpoint.
 */
export interface VopVerifyResponseDto {
  /** UUID of the verified IBAN record. */
  ibanId: string;
  /** The IBAN string that was verified. */
  iban: string;
  /** Updated verification status after VOP. */
  status: IbanVerificationStatus;
  /** VOP result code. */
  vopResult: VopResult | null;
  /** Suggested name when result is CLOSE_MATCH. */
  suggestedName: string | null;
  /** Human-readable description of the verification outcome. */
  message: string;
}
