'use client';

import { useCallback, useEffect, useLayoutEffect, useMemo, useRef, useState } from 'react';
import { useTranslations } from 'next-intl';
import {
  PAYOUT_FILTERS,
  PAYOUT_PERIODS,
  PayoutFilter,
  PayoutSort,
  badgeClass,
  badgeDotClass,
  buildCsv,
  downloadCsv,
  fmtDateTime,
  fmtEur,
  matchesFilter,
} from '@/components/campaign/payoutDisplay';
import {
  PayoutAction,
  PayoutUiState,
  payoutAction,
  payoutErrorMessageKey,
  payoutUiState,
} from '@/types/payment';
import type { PayoutDto } from '@/types/payment';

interface Props {
  payouts: PayoutDto[];
  isLoading: boolean;
  error: string | null;
  isSaving: boolean;
  awaitingReturnPayoutId: string | null;
  /** Human label of an accounting code. */
  typeLabel: (code: string) => string;
  onRetry: (payoutId: string) => void;
  /** Opens the detail panel on a payout. */
  onOpen: (payout: PayoutDto) => void;
}

const PAGE_SIZES = [25, 50, 100] as const;
const DAY_MS = 86_400_000;

/** The six columns, in order — drives both the colgroup and the resize handles. */
const COLUMN_KEYS = ['date', 'payee', 'type', 'amount', 'status', 'action'] as const;

/** Narrowest a column can be dragged to — below this its header label is unreadable. */
const MIN_COL_WIDTH = 64;
/** How much one arrow key press moves a column edge. */
const KEY_RESIZE_STEP = 16;

/** One journal line, with everything the filters, the sort and the export read precomputed. */
interface Row {
  payout: PayoutDto;
  /** Bridge's transaction id, or empty when no transfer has been ordered. */
  reference: string;
  typeLabel: string;
  state: PayoutUiState;
  stateLabel: string;
  /** Lower-cased concatenation of everything the text search matches against. */
  haystack: string;
}

type SortDir = 'asc' | 'desc';

/**
 * Full-width payment journal: filters, sortable table, pagination footer.
 *
 * Everything is computed over the campaign's whole history rather than a server page — a counter
 * reading "9 paiements affichés · 796,20 €" is what the association reconciles against its bank
 * statement, and it would be a lie over a twenty-row window. Paging happens at the very end, on
 * rows already searched, filtered and sorted.
 */
