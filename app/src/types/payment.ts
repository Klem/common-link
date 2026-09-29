/**
 * High-level category for a payout — mirrors backend PayoutKind enum.
 * - REMUNERATION: salary, social charges (plan comptable 64-*)
 * - EXPENSE: operational costs (plan comptable 60-*, 61-*, 62-*, 65-*)
 */
export const PayoutKind = {
  REMUNERATION: 'REMUNERATION',
  EXPENSE: 'EXPENSE',
} as const;
export type PayoutKind = (typeof PayoutKind)[keyof typeof PayoutKind];

/**
 * Lifecycle status for a payout — mirrors backend PayoutStatus enum.
 */
export const PayoutStatus = {
  PENDING: 'PENDING',
  CONFIRMED: 'CONFIRMED',
  FAILED: 'FAILED',
} as const;
export type PayoutStatus = (typeof PayoutStatus)[keyof typeof PayoutStatus];

/**
 * State of the Bridge payment initiation for a payout — mirrors backend BridgePaymentStatus.
 *
 * Deliberately separate from {@link PayoutStatus}: the accounting lifecycle stays three-state so
 * balance, KPI and breakdown computations keep their meaning, while the bank's own view is carried
 * here for display. The first six values are Bridge's ISO 20022 transaction statuses; the last two
 * are payment-link terminal states.
 */
export const BridgePaymentStatus = {
  CREA: 'CREA',
  ACTC: 'ACTC',
  PDNG: 'PDNG',
  ACSC: 'ACSC',
  RJCT: 'RJCT',
  PART: 'PART',
  LINK_EXPIRED: 'LINK_EXPIRED',
  LINK_REVOKED: 'LINK_REVOKED',
} as const;
export type BridgePaymentStatus = (typeof BridgePaymentStatus)[keyof typeof BridgePaymentStatus];

/** Bridge states in which the transfer is engaged but not settled. */
export const BRIDGE_IN_FLIGHT_STATUSES: readonly BridgePaymentStatus[] = [
  BridgePaymentStatus.CREA,
  BridgePaymentStatus.ACTC,
  BridgePaymentStatus.PDNG,
  BridgePaymentStatus.PART,
];

/**
 * Stable cause of a payout's last failure — mirrors backend PayoutErrorCode.
 *
 * The first seventeen are Bridge's own `status_reason` values, bare ISO 20022 codes; the rest are
 * CommonLink's own causes, which never reached a bank. Each maps to one
 * `dashboard.campaigns.payments.history.errorCode.*` key, with `fallback` for anything a newer
 * backend sends that this build does not know.
 */
export const PayoutErrorCode = {
  AC01: 'AC01',
  AC04: 'AC04',
  AC06: 'AC06',
  AG01: 'AG01',
  AM04: 'AM04',
  AM18: 'AM18',
  CH03: 'CH03',
  CUST: 'CUST',
  DS02: 'DS02',
  FF01: 'FF01',
  FRAD: 'FRAD',
  MS03: 'MS03',
  NOAS: 'NOAS',
  RR01: 'RR01',
  RR03: 'RR03',
  RR04: 'RR04',
  RR12: 'RR12',
  UNSPECIFIED: 'UNSPECIFIED',
  UNKNOWN: 'UNKNOWN',
  LINK_EXPIRED: 'LINK_EXPIRED',
  LINK_REVOKED: 'LINK_REVOKED',
  INITIATION_FAILED: 'INITIATION_FAILED',
  DESTINATION_UNVERIFIED: 'DESTINATION_UNVERIFIED',
  LINK_NOT_RECORDED: 'LINK_NOT_RECORDED',
} as const;
export type PayoutErrorCode = (typeof PayoutErrorCode)[keyof typeof PayoutErrorCode];

/** Codes this build can name. Anything else falls back rather than showing a raw code. */
const KNOWN_ERROR_CODES: ReadonlySet<string> = new Set(Object.keys(PayoutErrorCode));

/**
 * i18n key under `dashboard.campaigns.payments.history` explaining why a payout failed.
 *
 * Always returns a key: a deployed backend can carry a code this bundle predates, and the previous
 * behaviour — printing the stored string straight into a tooltip — is what showed associations
 * `AC01` and `Bridge recorded a different destination IBAN for payout 696de1eb-…`.
 */
export function payoutErrorMessageKey(code: PayoutErrorCode | null): string {
  return `errorCode.${code !== null && KNOWN_ERROR_CODES.has(code) ? code : 'fallback'}`;
}

