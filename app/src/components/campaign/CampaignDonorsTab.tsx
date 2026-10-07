'use client';

import { Fragment, useMemo, useState } from 'react';
import { useTranslations } from 'next-intl';
import { useCampaignDonors } from '@/hooks/campaign/useCampaignDonors';
import { DonorSort, SortDirection } from '@/types/donor-campaign';
import type { DonationDto } from '@/types/donor-campaign';
import type { CampaignDto } from '@/types/campaign';

interface Props {
  campaign: CampaignDto;
}

function fmtEur(amount: number) {
  return new Intl.NumberFormat('fr-FR', { style: 'currency', currency: 'EUR' }).format(amount);
}

function fmtDate(iso: string | null) {
  if (!iso) return '—';
  return new Intl.DateTimeFormat('fr-FR', { day: '2-digit', month: 'short' }).format(new Date(iso));
}

function fmtRef(ref: string) {
  return ref.length > 24 ? `${ref.slice(0, 12)}…${ref.slice(-8)}` : ref;
}

const AVATAR_COLORS = ['var(--bright-teal)', 'var(--deep-indigo)', 'var(--warm-coral)', '#b37800'];

function getInitials(name: string): string {
  return name
    .split(' ')
    .filter(Boolean)
    .slice(0, 2)
    .map((w) => w[0].toUpperCase())
    .join('');
}

function getAvatarBg(id: string): string {
  let hash = 0;
  for (let i = 0; i < id.length; i++) hash = (hash * 31 + id.charCodeAt(i)) & 0xffff;
  return AVATAR_COLORS[hash % AVATAR_COLORS.length];
}

function OnChainChip({ onChain }: { onChain: boolean }) {
  if (onChain) return <span className="chip green">✓ on-chain</span>;
  return <span className="chip yellow">⏳</span>;
}

const TxSortKey = {
  DATE: 'DATE',
  AMOUNT: 'AMOUNT',
  REF: 'REF',
  ON_CHAIN: 'ON_CHAIN',
} as const;
type TxSortKey = typeof TxSortKey[keyof typeof TxSortKey];

function compareDonations(a: DonationDto, b: DonationDto, key: TxSortKey): number {
  switch (key) {
    case TxSortKey.DATE:
      return a.createdAt.localeCompare(b.createdAt);
    case TxSortKey.AMOUNT:
      return a.amount - b.amount;
    case TxSortKey.REF:
      return a.providerRef.localeCompare(b.providerRef);
    case TxSortKey.ON_CHAIN:
      return Number(a.onChain) - Number(b.onChain);
  }
}

/** Header sort button shared by the donor table (server sort) and the transactions sub-table (client sort). */
function SortButton({
  label,
  active,
  direction,
  onClick,
}: {
  label: string;
  active: boolean;
  direction: SortDirection;
  onClick: () => void;
}) {
  const t = useTranslations('dashboard.campaigns.donors');
  const nextDirection = active && direction === SortDirection.ASC ? SortDirection.DESC : SortDirection.ASC;
  const aria = nextDirection === SortDirection.ASC
    ? t('table.sortAscendingAria', { column: label })
    : t('table.sortDescendingAria', { column: label });
  return (
    <button type="button" className={`th-sort${active ? ' active' : ''}`} onClick={onClick} aria-label={aria}>
      {label}
      <span className={`th-sort-chev${active && direction === SortDirection.DESC ? ' desc' : ''}`} aria-hidden="true">
        ▲
      </span>
    </button>
  );
}

/**
 * Expanded content of a donor row: every donation of the donor as a sortable `cm-table`
 * (date · amount · reference · on-chain). Sort is local — the full history is already loaded.
 */