export function PaymentJournal({
  payouts, isLoading, error, isSaving, awaitingReturnPayoutId,
  typeLabel, onRetry, onOpen,
}: Props) {
  const t = useTranslations('dashboard.campaigns.payments');

  const [search, setSearch] = useState('');
  const [typeCode, setTypeCode] = useState('');
  const [period, setPeriod] = useState('all');
  const [filter, setFilter] = useState<PayoutFilter>(PayoutFilter.ALL);
  const [sort, setSort] = useState<PayoutSort>(PayoutSort.DATE);
  const [dir, setDir] = useState<SortDir>('desc');
  const [page, setPage] = useState(0);
  const [pageSize, setPageSize] = useState<number>(PAGE_SIZES[0]);

  const rows: Row[] = useMemo(
    () => payouts.map((p) => {
      const state = payoutUiState(p);
      const stateLabel = t(`state.${state}`);
      const label = typeLabel(p.typeCode);
      const reference = p.bridgePaymentTransactionId ?? '';
      return {
        payout: p,
        reference,
        typeLabel: label,
        state,
        stateLabel,
        haystack: [
          reference, p.payeeName, label, p.typeCode, stateLabel,
          // Both spellings: the association types what it reads on the row ("123,00") as readily
          // as what it typed in the form ("123").
          fmtEur(p.amount), String(p.amount),
        ].join(' ').toLowerCase(),
      };
    }),
    [payouts, typeLabel, t],
  );

  /** Accounting codes actually present, so the select never offers an empty filter. */
  const typeOptions = useMemo(() => {
    const seen = new Map<string, string>();
    rows.forEach((r) => seen.set(r.payout.typeCode, r.typeLabel));
    return [...seen.entries()].sort((a, b) => a[1].localeCompare(b[1], 'fr'));
  }, [rows]);

  /** Everything but the quick-filter pill — the set the pill counters are computed over. */
  const preFiltered = useMemo(() => {
    const needle = search.trim().toLowerCase();
    const days = PAYOUT_PERIODS.find((p) => p.key === period)?.days ?? null;
    const floor = days === null ? null : Date.now() - days * DAY_MS;
    return rows.filter((r) => {
      if (needle && !r.haystack.includes(needle)) return false;
      if (typeCode && r.payout.typeCode !== typeCode) return false;
      if (floor !== null && new Date(r.payout.createdAt).getTime() < floor) return false;
      return true;
    });
  }, [rows, search, typeCode, period]);

  const counts = useMemo(() => {
    const map = {} as Record<PayoutFilter, number>;
    PAYOUT_FILTERS.forEach((f) => {
      map[f] = preFiltered.filter((r) => matchesFilter(f, r.state)).length;
    });
    return map;
  }, [preFiltered]);

  const filtered = useMemo(
    () => preFiltered.filter((r) => matchesFilter(filter, r.state)),
    [preFiltered, filter],
  );

  const sorted = useMemo(() => {
    const sign = dir === 'asc' ? 1 : -1;
    const compare = (a: Row, b: Row) => {
      switch (sort) {
        case PayoutSort.PAYEE: return a.payout.payeeName.localeCompare(b.payout.payeeName, 'fr');
        case PayoutSort.TYPE: return a.typeLabel.localeCompare(b.typeLabel, 'fr');
        case PayoutSort.AMOUNT: return a.payout.amount - b.payout.amount;
        case PayoutSort.STATUS: return a.stateLabel.localeCompare(b.stateLabel, 'fr');
        default: return a.payout.createdAt.localeCompare(b.payout.createdAt);
      }
    };
    // Ties fall back to creation order, then id, so the order never depends on the array the API
    // returned — the reference cannot serve here: a payout never sent to a bank has none.
    return [...filtered].sort((a, b) => sign * compare(a, b)
      || a.payout.createdAt.localeCompare(b.payout.createdAt)
      || a.payout.id.localeCompare(b.payout.id));
  }, [filtered, sort, dir]);

  const displayedTotal = useMemo(
    () => filtered.reduce((sum, r) => sum + r.payout.amount, 0),
    [filtered],
  );

  const pageCount = Math.max(1, Math.ceil(sorted.length / pageSize));
  // A filter that shrinks the list can strand the viewer past the last page.
  const safePage = Math.min(page, pageCount - 1);
  const pageRows = sorted.slice(safePage * pageSize, safePage * pageSize + pageSize);

  // Written back, not just clamped for the render: otherwise clearing the filter would jump to the
  // page index the viewer was on before it, not the one they are looking at.
  useEffect(() => {
    if (page !== safePage) setPage(safePage);
  }, [page, safePage]);

  const hasActiveFilters = search !== '' || typeCode !== '' || period !== 'all'
    || filter !== PayoutFilter.ALL;

  /**
   * Each column's share of the table, summing to 1 — or null before the first measurement.
   *
   * Shares rather than pixels, and a sum that is always 1: the table is then laid out from
   * percentages of its own width, so no combination of resizes can push it past its container.
   * The journal never scrolls sideways, whatever the viewer drags.
   *
   * The first values are measured, not guessed: the table renders once under the browser's own
   * content-driven algorithm, and those widths become the starting shares. That is what keeps a
   * short beneficiary from sitting beside a hand's width of blank — a hardcoded percentage cannot
   * know how long an account line's label is.
   */
  const [colShares, setColShares] = useState<number[] | null>(null);
  const headRowRef = useRef<HTMLTableRowElement>(null);
  /** Detaches an in-progress drag — called on pointer release and on unmount. */
  const endDragRef = useRef<(() => void) | null>(null);

  useEffect(() => () => endDragRef.current?.(), []);

  /** Current rendered width of every column, read off the header row. */
  const measureColumns = useCallback(
    () => [...(headRowRef.current?.cells ?? [])].map((c) => c.getBoundingClientRect().width),
    [],
  );

  const hasRows = pageRows.length > 0;

  // Before paint, so the switch from the content-driven pass to the share-driven one is never
  // visible. Skipped where there is no layout to read — a server render, or a test environment —
  // and the table simply stays on the browser's own algorithm there.
  useLayoutEffect(() => {
    if (colShares || !hasRows) return;
    const widths = measureColumns();
    const total = widths.reduce((sum, w) => sum + w, 0);
    if (total <= 0) return;
    setColShares(widths.map((w) => w / total));
  }, [colShares, hasRows, measureColumns]);

  /**
   * Moves the edge between column `index` and the one after it.
   *
   * What one column gains the next one loses, so the shares still add up to 1 and the table keeps
   * the width it had. The move is clamped by both neighbours' minimum, which is why dragging past
   * the end simply stops rather than squeezing a column to nothing.
   *
   * @param index column whose trailing edge is moving.
   * @param base shares to move from — the ones held when the drag started.
   * @param deltaPx how far the edge has travelled, in pixels.
   */
  const resizeBy = useCallback((index: number, base: number[], deltaPx: number) => {
    const tableWidth = measureColumns().reduce((sum, w) => sum + w, 0);
    if (tableWidth <= 0 || index + 1 >= base.length) return;
    const min = MIN_COL_WIDTH / tableWidth;
    const delta = Math.max(
      -(base[index] - min),
      Math.min(deltaPx / tableWidth, base[index + 1] - min),
    );
    const next = [...base];
    next[index] = base[index] + delta;
    next[index + 1] = base[index + 1] - delta;
    setColShares(next);
  }, [measureColumns]);

  function startResize(index: number, e: React.PointerEvent<HTMLElement>) {
    // Without this the pointer down also reaches the sort button under the handle.
    e.preventDefault();
    e.stopPropagation();
    const widths = measureColumns();
    const total = widths.reduce((sum, w) => sum + w, 0);
    if (total <= 0) return;
    const base = colShares ?? widths.map((w) => w / total);
    const startX = e.clientX;
    setColShares(base);

    const onMove = (ev: PointerEvent) => resizeBy(index, base, ev.clientX - startX);
    const stop = () => {
      document.removeEventListener('pointermove', onMove);
      document.removeEventListener('pointerup', stop);
      document.removeEventListener('pointercancel', stop);
      endDragRef.current = null;
    };
    endDragRef.current = stop;
    document.addEventListener('pointermove', onMove);
    document.addEventListener('pointerup', stop);
    document.addEventListener('pointercancel', stop);
  }

  /** Keyboard equivalent of dragging a handle — a drag target is otherwise mouse-only. */
  function handleResizeKey(index: number, e: React.KeyboardEvent<HTMLElement>) {
    const step = e.key === 'ArrowLeft' ? -KEY_RESIZE_STEP : e.key === 'ArrowRight' ? KEY_RESIZE_STEP : 0;
    if (step === 0) return;
    e.preventDefault();
    const widths = measureColumns();
    const total = widths.reduce((sum, w) => sum + w, 0);
    if (total <= 0) return;
    resizeBy(index, colShares ?? widths.map((w) => w / total), step);
  }

  /** Re-sorts on a column, flipping direction when it is already the sorted one. */
  function handleSort(key: PayoutSort) {
    if (key === sort) {
      setDir((d) => (d === 'asc' ? 'desc' : 'asc'));
      return;
    }
    setSort(key);
    // Dates read newest-first, everything else reads A→Z / smallest-first.
    setDir(key === PayoutSort.DATE || key === PayoutSort.AMOUNT ? 'desc' : 'asc');
  }

  function resetFilters() {
    setSearch(''); setTypeCode(''); setPeriod('all'); setFilter(PayoutFilter.ALL); setPage(0);
  }

  /**
   * Exports every matching row — what the association reconciles against its bank statement, which
   * is also the only thing an export is for here. No free-text column: the columns are the ones a
   * statement line carries.
   */
  function handleExport() {
    const headers = [
      t('journal.csv.date'), t('journal.csv.reference'), t('journal.csv.payee'),
      t('journal.csv.type'), t('journal.csv.code'), t('journal.csv.amount'),
      t('journal.csv.status'),
    ];
    const body = sorted.map((r) => [
      fmtDateTime(r.payout.createdAt),
      r.reference,
      r.payout.payeeName,
      r.typeLabel,
      r.payout.typeCode,
      // Comma decimal, no currency symbol: a French spreadsheet reads this as a number.
      r.payout.amount.toFixed(2).replace('.', ','),
      r.stateLabel,
    ]);
    downloadCsv(buildCsv(body, headers), t('journal.csv.filename'));
  }

  const ariaSort = (key: PayoutSort) =>
    sort === key ? (dir === 'asc' ? 'ascending' : 'descending') : 'none';

  function sortButton(key: PayoutSort, labelKey: string) {
    return (
      <button type="button" className="pj-sort" onClick={() => handleSort(key)}>
        {t(labelKey)}
        <span className="pj-sort-arrow" aria-hidden="true">
          {sort === key ? (dir === 'asc' ? '▲' : '▼') : '▾'}
        </span>
      </button>
    );
  }

  /** Drag handle on a column's trailing edge. The last column has none — nothing follows it. */
  function resizeHandle(index: number, labelKey: string) {
    return (
      <span
        className="pj-resizer"
        role="separator"
        aria-orientation="vertical"
        aria-label={t('journal.resize', { column: t(labelKey) })}
        tabIndex={0}
        onPointerDown={(e) => startResize(index, e)}
        onKeyDown={(e) => handleResizeKey(index, e)}
      />
    );
  }

  return (
    <div className="cm-card pj-card">
      <div className="cm-card-title">{t('journal.title')}</div>

      {/* ── Band 1: filters ──────────────────────────────────────── */}
      <div className="pj-filters">
        <div className="filter-bar pj-filter-bar">
          <input
            className="cm-fi pj-search"
            type="search"
            placeholder={t('journal.searchPlaceholder')}
            aria-label={t('journal.searchPlaceholder')}
            value={search}
            onChange={(e) => { setSearch(e.target.value); setPage(0); }}
          />
          <select
            className="fsel"
            aria-label={t('journal.allTypes')}
            value={typeCode}
            onChange={(e) => { setTypeCode(e.target.value); setPage(0); }}
          >
            <option value="">{t('journal.allTypes')}</option>
            {typeOptions.map(([code, label]) => (
              <option key={code} value={code}>{label}</option>
            ))}
          </select>
          <select
            className="fsel"
            aria-label={t('journal.period.label')}
            value={period}
            onChange={(e) => { setPeriod(e.target.value); setPage(0); }}
          >
            {PAYOUT_PERIODS.map((p) => (
              <option key={p.key} value={p.key}>{t(`journal.period.${p.key}`)}</option>
            ))}
          </select>
          <button
            type="button"
            className="cm-btn cm-btn-ghost cm-btn-sm pj-export"
            onClick={handleExport}
            disabled={sorted.length === 0}
          >
            {t('journal.export')}
          </button>
        </div>

        <div className="pj-pills">
          {PAYOUT_FILTERS.map((f) => (
            <button
              key={f}
              type="button"
              className={`pj-pill${filter === f ? ' active' : ''}`}
              aria-pressed={filter === f}
              onClick={() => { setFilter(f); setPage(0); }}
            >
              {t(`journal.pills.${f}`)}
              <span className="pj-pill-count">{counts[f]}</span>
            </button>
          ))}
          <span className="pj-counter" aria-live="polite">
            {t('journal.counter', { count: filtered.length, total: fmtEur(displayedTotal) })}
          </span>
        </div>
      </div>

      {/* ── Band 2: the table ────────────────────────────────────── */}
      {isLoading ? (
        <div className="cm-loading-center">
          <div className="animate-spin spinner lg" />
        </div>
      ) : error ? (
        <p className="cm-error-center">{error}</p>
      ) : payouts.length === 0 ? (
        <p className="cm-empty-center">{t('history.empty')}</p>
      ) : (
        <>
          <div className="pj-scroll">
            <table className={`cm-table pj-table${colShares ? ' pj-table-sized' : ''}`}>
              {/*
                Percentages of the table, measured from one content-driven pass and then held: the
                six always add up to 100, so dragging an edge moves width between two neighbours
                instead of making the table wider. Nothing here can produce a horizontal scrollbar.
              */}
              <colgroup>
                {COLUMN_KEYS.map((key, i) => (
                  <col
                    key={key}
                    className={`pj-col-${key}`}
                    style={colShares ? { width: `${colShares[i] * 100}%` } : undefined}
                  />
                ))}
              </colgroup>
              <thead>
                <tr ref={headRowRef}>
                  <th aria-sort={ariaSort(PayoutSort.DATE)}>
                    {sortButton(PayoutSort.DATE, 'journal.col.date')}
                    {resizeHandle(0, 'journal.col.date')}
                  </th>
                  <th aria-sort={ariaSort(PayoutSort.PAYEE)}>
                    {sortButton(PayoutSort.PAYEE, 'journal.col.payee')}
                    {resizeHandle(1, 'journal.col.payee')}
                  </th>
                  <th aria-sort={ariaSort(PayoutSort.TYPE)}>
                    {sortButton(PayoutSort.TYPE, 'journal.col.type')}
                    {resizeHandle(2, 'journal.col.type')}
                  </th>
                  <th className="col-right" aria-sort={ariaSort(PayoutSort.AMOUNT)}>
                    {sortButton(PayoutSort.AMOUNT, 'journal.col.amount')}
                    {resizeHandle(3, 'journal.col.amount')}
                  </th>
                  <th aria-sort={ariaSort(PayoutSort.STATUS)}>
                    {sortButton(PayoutSort.STATUS, 'journal.col.status')}
                    {resizeHandle(4, 'journal.col.status')}
                  </th>
                  <th className="col-right pj-th-action">{t('journal.col.action')}</th>
                </tr>
              </thead>
              <tbody>
                {pageRows.map((r) => {
                  const p = r.payout;
                  const action = payoutAction(p, awaitingReturnPayoutId);
                  // A row whose action is a button has no chevron, so the row itself has to be the
                  // keyboard way into the detail panel — that panel holds the full refusal sentence.
                  return (
                    <tr
                      key={p.id}
                      className="pj-row"
                      tabIndex={0}
                      onClick={() => onOpen(p)}
                      onKeyDown={(e) => {
                        if (e.target !== e.currentTarget) return;
                        if (e.key === 'Enter' || e.key === ' ') { e.preventDefault(); onOpen(p); }
                      }}
                    >
                      <td>
                        <div className="pj-date">{fmtDateTime(p.createdAt)}</div>
                        {r.reference && <div className="mono pj-ref">{r.reference}</div>}
                      </td>
                      <td className="pj-payee">{p.payeeName}</td>
                      <td>
                        <div className="pj-type">{r.typeLabel}</div>
                        <div className="mono pj-code">{p.typeCode}</div>
                      </td>
                      <td className="col-right pj-amount">{fmtEur(p.amount)}</td>
                      <td>
                        <span className={badgeClass(r.state)}>
                          <span className={badgeDotClass(r.state)} aria-hidden="true" />
                          {r.stateLabel}
                        </span>
                        {/*
                          A badge saying "Refusé" does not tell the association what to do about it.
                          The sentence is translated from a stable code — never the stored string,
                          which mixes Bridge's bare ISO codes with our own English messages — and
                          clamped to one line, the panel holding it in full.
                        */}
                        {(r.state === PayoutUiState.FAILED || r.state === PayoutUiState.RETRYABLE)
                          && p.bridgeLastErrorCode != null && (
                          <div
                            className="pj-reason"
                            title={t(`history.${payoutErrorMessageKey(p.bridgeLastErrorCode)}`)}
                          >
                            {t(`history.${payoutErrorMessageKey(p.bridgeLastErrorCode)}`)}
                          </div>
                        )}
                      </td>
                      {/*
                        Stops the click from reaching the row: opening the detail panel and firing a
                        real bank transfer must not be the same gesture.
                      */}
                      <td className="col-right" onClick={(e) => e.stopPropagation()}>
                        {action === PayoutAction.AWAITING_RETURN ? (
                          <span className="cm-hint-sm">{t('history.awaitingBank')}</span>
                        ) : action === PayoutAction.RETRY ? (
                          /*
                            Nothing was debited and the payout was deliberately left retryable rather
                            than failed. Without this button that only means something in the
                            database: the association re-creates the payout instead, which is how
                            four identical rows appeared from one payment. The old link is dead by
                            then, so this asks for a fresh one rather than re-opening the stored URL.
                          */
                          <button
                            type="button"
                            className="cm-btn cm-btn-ghost cm-btn-sm pj-action"
                            title={t('history.retryHint')}
                            disabled={isSaving}
                            onClick={() => onRetry(p.id)}
                          >
                            {t('history.retry')}
                          </button>
                        ) : action === PayoutAction.AUTHORISE && p.bridgeCheckoutUrl ? (
                          <a
                            className="cm-btn cm-btn-ghost cm-btn-sm pj-action"
                            href={p.bridgeCheckoutUrl}
                            title={t('history.authoriseHint')}
                          >
                            {t('history.authorise')}
                          </a>
                        ) : (
                          <button
                            type="button"
                            className="pj-chevron"
                            aria-label={t('journal.openDetail', { reference: r.reference })}
                            onClick={() => onOpen(p)}
                          >
                            ›
                          </button>
                        )}
                      </td>
                    </tr>
                  );
                })}
              </tbody>
            </table>

            {pageRows.length === 0 && (
              <div className="empty-state pj-empty">
                <p className="pj-empty-title">{t('journal.noMatch.title')}</p>
                <p className="pj-empty-hint">{t('journal.noMatch.hint')}</p>
                {hasActiveFilters && (
                  <button type="button" className="cm-btn cm-btn-ghost cm-btn-sm" onClick={resetFilters}>
                    {t('journal.reset')}
                  </button>
                )}
              </div>
            )}
          </div>

          {/* ── Band 3: pagination ─────────────────────────────────── */}
          <div className="pj-foot">
            <span className="pj-range">
              {t('journal.range', {
                from: sorted.length === 0 ? 0 : safePage * pageSize + 1,
                to: Math.min((safePage + 1) * pageSize, sorted.length),
                total: sorted.length,
              })}
            </span>
            <div className="pj-foot-right">
              <label className="pj-perpage">
                {t('journal.perPage')}
                <select
                  className="fsel"
                  value={pageSize}
                  onChange={(e) => { setPageSize(Number(e.target.value)); setPage(0); }}
                >
                  {PAGE_SIZES.map((s) => <option key={s} value={s}>{s}</option>)}
                </select>
              </label>
              <div className="pager pj-pager">
                <button
                  type="button"
                  aria-label={t('journal.prev')}
                  disabled={safePage === 0}
                  onClick={() => setPage(safePage - 1)}
                >
                  ‹
                </button>
                <button
                  type="button"
                  aria-label={t('journal.next')}
                  disabled={safePage >= pageCount - 1}
                  onClick={() => setPage(safePage + 1)}
                >
                  ›
                </button>
              </div>
            </div>
          </div>
        </>
      )}
    </div>
  );
}