/** Single payout as returned by the API. */
export interface PayoutDto {
  id: string;
  campaignId: string;
  payeeId: string;
  payeeName: string;
  payeeIbanId: string;
  ibanValue: string;
  amount: number;
  kind: PayoutKind;
  /** French plan comptable code, e.g. "60-mat", "64-rem". */
  typeCode: string;
  label: string;
  status: PayoutStatus;
  createdAt: string;
  confirmedAt: string | null;
  onchainJobId: string | null;
  /** State of the Bridge initiation; null when no transfer has been initiated. */
  bridgeStatus: BridgePaymentStatus | null;
  /** Stable cause of the last failure, turned into a sentence by `history.errorCode.*`. */
  bridgeLastErrorCode: PayoutErrorCode | null;
  /**
   * URL the association must open to authorise the transfer with its own bank. Non-null while a
   * payout awaits that authorisation.
   */
  bridgeCheckoutUrl: string | null;
  /**
   * Bridge's own identifier for the transfer — the payout's reference in the journal.
   *
   * Null until a transfer has actually been ordered, and the journal then shows nothing rather
   * than inventing a reference: a payout that never reached a bank has none to show.
   */
  bridgePaymentTransactionId: string | null;
}

/** True while the payout's transfer is engaged but not yet settled by the bank. */
export function isPayoutInFlight(payout: PayoutDto): boolean {
  return payout.bridgeStatus !== null && BRIDGE_IN_FLIGHT_STATUSES.includes(payout.bridgeStatus);
}

/**
 * True while the payout is waiting for the association to authorise the transfer at its bank.
 *
 * Distinct from {@link isPayoutInFlight}: here nothing has been debited yet and the association
 * still has an action to take, so the UI must offer the authorisation link rather than merely
 * report progress.
 *
 * `ACTC` counts, not just `CREA`. Bridge stamps `ACTC` the moment the payer enters the tunnel and
 * keeps it for the whole bank authentication, so gating on `CREA` alone made the button vanish at
 * the exact instant it was clicked: an association that closed the tab was left with a row saying
 * "in transit" and no action at all until the link expired — five minutes on staging, a full day
 * in production, for a transfer where nothing had been debited.
 *
 * Re-opening a link that already carries a request adds a second one beside it, which is only safe
 * because the backend ranks a link's requests by tier and then by recency: a transfer the bank is
 * executing can no longer lose to a newer unauthorised sibling. `bridgeCheckoutUrl` is cleared
 * whenever the link dies, so a dead link is never offered here.
 */
export function needsBankAuthorisation(payout: PayoutDto): boolean {
  return (
    (payout.bridgeStatus === BridgePaymentStatus.CREA ||
      payout.bridgeStatus === BridgePaymentStatus.ACTC) &&
    payout.bridgeCheckoutUrl !== null
  );
}

/**
 * True when the last attempt to initiate the transfer failed, leaving nothing engaged.
 *
 * The payout deliberately stays PENDING rather than FAILED: FAILED is terminal, and stamping it on
 * a transfer that never debited anything would retire a payout the association can still issue. So
 * this is the only thing distinguishing "attempted and failed" from "never attempted" — without it
 * the two are the same hourglass. It clears by itself on a successful retry, the backend resetting
 * the error when it attaches a payment link.
 */
export function lastAttemptFailed(payout: PayoutDto): boolean {
  return (
    payout.status === PayoutStatus.PENDING &&
    payout.bridgeStatus === null &&
    // `!= null`, deliberately loose: an API that predates this field omits it, and JSON absence is
    // `undefined`, which a strict `!== null` reports as "a previous attempt failed". Every payout
    // never submitted would then offer "Réessayer" — and that button calls confirm, so a click
    // would initiate a real transfer on a row the association never sent anywhere.
    payout.bridgeLastErrorCode != null
  );
}

/**
 * What the journal shows in a payout's status cell.
 *
 * Deliberately finer than {@link PayoutStatus}: the accounting lifecycle has three values, but a
 * payout sitting in `PENDING` can be four very different things to the association — never
 * submitted, waiting for it to authorise the transfer at its bank, authorised and travelling, or
 * failed on the way out and retryable. Collapsing them is what made a hourglass mean anything.
 *
 * Derived once by {@link payoutUiState} so the badge, the quick filters, the live counter and the
 * CSV export can never disagree about what a row is.
 */
export const PayoutUiState = {
  /** Terminal refusal by the bank. Nothing more can be done from here. */
  FAILED: 'FAILED',
  /** Settled: the beneficiary has been credited. */
  CONFIRMED: 'CONFIRMED',
  /** Waiting for the association to authorise the transfer with its own bank. */
  AWAITING_AUTHORISATION: 'AWAITING_AUTHORISATION',
  /** Authorised at the bank, not yet executed. */
  AUTHORISED: 'AUTHORISED',
  /** Engaged with the bank in a state that is neither of the two above (Bridge `PART`). */
  IN_TRANSIT: 'IN_TRANSIT',
  /** The last initiation attempt failed; nothing was debited and the payout can be re-issued. */
  RETRYABLE: 'RETRYABLE',
  /** Recorded, never submitted to a bank. */
  PENDING: 'PENDING',
} as const;
export type PayoutUiState = (typeof PayoutUiState)[keyof typeof PayoutUiState];

