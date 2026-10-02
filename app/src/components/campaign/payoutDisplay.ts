import { PayoutUiState } from '@/types/payment';

/** Money as the association reads it on its statement. */
export function fmtEur(amount: number) {
  return new Intl.NumberFormat('fr-FR', { style: 'currency', currency: 'EUR' }).format(amount);
}

/** Short date, for the compact transaction lists under the breakdown donut. */
export function fmtDate(iso: string) {
  return new Intl.DateTimeFormat('fr-FR', { day: '2-digit', month: 'short' }).format(new Date(iso));
}

/**
 * Full instant of an operation — date and time to the second.
 *
 * Numeric and to the second wherever a payout is identified: two transfers to the same
 * beneficiary, for the same amount, on the same day are told apart by nothing else, and a bank
 * statement is reconciled on the instant rather than on the day.
 */
export function fmtDateTime(iso: string) {
  return new Intl.DateTimeFormat('fr-FR', { dateStyle: 'short', timeStyle: 'medium' })
    .format(new Date(iso));
}

/**
 * Visual treatment of a payout state's badge.
 *
 * Two signals, never one: a hue *and* a dot shape. A badge that differed only in colour would say
 * nothing to a colour-blind reader, and the previous chips — an hourglass and a cross at 13px —
 * were indistinguishable to everyone else.
 */
interface BadgeStyle {
  /** Modifier appended to `.badge`. */
  variant: string;
  /** True for a hollow ring rather than a filled disc. */
  hollow: boolean;
}

const BADGE_STYLES: Record<PayoutUiState, BadgeStyle> = {
  [PayoutUiState.CONFIRMED]: { variant: 'badge-active', hollow: false },
  [PayoutUiState.AUTHORISED]: { variant: 'badge-authorised', hollow: false },
  [PayoutUiState.IN_TRANSIT]: { variant: 'badge-transit', hollow: false },
  [PayoutUiState.AWAITING_AUTHORISATION]: { variant: 'badge-draft', hollow: true },
  [PayoutUiState.RETRYABLE]: { variant: 'badge-retry', hollow: false },
  [PayoutUiState.FAILED]: { variant: 'badge-urgent', hollow: false },
  [PayoutUiState.PENDING]: { variant: 'badge-ended', hollow: true },
};

/** CSS classes for a state's badge, `.badge` included. */
export function badgeClass(state: PayoutUiState): string {
  return `badge ${BADGE_STYLES[state].variant}`;
}

/** CSS classes for the dot inside a state's badge. */
export function badgeDotClass(state: PayoutUiState): string {
  return BADGE_STYLES[state].hollow ? 'badge-dot badge-dot-hollow' : 'badge-dot';
}

/**
 * States a payout can be filtered down to by the quick filter pills.
 *
 * `TODO` and `FAILED` overlap with nothing and with each other on purpose: a terminal refusal asks
 * for no click, while a payout whose authorisation is still owed — or whose last attempt failed —
 * is the only kind the association can actually act on.
 */
export const PayoutFilter = {
  ALL: 'ALL',
  TODO: 'TODO',
  FAILED: 'FAILED',
  PENDING: 'PENDING',
  DONE: 'DONE',
} as const;
export type PayoutFilter = (typeof PayoutFilter)[keyof typeof PayoutFilter];

/** Pill order, left to right. */
export const PAYOUT_FILTERS: readonly PayoutFilter[] = [
  PayoutFilter.ALL,
  PayoutFilter.TODO,
  PayoutFilter.FAILED,
  PayoutFilter.PENDING,
  PayoutFilter.DONE,
];

/** States each pill selects. `ALL` selects everything and is handled separately. */
const FILTER_STATES: Record<Exclude<PayoutFilter, 'ALL'>, readonly PayoutUiState[]> = {
  [PayoutFilter.TODO]: [PayoutUiState.RETRYABLE, PayoutUiState.AWAITING_AUTHORISATION],
  [PayoutFilter.FAILED]: [PayoutUiState.FAILED],
  [PayoutFilter.PENDING]: [
    PayoutUiState.AUTHORISED,
    PayoutUiState.IN_TRANSIT,
    PayoutUiState.PENDING,
  ],
  [PayoutFilter.DONE]: [PayoutUiState.CONFIRMED],
};

/** Whether a payout in `state` belongs under `filter`. */
export function matchesFilter(filter: PayoutFilter, state: PayoutUiState): boolean {
  return filter === PayoutFilter.ALL || FILTER_STATES[filter].includes(state);
}

/** Periods offered by the date filter, as a number of days back — `null` meaning no limit. */
export const PAYOUT_PERIODS: readonly { key: string; days: number | null }[] = [
  { key: 'all', days: null },
  { key: 'd30', days: 30 },
  { key: 'd90', days: 90 },
  { key: 'd365', days: 365 },
];

/** Journal columns that can be sorted. */
export const PayoutSort = {
  DATE: 'DATE',
  PAYEE: 'PAYEE',
  TYPE: 'TYPE',
  AMOUNT: 'AMOUNT',
  STATUS: 'STATUS',
} as const;
export type PayoutSort = (typeof PayoutSort)[keyof typeof PayoutSort];

/**
 * Builds the journal's CSV export.
 *
 * Semicolon-separated and BOM-prefixed: French Excel splits on `;` and reads a comma as a decimal
 * separator, and without the BOM every accented beneficiary name arrives mojibake.
 *
 * @param rows one entry per visible payout, already formatted for display.
 * @param headers translated column headers, in the same order as a row's fields.
 */
export function buildCsv(rows: readonly string[][], headers: readonly string[]): string {
  const escape = (v: string) => `"${v.replace(/"/g, '""')}"`;
  const lines = [headers, ...rows].map((r) => r.map(escape).join(';'));
  return `﻿${lines.join('\r\n')}\r\n`;
}

/** Hands the viewer a file to save, then releases the object URL. */
export function downloadCsv(content: string, filename: string): void {
  const url = URL.createObjectURL(new Blob([content], { type: 'text/csv;charset=utf-8;' }));
  const a = document.createElement('a');
  a.href = url;
  a.download = filename;
  a.click();
  URL.revokeObjectURL(url);
}
