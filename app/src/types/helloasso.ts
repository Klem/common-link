/** HelloAsso link as shown to the association (mirror of the backend `HelloAssoLinkState`). */
export const HelloAssoLink = {
  /** Never linked, or disconnected. */
  NONE: 'NONE',
  /** Linked and working. */
  OK: 'OK',
  /** HelloAsso refused our access (key regenerated or deleted, renewal failed): the key must be pasted again. */
  BROKEN: 'BROKEN',
} as const;
export type HelloAssoLink = typeof HelloAssoLink[keyof typeof HelloAssoLink];

/** Whether HelloAsso notifications reach CommonLink (mirror of the backend `HelloAssoWebhookState`). */
export const HelloAssoWebhookState = {
  /** No notification received yet. */
  UNKNOWN: 'UNKNOWN',
  /** At least one notification received since the last miss. */
  RECEIVED: 'RECEIVED',
  /** A payment was confirmed without its notification: the notification URL is probably not saved at HelloAsso. */
  MISSING: 'MISSING',
} as const;
export type HelloAssoWebhookState = typeof HelloAssoWebhookState[keyof typeof HelloAssoWebhookState];

/**
 * HelloAsso link details of the neutral payment status (`PaymentStatusResponse.helloasso`), read from our database
 * only. Never the secret nor a token. Instants are ISO-8601 strings.
 */
export interface HelloAssoStatus {
  link: HelloAssoLink;
  /** URL to paste in the HelloAsso back office; null until created (`ensureNotificationUrl`). */
  notificationUrl: string | null;
  /** Linked HelloAsso organization, null when not linked. */
  organizationSlug: string | null;
  /** Last eligibility read (`isCashInCompliant`). */
  canCollect: boolean;
  /** Whether the organization could ever collect: tells "suspended" from "never verified". */
  everCompliant: boolean;
  lastEligibilityCheckAt: string | null;
  webhook: HelloAssoWebhookState;
  /** `abcd…wxyz` of the linked client id. */
  clientIdMasked: string | null;
  linkedAt: string | null;
  /** Email of the user who linked the account. */
  linkedBy: string | null;
  /** When the client secret used for the link was discarded. */
  secretWipedAt: string | null;
  /** Last token renewal. */
  lastRefreshAt: string | null;
}

/** Body of `POST /api/association/helloasso/credentials/verify`. The secret is sent once, never stored. */
export interface HelloAssoVerifyRequest {
  clientId: string;
  clientSecret: string;
}

/**
 * Why the HelloAsso organization is not the association verified on CommonLink: the two refusing values of the backend
 * `HelloAssoKybOutcome`.
 */
export const HelloAssoMismatchReason = {
  /** Both sides carry an RNA and they differ. */
  RNA_MISMATCH: 'RNA_MISMATCH',
  /** No RNA on one side or both: the names were compared, and they differ. */
  NAME_MISMATCH: 'NAME_MISMATCH',
} as const;
export type HelloAssoMismatchReason = typeof HelloAssoMismatchReason[keyof typeof HelloAssoMismatchReason];

/** Result of the key verification. Nothing is persisted at this stage. */
export interface HelloAssoVerifyResponse {
  /** One-time handle (10 minutes) to confirm the link; null when the organization is not the association's. */
  verifyId: string | null;
  orgName: string;
  slug: string;
  /** Whether the organization is the association verified on CommonLink. */
  matchesKyb: boolean;
  /** Whether HelloAsso accepts payments for the organization right now. */
  canCollect: boolean;
  /** Why the organization does not match; null (or absent) when it matches. */
  mismatchReason?: HelloAssoMismatchReason | null;
  /** RNA of the association as known by CommonLink; null when it has none. */
  associationRna?: string | null;
  /**
   * RNA of the HelloAsso organization (`rnaNumber`), null when blank. HelloAsso fills it from the documents supplied at
   * registration: the organization cannot type it.
   */
  organizationRna?: string | null;
}

/** The association's mandate to CommonLink over its HelloAsso access. */
export interface HelloAssoMandate {
  accepted: boolean;
  /** Wording version shown to the user; must equal the backend's current one. */
  version: string;
}

/** Body of `POST /api/association/helloasso/connection`. */
export interface HelloAssoConnectRequest {
  verifyId: string;
  mandate: HelloAssoMandate;
}

/** `code` property of a HelloAsso linking refusal (backend `HelloAssoLinkException`). */
export const HelloAssoErrorCode = {
  INVALID_KEYS: 'HELLOASSO_INVALID_KEYS',
  ORGANIZATION_UNRESOLVED: 'HELLOASSO_ORGANIZATION_UNRESOLVED',
  NOT_ASSOCIATION_PROVIDER: 'HELLOASSO_NOT_ASSOCIATION_PROVIDER',
  MANDATE_REQUIRED: 'HELLOASSO_MANDATE_REQUIRED',
  VERIFICATION_EXPIRED: 'HELLOASSO_VERIFICATION_EXPIRED',
  NOT_LINKED: 'HELLOASSO_NOT_LINKED',
  NOTIFICATION_URL_NOT_CONFIGURED: 'HELLOASSO_NOTIFICATION_URL_NOT_CONFIGURED',
  /** HTTP 409: the organization is already linked to another CommonLink association, for good. */
  ORGANIZATION_ALREADY_LINKED: 'HELLOASSO_ORGANIZATION_ALREADY_LINKED',
} as const;
export type HelloAssoErrorCode = typeof HelloAssoErrorCode[keyof typeof HelloAssoErrorCode];

/** Outcome of the live key check of the "Relier" screen. */
export const KeyCheck = {
  /** One of the two codes is missing. */
  IDLE: 'IDLE',
  CHECKING: 'CHECKING',
  /** HelloAsso accepts the codes and they belong to the association verified on CommonLink. */
  OK: 'OK',
  /** HelloAsso refuses the codes (or they reach no single organization). */
  INVALID: 'INVALID',
  /** The codes belong to another organization: refused, nothing kept. */
  MISMATCH: 'MISMATCH',
  /** The organization is already linked to another CommonLink association: refused for good, nothing kept. */
  ALREADY_LINKED: 'ALREADY_LINKED',
  /** HelloAsso or our API did not answer (outage, rate limit): retry later. */
  UNAVAILABLE: 'UNAVAILABLE',
} as const;
export type KeyCheck = typeof KeyCheck[keyof typeof KeyCheck];
