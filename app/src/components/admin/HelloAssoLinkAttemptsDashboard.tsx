'use client';

import { useCallback, useEffect, useMemo, useState, type CSSProperties } from 'react';
import { useLocale, useTranslations } from 'next-intl';
import { getHelloAssoLinkAttemptStats, listHelloAssoLinkAttempts } from '@/lib/api/admin';
import {
  HelloAssoLinkOutcome,
  type HelloAssoLinkAttemptPage,
  type HelloAssoLinkAttemptRow,
  type HelloAssoLinkAttemptStats,
  type HelloAssoLinkOutcomeCount,
} from '@/types/admin';

/** Selectable periods, in days. */
const PERIODS = [7, 30, 90] as const;
type Period = typeof PERIODS[number];

/** Attempts per table page. */
const PAGE_SIZE = 20;

/**
 * Fixed color of each outcome (validated categorical palette, slots in enum order): a color follows its outcome,
 * never its rank, so filtering or an empty outcome never repaints the others.
 */
export const OUTCOME_COLORS: Record<HelloAssoLinkOutcome, string> = {
  [HelloAssoLinkOutcome.MATCHED]: '#257de0',
  [HelloAssoLinkOutcome.NAME_MISMATCH]: '#f47230',
  [HelloAssoLinkOutcome.RNA_MISMATCH]: '#00b894',
  [HelloAssoLinkOutcome.ORGANIZATION_ALREADY_LINKED]: '#f2a600',
  [HelloAssoLinkOutcome.INVALID_KEYS]: '#ee7fac',
  [HelloAssoLinkOutcome.ORGANIZATION_UNRESOLVED]: '#008a17',
  [HelloAssoLinkOutcome.HELLOASSO_UNAVAILABLE]: '#6a4ce2',
  [HelloAssoLinkOutcome.HELLOASSO_RATE_LIMITED]: '#ec4647',
};

const SortDirection = { ASC: 'ASC', DESC: 'DESC' } as const;
type SortDirection = typeof SortDirection[keyof typeof SortDirection];

/** Sortable columns of the outcome table. */
const OutcomeSortKey = { OUTCOME: 'OUTCOME', COUNT: 'COUNT' } as const;
type OutcomeSortKey = typeof OutcomeSortKey[keyof typeof OutcomeSortKey];

/** Sortable columns of the attempts table (sorts the loaded page; the server returns newest first). */
const AttemptSortKey = {
  DATE: 'DATE',
  ASSOCIATION: 'ASSOCIATION',
  OUTCOME: 'OUTCOME',
  ORGANIZATION: 'ORGANIZATION',
  ORGANIZATION_RNA: 'ORGANIZATION_RNA',
  ASSOCIATION_RNA: 'ASSOCIATION_RNA',
} as const;
type AttemptSortKey = typeof AttemptSortKey[keyof typeof AttemptSortKey];

/** Swatch color as a CSS variable: the only per-outcome (data-driven) style. */
const swatch = (outcome: HelloAssoLinkOutcome) => ({ '--hla-swatch': OUTCOME_COLORS[outcome] }) as CSSProperties;

/** `[from, to)` of the last [days] days, as ISO instants. */
function periodBounds(days: Period): { from: string; to: string } {
  const to = new Date();
  const from = new Date(to.getTime() - days * 24 * 60 * 60 * 1000);
  return { from: from.toISOString(), to: to.toISOString() };
}

/** SVG path of a donut segment from [start] to [end] (fractions of the turn). */
function arcPath(start: number, end: number, outer: number, inner: number): string {
  const point = (fraction: number, radius: number) => {
    const angle = fraction * 2 * Math.PI - Math.PI / 2;
    return `${(radius * Math.cos(angle)).toFixed(3)} ${(radius * Math.sin(angle)).toFixed(3)}`;
  };
  const large = end - start > 0.5 ? 1 : 0;
  return [
    `M ${point(start, outer)}`,
    `A ${outer} ${outer} 0 ${large} 1 ${point(end, outer)}`,
    `L ${point(end, inner)}`,
    `A ${inner} ${inner} 0 ${large} 0 ${point(start, inner)}`,
    'Z',
  ].join(' ');
}

