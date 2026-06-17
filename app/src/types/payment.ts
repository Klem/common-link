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
}

/** Aggregated KPIs for the Payments tab. */
export interface PayoutSummaryDto {
  confirmedAmount: number;
  confirmedCount: number;
  pendingAmount: number;
  txTotal: number;
  txConfirmed: number;
  availableBalance: number;
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
}
