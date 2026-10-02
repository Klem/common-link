'use client';

import Link from 'next/link';
import { useLocale, useTranslations } from 'next-intl';
import { useMemo, useState } from 'react';
import { EmptyStateCard } from '@/components/dashboard';
import { ROUTES } from '@/lib/routes';
import type { DonorDonationDto } from '@/types/donor';

interface Props {
  donations: DonorDonationDto[];
  isLoading: boolean;
  error: string | null;
  onDownloadReceipt: (donation: DonorDonationDto) => void;
  onOpenTraceability: (donation: DonorDonationDto) => void;
}

const SortKey = {
  DATE: 'DATE',
  PROJECT: 'PROJECT',
  ASSOCIATION: 'ASSOCIATION',
  AMOUNT: 'AMOUNT',
  USED_PERCENT: 'USED_PERCENT',
} as const;
type SortKey = typeof SortKey[keyof typeof SortKey];

type SortDirection = 'asc' | 'desc';

function fmtEur(amount: number): string {
  return new Intl.NumberFormat('fr-FR', { style: 'currency', currency: 'EUR' }).format(amount);
}

function fmtDate(iso: string): string {
  return new Intl.DateTimeFormat('fr-FR', { day: '2-digit', month: 'short', year: 'numeric' }).format(
    new Date(iso),
  );
}

function usedPercent(donation: DonorDonationDto): number {
  return donation.amount > 0 ? Math.min(100, (donation.usedAmount / donation.amount) * 100) : 0;
}

function compareDonations(a: DonorDonationDto, b: DonorDonationDto, key: SortKey): number {
  switch (key) {
    case SortKey.DATE:
      return a.donatedAt.localeCompare(b.donatedAt);
    case SortKey.PROJECT:
      return a.campaignName.localeCompare(b.campaignName);
    case SortKey.ASSOCIATION:
      return a.associationName.localeCompare(b.associationName);
    case SortKey.AMOUNT:
      return a.amount - b.amount;
    case SortKey.USED_PERCENT:
      return usedPercent(a) - usedPercent(b);
  }
}

/**
 * Donation history — a semantic `<table>` at `md` and above, stacked cards below it.
 * Both layouts share the same data, the receipt-download action, the traceability action, and a
 * link from the campaign name to its "bilan de campagne" page.
 */