interface DonutProps {
  stats: HelloAssoLinkAttemptStats;
  tooltip: (count: HelloAssoLinkOutcomeCount) => string;
  selected: HelloAssoLinkOutcome | null;
  onSelect: (outcome: HelloAssoLinkOutcome) => void;
  ariaLabel: string;
  centerLabel: string;
}

/**
 * Donut of the attempts per outcome. Outcomes with no attempt are not drawn (they stay in the outcome table, which is
 * the accessible table view). Each segment has a hover tooltip, a 2px surface gap, and filters the list when clicked.
 */
function OutcomeDonut({ stats, tooltip, selected, onSelect, ariaLabel, centerLabel }: DonutProps) {
  const drawn = stats.byOutcome.filter((c) => c.count > 0);
  let cursor = 0;
  return (
    <svg className="hla-donut" viewBox="-110 -110 220 220" role="img" aria-label={ariaLabel}>
      {drawn.map((c) => {
        const start = cursor;
        cursor += c.count / stats.total;
        // A single outcome fills the ring: an arc from 0 to 1 would collapse to nothing.
        const end = drawn.length === 1 ? 0.9999 : cursor;
        const dimmed = selected !== null && selected !== c.outcome;
        return (
          <path
            key={c.outcome}
            data-testid={`donut-${c.outcome}`}
            className={`hla-segment${dimmed ? ' dimmed' : ''}`}
            d={arcPath(start, end, 100, 64)}
            fill={OUTCOME_COLORS[c.outcome]}
            onClick={() => onSelect(c.outcome)}
          >
            <title>{tooltip(c)}</title>
          </path>
        );
      })}
      <text className="hla-donut-total" textAnchor="middle" dy="-0.1em">{stats.total}</text>
      <text className="hla-donut-caption" textAnchor="middle" dy="1.6em">{centerLabel}</text>
    </svg>
  );
}

/**
 * Curator screen of the associations' HelloAsso key checks: KPIs, attempts per outcome (donut + outcome table that is
 * also the accessible table view and the outcome filter), and the attempts themselves (server pages, newest first).
 */
