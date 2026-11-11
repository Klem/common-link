/**
 * Funds of a campaign as computed by the backend (`GET /api/association/campaigns/{id}/funds`, `CampaignFundsResponse`):
 * where its donations stand between the payment provider and the association's bank account, and what a payment can
 * use. Amounts are euros (decimal numbers). The front never adds them up: it displays them.
 */

/** Payment provider of the association (its current one). */
export const FundsProvider = {
  MOLLIE: 'MOLLIE',
  HELLOASSO: 'HELLOASSO',
} as const;
export type FundsProvider = typeof FundsProvider[keyof typeof FundsProvider];

/** Where a HelloAsso payout of the campaign stands: paid out but not arrived yet, or arrived. */
export const CashOutState = {
  IN_TRANSIT: 'IN_TRANSIT',
  AVAILABLE: 'AVAILABLE',
} as const;
export type CashOutState = typeof CashOutState[keyof typeof CashOutState];

/** How an arrived payout became usable: confirmed by the association, settlement delay, or CommonLink team. */
export const CashOutConfirmedBy = {
  MANUAL: 'MANUAL',
  AUTO: 'AUTO',
  CURATOR_RELEASE: 'CURATOR_RELEASE',
} as const;
export type CashOutConfirmedBy = typeof CashOutConfirmedBy[keyof typeof CashOutConfirmedBy];

/** One HelloAsso payout carrying donations of the campaign. */
export interface CampaignCashOut {
  idCashOut: number;
  /** HelloAsso's payout day (`YYYY-MM-DD`), null when HelloAsso gave none. */
  cashOutDate: string | null;
  /** Net amount of the whole payout (may include other payments), null until known. */
  netTotal: number | null;
  /** Part of the payout coming from this campaign. */
  campaignAmount: number;
  state: CashOutState;
  /** Day the donations become usable on their own (`YYYY-MM-DD`). */
  autoAvailableOn: string | null;
  /** When it became usable (ISO instant), null while in transit. */
  confirmedAt: string | null;
  confirmedBy: CashOutConfirmedBy | null;
}

/** Donations the CommonLink team made usable before HelloAsso paid them out. */
export interface CampaignManualRelease {
  releasedAt: string;
  campaignAmount: number;
}

/** `CampaignFundsResponse`: segments `atProvider + payoutPreparing + inTransit + available = raised − withdrawn`. */
export interface CampaignFunds {
  provider: FundsProvider;
  raised: number;
  atProvider: number;
  payoutPreparing: number;
  inTransit: number;
  available: number;
  withdrawn: number;
  paidOut: number;
  pendingPayouts: number;
  /** What a new payment can use (same figure as the backend's creation check), never negative. */
  disbursable: number;
  cashOuts: CampaignCashOut[];
  manualReleases: CampaignManualRelease[];
  /** End of the last HelloAsso tracking pass (ISO instant), null before the first one. */
  lastSyncAt: string | null;
  syncError: boolean;
  syncErrorCode: string | null;
  /** From when a refresh will run a new pass (ISO instant), null when possible now. */
  nextRefreshAvailableAt: string | null;
}

/** Response of `POST /api/association/cash-outs/{idCashOut}/receipt`. */
export interface CashOutReceiptResponse {
  idCashOut: number;
  netTotal: number | null;
  donations: { donationId: string; campaignId: string; amount: number; availableAt: string | null; confirmationMode: CashOutConfirmedBy | null }[];
}

/** `code` values of the funds-related refusals (ProblemDetail `code`). */
export const FundsErrorCode = {
  FUNDS_NOT_AVAILABLE: 'FUNDS_NOT_AVAILABLE',
  CASH_OUT_NOT_CASHED_OUT: 'CASH_OUT_NOT_CASHED_OUT',
  HELLOASSO_NOT_LINKED: 'HELLOASSO_NOT_LINKED',
} as const;
export type FundsErrorCode = typeof FundsErrorCode[keyof typeof FundsErrorCode];

/** Body of a `409 FUNDS_NOT_AVAILABLE` payout refusal. */
export interface FundsNotAvailableBody {
  code: typeof FundsErrorCode.FUNDS_NOT_AVAILABLE;
  disbursable: number;
  inTransit: number;
  atProvider: number;
}