/**
 * The single state a payout is displayed in.
 *
 * Evaluation order matters and mirrors the one the status chip has always used: the accounting
 * status wins over the bank's view (a `FAILED` payout is refused whatever Bridge last said), then
 * the in-flight states are split by `bridgeStatus`, then a failed attempt, then plain pending.
 *
 * @param payout the payout to classify.
 * @returns the state its badge, its quick filter and its CSV row all read.
 */
export function payoutUiState(payout: PayoutDto): PayoutUiState {
  if (payout.status === PayoutStatus.FAILED) return PayoutUiState.FAILED;
  if (payout.status === PayoutStatus.CONFIRMED) return PayoutUiState.CONFIRMED;
  if (isPayoutInFlight(payout)) {
    if (
      payout.bridgeStatus === BridgePaymentStatus.CREA ||
      payout.bridgeStatus === BridgePaymentStatus.ACTC
    ) {
      return PayoutUiState.AWAITING_AUTHORISATION;
    }
    if (payout.bridgeStatus === BridgePaymentStatus.PDNG) return PayoutUiState.AUTHORISED;
    // `PART` — engaged, partially accepted, neither awaiting the association nor settled.
    return PayoutUiState.IN_TRANSIT;
  }
  if (lastAttemptFailed(payout)) return PayoutUiState.RETRYABLE;
  return PayoutUiState.PENDING;
}

/** What the end of a payout's row offers. */
export const PayoutAction = {
  /** Just back from the bank, fate still unknown: state the wait, offer no link. */
  AWAITING_RETURN: 'AWAITING_RETURN',
  /** Re-issue the payout with a fresh authorisation link. */
  RETRY: 'RETRY',
  /** Open the bank's authorisation tunnel. */
  AUTHORISE: 'AUTHORISE',
  /** Nothing to do — the row only opens its detail panel. */
  NONE: 'NONE',
} as const;
export type PayoutAction = (typeof PayoutAction)[keyof typeof PayoutAction];

/**
 * The action a payout's row offers, if any.
 *
 * Kept beside {@link payoutUiState} rather than derived from it: the two do not partition the same
 * way. `AWAITING_RETURN` and `AUTHORISE` are both `AWAITING_AUTHORISATION` rows, told apart only by
 * whether the association is coming back from its bank right now.
 *
 * @param payout the payout to classify.
 * @param awaitingReturnPayoutId id of the payout the association has just returned from its bank
 *   for, or null.
 */
export function payoutAction(payout: PayoutDto, awaitingReturnPayoutId: string | null): PayoutAction {
  // The in-flight check is not redundant with the id match: the hook only sets that id while the
  // payout is still `CREA`, but it is re-evaluated on every poll, and a payout that has meanwhile
  // been refused must offer its retry rather than keep telling the association to wait for a bank
  // that has already answered.
  if (payout.id === awaitingReturnPayoutId && isPayoutInFlight(payout)) {
    return PayoutAction.AWAITING_RETURN;
  }
  if (lastAttemptFailed(payout)) return PayoutAction.RETRY;
  if (needsBankAuthorisation(payout) && payout.bridgeCheckoutUrl) return PayoutAction.AUTHORISE;
  return PayoutAction.NONE;
}

/**
 * A business rule preventing a payout from being issued — mirrors backend PayoutBlockingReason enum,
 * except IBAN_NOT_VERIFIED: the payee-IBAN selector only ever offers VERIFIED IBANs, so the frontend
 * can never end up in that state and the reason is omitted here.
 */
export const PayoutBlockingReason = {
  INSUFFICIENT_BALANCE: 'INSUFFICIENT_BALANCE',
  DESCRIPTION_TOO_SHORT: 'DESCRIPTION_TOO_SHORT',
} as const;
export type PayoutBlockingReason = (typeof PayoutBlockingReason)[keyof typeof PayoutBlockingReason];

/** Aggregated KPIs for the Payments tab. */
export interface PayoutSummaryDto {
  confirmedAmount: number;
  confirmedCount: number;
  pendingAmount: number;
  txTotal: number;
  txConfirmed: number;
  availableBalance: number;
  /**
   * Whether a payout would actually be executed. False while the backend runs Bridge in demo
   * mode: the transfer would be simulated, never sent to a bank, yet reported as settled.
   */
  paymentsEnabled: boolean;
}

/** Request body for creating a payout. */
export interface CreatePayoutRequest {
  payeeId: string;
  payeeIbanId: string;
  amount: number;
  kind: PayoutKind;
  typeCode: string;
  /** Justification text — min 6, max 500 chars. */
  label: string;
}

/** Spring Page<T> wrapper returned by paginated list endpoints. */
export interface Page<T> {
  content: T[];
  totalElements: number;
  totalPages: number;
  number: number;
  size: number;
  first: boolean;
  last: boolean;
}