export default function HelloAssoLinkAttemptsDashboard() {
  const t = useTranslations('admin.helloassoLinkAttempts');
  const locale = useLocale();
  const [period, setPeriod] = useState<Period>(30);
  const [outcome, setOutcome] = useState<HelloAssoLinkOutcome | null>(null);
  const [page, setPage] = useState(0);
  const [stats, setStats] = useState<HelloAssoLinkAttemptStats | null>(null);
  const [rows, setRows] = useState<HelloAssoLinkAttemptPage | null>(null);
  const [hasError, setHasError] = useState(false);
  const [outcomeSort, setOutcomeSort] = useState<{ key: OutcomeSortKey; direction: SortDirection } | null>(null);
  const [attemptSort, setAttemptSort] = useState<{ key: AttemptSortKey; direction: SortDirection } | null>(null);
  const bounds = useMemo(() => periodBounds(period), [period]);

  const outcomeLabel = useCallback((o: HelloAssoLinkOutcome) => t(`outcome.${o}`), [t]);

  useEffect(() => {
    let cancelled = false;
    setHasError(false);
    getHelloAssoLinkAttemptStats(bounds)
      .then((s) => { if (!cancelled) setStats(s); })
      .catch(() => { if (!cancelled) setHasError(true); });
    return () => { cancelled = true; };
  }, [bounds]);

  useEffect(() => {
    let cancelled = false;
    listHelloAssoLinkAttempts({ ...bounds, outcome, page, size: PAGE_SIZE })
      .then((r) => { if (!cancelled) setRows(r); })
      .catch(() => { if (!cancelled) setHasError(true); });
    return () => { cancelled = true; };
  }, [bounds, outcome, page]);

  const selectPeriod = (p: Period) => {
    setPeriod(p);
    setPage(0);
  };
  const toggleOutcome = (o: HelloAssoLinkOutcome) => {
    setOutcome((current) => (current === o ? null : o));
    setPage(0);
  };

  const sortedOutcomes = useMemo(() => {
    const counts = stats?.byOutcome ?? [];
    if (!outcomeSort) return counts;
    const sorted = [...counts].sort((a, b) =>
      outcomeSort.key === OutcomeSortKey.COUNT ? a.count - b.count : outcomeLabel(a.outcome).localeCompare(outcomeLabel(b.outcome)),
    );
    return outcomeSort.direction === SortDirection.DESC ? sorted.reverse() : sorted;
  }, [stats, outcomeSort, outcomeLabel]);

  const sortedAttempts = useMemo(() => {
    const content = rows?.content ?? [];
    if (!attemptSort) return content;
    const text = (r: HelloAssoLinkAttemptRow): string => {
      switch (attemptSort.key) {
        case AttemptSortKey.DATE: return r.attemptedAt;
        case AttemptSortKey.ASSOCIATION: return r.associationName;
        case AttemptSortKey.OUTCOME: return outcomeLabel(r.outcome);
        case AttemptSortKey.ORGANIZATION: return r.organizationName ?? '';
        case AttemptSortKey.ORGANIZATION_RNA: return r.organizationRna ?? '';
        case AttemptSortKey.ASSOCIATION_RNA: return r.associationRna ?? '';
      }
    };
    const sorted = [...content].sort((a, b) => text(a).localeCompare(text(b)));
    return attemptSort.direction === SortDirection.DESC ? sorted.reverse() : sorted;
  }, [rows, attemptSort, outcomeLabel]);

  function sortButton<K extends string>(
    key: K,
    label: string,
    current: { key: K; direction: SortDirection } | null,
    set: (next: { key: K; direction: SortDirection }) => void,
  ) {
    const active = current?.key === key;
    const next = active && current?.direction === SortDirection.ASC ? SortDirection.DESC : SortDirection.ASC;
    return (
      <button
        type="button"
        className={`th-sort${active ? ' active' : ''}`}
        onClick={() => set({ key, direction: next })}
        aria-label={next === SortDirection.ASC ? t('sortAscendingAria', { column: label }) : t('sortDescendingAria', { column: label })}
      >
        {label}
        <span className={`th-sort-chev${active && current?.direction === SortDirection.DESC ? ' desc' : ''}`} aria-hidden="true">
          ▲
        </span>
      </button>
    );
  }

  const matched = stats?.byOutcome.find((c) => c.outcome === HelloAssoLinkOutcome.MATCHED)?.count ?? 0;
  const percentOf = (count: number) => (stats && stats.total > 0 ? Math.round((count / stats.total) * 100) : null);
  const successRate = percentOf(matched);
  const formatDate = (iso: string) =>
    new Date(iso).toLocaleString(locale === 'fr' ? 'fr-FR' : 'en-GB', {
      day: '2-digit', month: 'short', year: 'numeric', hour: '2-digit', minute: '2-digit',
    });

  return (
    <div className="page">
      <div className="page-head">
        <div>
          <h1>{t('title')}</h1>
          <p className="hla-subtitle">{t('subtitle')}</p>
        </div>
      </div>

      <div className="camp-filter-bar">
        {PERIODS.map((p) => (
          <button
            key={p}
            type="button"
            className={`btn btn-sm ${period === p ? 'btn-primary' : 'btn-secondary'}`}
            aria-pressed={period === p}
            onClick={() => selectPeriod(p)}
          >
            {t('period', { days: p })}
          </button>
        ))}
      </div>

      {hasError && <p className="hla-error" role="alert">{t('error')}</p>}

      {stats && (
        <>
          <div className="hla-kpis">
            <div className="stat-card">
              <div className="stat-card-value" data-testid="kpi-total">{stats.total}</div>
              <div className="stat-card-label">{t('kpi.total')}</div>
            </div>
            <div className="stat-card">
              <div className="stat-card-value" data-testid="kpi-associations">{stats.associations}</div>
              <div className="stat-card-label">{t('kpi.associations')}</div>
            </div>
            <div className="stat-card">
              <div className="stat-card-value" data-testid="kpi-success">
                {successRate === null ? t('none') : t('percent', { value: successRate })}
              </div>
              <div className="stat-card-label">{t('kpi.successRate')}</div>
            </div>
          </div>

          {stats.total === 0 ? (
            <p className="cm-table-empty">{t('empty')}</p>
          ) : (
            <div className="cm-card hla-chart">
              <OutcomeDonut
                stats={stats}
                tooltip={(c) => t('chart.tooltip', { outcome: outcomeLabel(c.outcome), count: c.count, percent: percentOf(c.count) ?? 0 })}
                selected={outcome}
                onSelect={toggleOutcome}
                ariaLabel={t('chart.aria', { total: stats.total })}
                centerLabel={t('chart.center')}
              />
              <div className="tw hla-grow">
                <table className="cm-table">
                  <caption className="sr-only">{t('chart.caption')}</caption>
                  <thead>
                    <tr>
                      <th scope="col">{sortButton(OutcomeSortKey.OUTCOME, t('col.outcome'), outcomeSort, setOutcomeSort)}</th>
                      <th scope="col">{sortButton(OutcomeSortKey.COUNT, t('col.count'), outcomeSort, setOutcomeSort)}</th>
                      <th scope="col">{t('col.share')}</th>
                    </tr>
                  </thead>
                  <tbody>
                    {sortedOutcomes.map((c) => (
                      <tr key={c.outcome} className={outcome === c.outcome ? 'hla-selected' : undefined}>
                        <td>
                          <button
                            type="button"
                            className="hla-legend-btn"
                            onClick={() => toggleOutcome(c.outcome)}
                            aria-pressed={outcome === c.outcome}
                          >
                            <span className="hla-swatch" style={swatch(c.outcome)} aria-hidden="true" />
                            {outcomeLabel(c.outcome)}
                          </button>
                        </td>
                        <td>{c.count}</td>
                        <td>{c.count > 0 ? t('percent', { value: percentOf(c.count) ?? 0 }) : t('none')}</td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            </div>
          )}
        </>
      )}

      <div className="hla-list-head">
        <h2 className="cm-card-title">{t('list.title')}</h2>
        {outcome && (
          <button type="button" className="btn btn-sm btn-secondary" onClick={() => toggleOutcome(outcome)}>
            {t('list.clearFilter', { outcome: outcomeLabel(outcome) })}
          </button>
        )}
      </div>

      {rows && rows.content.length === 0 && <p className="cm-table-empty">{t('list.empty')}</p>}

      {rows && rows.content.length > 0 && (
        <div className="tw">
          <table className="cm-table">
            <caption className="sr-only">{t('list.title')}</caption>
            <thead>
              <tr>
                <th scope="col">{sortButton(AttemptSortKey.DATE, t('col.date'), attemptSort, setAttemptSort)}</th>
                <th scope="col">{sortButton(AttemptSortKey.ASSOCIATION, t('col.association'), attemptSort, setAttemptSort)}</th>
                <th scope="col">{sortButton(AttemptSortKey.OUTCOME, t('col.outcome'), attemptSort, setAttemptSort)}</th>
                <th scope="col">{sortButton(AttemptSortKey.ORGANIZATION, t('col.organization'), attemptSort, setAttemptSort)}</th>
                <th scope="col">{sortButton(AttemptSortKey.ORGANIZATION_RNA, t('col.organizationRna'), attemptSort, setAttemptSort)}</th>
                <th scope="col">{sortButton(AttemptSortKey.ASSOCIATION_RNA, t('col.associationRna'), attemptSort, setAttemptSort)}</th>
              </tr>
            </thead>
            <tbody>
              {sortedAttempts.map((r) => (
                <tr key={r.id}>
                  <td>{formatDate(r.attemptedAt)}</td>
                  <td>{r.associationName}</td>
                  <td>
                    <span className="hla-outcome">
                      <span className="hla-swatch" style={swatch(r.outcome)} aria-hidden="true" />
                      {outcomeLabel(r.outcome)}
                    </span>
                  </td>
                  <td>
                    {r.organizationName ?? t('none')}
                    {r.organizationType && <span className="hla-muted"> · {r.organizationType}</span>}
                  </td>
                  <td className="hla-mono">{r.organizationRna ?? t('list.missingRna')}</td>
                  <td className="hla-mono">{r.associationRna ?? t('list.missingRna')}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}

      {rows && rows.totalPages > 1 && (
        <div className="hla-pager">
          <button
            type="button"
            className="btn btn-sm btn-secondary"
            disabled={rows.page === 0}
            onClick={() => setPage((p) => p - 1)}
            aria-label={t('list.previous')}
          >
            ←
          </button>
          <span className="hla-muted">{t('list.pageOf', { current: rows.page + 1, total: rows.totalPages })}</span>
          <button
            type="button"
            className="btn btn-sm btn-secondary"
            disabled={rows.page + 1 >= rows.totalPages}
            onClick={() => setPage((p) => p + 1)}
            aria-label={t('list.next')}
          >
            →
          </button>
        </div>
      )}
    </div>
  );
}