function DonorTransactions({ donations, isLoading }: { donations: DonationDto[]; isLoading: boolean }) {
  const t = useTranslations('dashboard.campaigns.donors');
  const [sortKey, setSortKey] = useState<TxSortKey | null>(null);
  const [sortDirection, setSortDirection] = useState<SortDirection>(SortDirection.ASC);

  const sorted = useMemo(() => {
    if (!sortKey) return donations;
    return [...donations].sort((a, b) => {
      const cmp = compareDonations(a, b, sortKey);
      return sortDirection === SortDirection.DESC ? -cmp : cmp;
    });
  }, [donations, sortKey, sortDirection]);

  function handleSort(key: TxSortKey): void {
    if (sortKey === key) {
      setSortDirection((d) => (d === SortDirection.ASC ? SortDirection.DESC : SortDirection.ASC));
    } else {
      setSortKey(key);
      setSortDirection(SortDirection.ASC);
    }
  }

  function header(label: string, key: TxSortKey) {
    return <SortButton label={label} active={sortKey === key} direction={sortDirection} onClick={() => handleSort(key)} />;
  }

  if (isLoading) {
    return (
      <div className="spinner-wrap">
        <div className="w-[20px] h-[20px] rounded-full border-2 border-[var(--bright-teal)]/30 border-t-[var(--bright-teal)] animate-spin" />
      </div>
    );
  }
  if (donations.length === 0) {
    return <p className="cm-table-empty">{t('detail.noTx')}</p>;
  }
  return (
    <div className="overflow-x-auto">
      <table className="cm-table">
        <caption className="sr-only">{t('detail.transactions')}</caption>
        <thead>
          <tr>
            <th scope="col">{header(t('tx.date'), TxSortKey.DATE)}</th>
            <th scope="col" className="col-right">{header(t('tx.amount'), TxSortKey.AMOUNT)}</th>
            <th scope="col">{header(t('tx.ref'), TxSortKey.REF)}</th>
            <th scope="col">{header(t('tx.onChain'), TxSortKey.ON_CHAIN)}</th>
          </tr>
        </thead>
        <tbody>
          {sorted.map((d) => (
            <tr key={d.id}>
              <td>{fmtDate(d.createdAt)}</td>
              <td className="amount-teal col-right">{fmtEur(d.amount)}</td>
              <td><code className="d-ref-code" title={d.providerRef}>{fmtRef(d.providerRef)}</code></td>
              <td><OnChainChip onChain={d.onChain} /></td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}

function Pager({
  page,
  totalPages,
  onPageChange,
}: {
  page: number;
  totalPages: number;
  onPageChange: (p: number) => void;
}) {
  if (totalPages <= 1) return null;

  const pages: (number | '…')[] = [];
  if (totalPages <= 7) {
    for (let i = 0; i < totalPages; i++) pages.push(i);
  } else {
    pages.push(0);
    if (page > 2) pages.push('…');
    for (let i = Math.max(1, page - 1); i <= Math.min(totalPages - 2, page + 1); i++) pages.push(i);
    if (page < totalPages - 3) pages.push('…');
    pages.push(totalPages - 1);
  }

  return (
    <div className="pager">
      <button type="button" disabled={page === 0} onClick={() => onPageChange(page - 1)}>←</button>
      {pages.map((p, i) =>
        p === '…' ? (
          <button key={`ellipsis-${i}`} type="button" disabled>…</button>
        ) : (
          <button
            key={p}
            type="button"
            className={p === page ? 'active' : undefined}
            onClick={() => onPageChange(p as number)}
          >
            {(p as number) + 1}
          </button>
        )
      )}
      <button type="button" disabled={page >= totalPages - 1} onClick={() => onPageChange(page + 1)}>→</button>
    </div>
  );
}

/**
 * Donors tab of the association campaign editor. Standard sortable `cm-table` (see app/CLAUDE.md "Tables"):
 * column headers drive the server-side sort, and each donor row expands via its chevron into the
 * donor's transactions sub-table. Only one donor is expanded at a time.
 */
export function CampaignDonorsTab({ campaign }: Props) {
  const t = useTranslations('dashboard.campaigns.donors');
  const {
    donorsPage,
    page,
    search,
    sort,
    direction,
    isLoading,
    error,
    openDonorId,
    donorDonations,
    isDonorLoading,
    setPage,
    setSearch,
    toggleSort,
    toggleDonor,
  } = useCampaignDonors(campaign.id);

  const donors = donorsPage?.content ?? [];
  const totalElements = donorsPage?.totalElements ?? 0;
  const totalPages = donorsPage?.totalPages ?? 0;

  const isTopFirst = sort === DonorSort.AMOUNT && direction === SortDirection.DESC && page === 0;
  const topDonor = isTopFirst && donors.length > 0 ? donors[0].displayName : '—';
  const avgAmount =
    donors.length > 0 ? donors.reduce((s, d) => s + d.totalAmount, 0) / donors.length : 0;

  function header(label: string, key: DonorSort) {
    return <SortButton label={label} active={sort === key} direction={direction} onClick={() => toggleSort(key)} />;
  }

  function handleExportCsv() {
    if (donors.length === 0) return;
    const header = ['Nom', 'Montant (€)', 'Transactions', 'Dernier don'];
    const rows = donors.map((d) => [
      `"${d.displayName.replace(/"/g, '""')}"`,
      d.totalAmount.toFixed(2),
      String(d.txCount),
      fmtDate(d.lastDonationAt),
    ]);
    const csv = [header, ...rows].map((r) => r.join(',')).join('\n');
    const url = URL.createObjectURL(new Blob([csv], { type: 'text/csv;charset=utf-8;' }));
    const a = document.createElement('a');
    a.href = url;
    a.download = `donateurs-${campaign.id}.csv`;
    a.click();
    URL.revokeObjectURL(url);
  }

  return (
    <div>
      {/* ── Stats ─────────────────────────────────────────────────────────── */}
      <div className="cm-stats">
        <div className="cm-stat">
          <div className="cm-stat-icon">👥</div>
          <div className="cm-stat-lbl">{t('stats.total')}</div>
          <div className="cm-stat-val val-teal">{isLoading ? '—' : totalElements}</div>
        </div>
        <div className="cm-stat">
          <div className="cm-stat-icon">💎</div>
          <div className="cm-stat-lbl">{t('stats.avg')}</div>
          <div className="cm-stat-val val-dark">{donors.length > 0 ? fmtEur(avgAmount) : '—'}</div>
        </div>
        <div className="cm-stat">
          <div className="cm-stat-icon">🏆</div>
          <div className="cm-stat-lbl">{t('stats.top')}</div>
          <div className="cm-stat-val val-amber">{isLoading ? '—' : topDonor}</div>
        </div>
        <div className="cm-stat">
          <div className="cm-stat-icon">💶</div>
          <div className="cm-stat-lbl">{t('stats.raised')}</div>
          <div className="cm-stat-val val-dark">{fmtEur(campaign.raised ?? 0)}</div>
        </div>
      </div>

      {/* ── Carte table ───────────────────────────────────────────────────── */}
      <div className="cm-card">
        <div className="filter-bar">
          <input
            className="cm-fi"
            type="text"
            placeholder={t('search.placeholder')}
            value={search}
            onChange={(e) => setSearch(e.target.value)}
          />
          <span className="filter-bar-count">
            {!isLoading && t('showing', { count: totalElements })}
          </span>
          <button type="button" className="cm-btn cm-btn-ghost cm-btn-sm" onClick={handleExportCsv}>
            {t('exportCsv')}
          </button>
        </div>

        {isLoading ? (
          <div className="spinner-wrap lg">
            <div className="w-[28px] h-[28px] rounded-full border-2 border-[var(--bright-teal)]/30 border-t-[var(--bright-teal)] animate-spin" />
          </div>
        ) : error ? (
          <p className="cm-table-error">
            {t('error')}
          </p>
        ) : donors.length === 0 ? (
          <p className="cm-table-empty-lg">
            {t('empty')}
          </p>
        ) : (
          <div className="overflow-x-auto tw">
            <table className="cm-table">
              <caption className="sr-only">{t('table.caption')}</caption>
              <thead>
                <tr>
                  <th scope="col" />
                  <th scope="col">{header(t('table.donor'), DonorSort.NAME)}</th>
                  <th scope="col" className="col-right">{header(t('table.amount'), DonorSort.AMOUNT)}</th>
                  <th scope="col" className="col-center">{header(t('table.transactions'), DonorSort.COUNT)}</th>
                  <th scope="col">{header(t('table.lastDonation'), DonorSort.DATE)}</th>
                </tr>
              </thead>
              <tbody>
                {donors.map((donor) => {
                  const isOpen = openDonorId === donor.donorId;
                  const detailId = `donor-detail-${donor.donorId}`;
                  return (
                    <Fragment key={donor.donorId}>
                      <tr>
                        <td>
                          <button
                            type="button"
                            className="btn-icon"
                            aria-expanded={isOpen}
                            aria-controls={detailId}
                            aria-label={`${isOpen ? t('table.hideDetail') : t('table.showDetail')} — ${donor.displayName}`}
                            onClick={() => toggleDonor(donor.donorId)}
                          >
                            <span className={`th-sort-chev${isOpen ? ' desc' : ''}`} aria-hidden="true">▲</span>
                          </button>
                        </td>
                        <td>
                          <div className="avatar-row">
                            <div
                              className="avatar avatar-xs"
                              style={{ background: getAvatarBg(donor.donorId) }}
                            >
                              {getInitials(donor.displayName)}
                            </div>
                            <div className="donor-name">{donor.displayName}</div>
                          </div>
                        </td>
                        <td className="amount-teal col-right">
                          {fmtEur(donor.totalAmount)}
                        </td>
                        <td className="tx-count col-center">
                          {donor.txCount}
                        </td>
                        <td className="col-muted">
                          {fmtDate(donor.lastDonationAt)}
                        </td>
                      </tr>
                      {isOpen && (
                        <tr id={detailId}>
                          <td colSpan={5}>
                            <DonorTransactions donations={donorDonations} isLoading={isDonorLoading} />
                          </td>
                        </tr>
                      )}
                    </Fragment>
                  );
                })}
              </tbody>
            </table>
          </div>
        )}

        <Pager page={page} totalPages={totalPages} onPageChange={setPage} />
      </div>
    </div>
  );
}