export function DonationHistoryTable({
  donations,
  isLoading,
  error,
  onDownloadReceipt,
  onOpenTraceability,
}: Props) {
  const t = useTranslations('dashboard.donor.donations');
  const locale = useLocale();
  const [sortKey, setSortKey] = useState<SortKey | null>(null);
  const [sortDirection, setSortDirection] = useState<SortDirection>('asc');

  const sortedDonations = useMemo(() => {
    if (!sortKey) return donations;
    const sorted = [...donations].sort((a, b) => compareDonations(a, b, sortKey));
    if (sortDirection === 'desc') sorted.reverse();
    return sorted;
  }, [donations, sortKey, sortDirection]);

  function handleSort(key: SortKey) {
    if (sortKey === key) {
      setSortDirection((d) => (d === 'asc' ? 'desc' : 'asc'));
    } else {
      setSortKey(key);
      setSortDirection('asc');
    }
  }

  function sortButton(key: SortKey, label: string) {
    const active = sortKey === key;
    const nextDirection = active && sortDirection === 'asc' ? 'desc' : 'asc';
    const aria = nextDirection === 'asc'
      ? t('table.sortAscendingAria', { column: label })
      : t('table.sortDescendingAria', { column: label });
    return (
      <button
        type="button"
        className={`th-sort${active ? ' active' : ''}`}
        onClick={() => handleSort(key)}
        aria-label={aria}
      >
        {label}
        <span className={`th-sort-chev${active && sortDirection === 'desc' ? ' desc' : ''}`} aria-hidden="true">
          ▲
        </span>
      </button>
    );
  }

  if (isLoading) {
    return (
      <p className="text-sm text-text-2 py-8 text-center" aria-live="polite">
        {t('loading')}
      </p>
    );
  }

  if (error) {
    return (
      <p className="text-sm text-coral py-8 text-center" role="alert">
        {t('error')}
      </p>
    );
  }

  if (donations.length === 0) {
    return (
      <EmptyStateCard icon="🎁" title={t('empty.title')} subtitle={t('empty.subtitle')} />
    );
  }

  return (
    <>
      {/* ── Desktop / tablet: semantic table ──────────────────────────────── */}
      <div className="hidden md:block tw">
        <table className="cm-table" aria-label={t('tableLabel')}>
          <caption className="sr-only">{t('tableLabel')}</caption>
          <thead>
            <tr>
              <th scope="col">{sortButton(SortKey.DATE, t('table.date'))}</th>
              <th scope="col">{sortButton(SortKey.PROJECT, t('table.project'))}</th>
              <th scope="col">{sortButton(SortKey.ASSOCIATION, t('table.association'))}</th>
              <th scope="col">{sortButton(SortKey.AMOUNT, t('table.amount'))}</th>
              <th scope="col">{sortButton(SortKey.USED_PERCENT, t('table.usedAmount'))}</th>
              <th scope="col">{t('table.traceability')}</th>
              <th scope="col">{t('table.receipt')}</th>
            </tr>
          </thead>
          <tbody>
            {sortedDonations.map((donation) => {
              const pct = usedPercent(donation);
              return (
                <tr key={donation.id}>
                  <td>{fmtDate(donation.donatedAt)}</td>
                  <td>
                    <Link href={`/${locale}${ROUTES.DONOR_CAMPAIGN_REPORT(donation.campaignId)}`}>
                      {donation.campaignEmoji} {donation.campaignName}
                    </Link>
                  </td>
                  <td>{donation.associationName}</td>
                  <td className="amount-teal">{fmtEur(donation.amount)}</td>
                  <td>
                    <span
                      className="badge badge-active"
                      style={{ background: `linear-gradient(to right, rgba(78,205,196,.12) ${pct}%, var(--white) ${pct}%)` }}
                    >
                      {t('table.usedAmountBadge', {
                        used: fmtEur(donation.usedAmount),
                        total: fmtEur(donation.amount),
                      })}
                    </span>
                  </td>
                  <td>
                    <button
                      type="button"
                      className="btn-icon"
                      onClick={() => onOpenTraceability(donation)}
                      aria-label={t('table.viewTraceabilityAria', {
                        date: fmtDate(donation.donatedAt),
                        association: donation.associationName,
                      })}
                    >
                      <span aria-hidden="true">🔍</span>
                    </button>
                  </td>
                  <td>
                    {donation.receiptAvailable ? (
                      <button
                        type="button"
                        className="btn-icon"
                        onClick={() => onDownloadReceipt(donation)}
                        aria-label={t('table.downloadReceiptAria', {
                          receiptNumber: donation.receiptNumber ?? donation.id,
                        })}
                      >
                        <span aria-hidden="true">📥</span>
                      </button>
                    ) : (
                      <span className="text-text-2 text-sm">{t('table.noReceipt')}</span>
                    )}
                  </td>
                </tr>
              );
            })}
          </tbody>
        </table>
      </div>

      {/* ── Mobile: stacked cards ──────────────────────────────────────────── */}
      <div className="flex flex-col gap-3 md:hidden donations-cards" aria-label={t('tableLabel')}>
        {sortedDonations.map((donation) => (
          <article key={donation.id} className="donation-card">
            <Link
              href={`/${locale}${ROUTES.DONOR_CAMPAIGN_REPORT(donation.campaignId)}`}
              className="donation-card-title"
            >
              {donation.campaignEmoji} {donation.campaignName}
            </Link>
            <div className="donation-card-asso">{donation.associationName}</div>
            <div className="donation-card-row">
              <span className="donation-card-label">{t('table.date')}</span>
              <span className="donation-card-value">{fmtDate(donation.donatedAt)}</span>
            </div>
            <div className="donation-card-row">
              <span className="donation-card-label">{t('table.amount')}</span>
              <span className="donation-card-amt">{fmtEur(donation.amount)}</span>
            </div>
            <div className="donation-card-row">
              <span className="donation-card-label">{t('table.usedAmount')}</span>
              <span className="donation-card-value">
                <span
                  className="badge badge-active"
                  style={{ background: `linear-gradient(to right, rgba(78,205,196,.12) ${usedPercent(donation)}%, var(--white) ${usedPercent(donation)}%)` }}
                >
                  {t('table.usedAmountBadge', {
                    used: fmtEur(donation.usedAmount),
                    total: fmtEur(donation.amount),
                  })}
                </span>
              </span>
            </div>
            <div className="donation-card-actions">
              <button
                type="button"
                className="btn btn-sm btn-ghost flex-1"
                onClick={() => onOpenTraceability(donation)}
                aria-label={t('table.viewTraceabilityAria', {
                  date: fmtDate(donation.donatedAt),
                  association: donation.associationName,
                })}
              >
                <span aria-hidden="true">🔍</span> {t('table.traceability')}
              </button>
              {donation.receiptAvailable ? (
                <button
                  type="button"
                  className="btn btn-sm btn-ghost flex-1"
                  onClick={() => onDownloadReceipt(donation)}
                  aria-label={t('table.downloadReceiptAria', {
                    receiptNumber: donation.receiptNumber ?? donation.id,
                  })}
                >
                  <span aria-hidden="true">📥</span> {t('table.receipt')}
                </button>
              ) : (
                <span className="text-text-2 text-sm flex-1 text-center">{t('table.noReceipt')}</span>
              )}
            </div>
          </article>
        ))}
      </div>
    </>
  );
}
