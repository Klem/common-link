'use client';

import Link from 'next/link';
import { useLocale, useTranslations } from 'next-intl';
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

function fmtEur(amount: number): string {
  return new Intl.NumberFormat('fr-FR', { style: 'currency', currency: 'EUR' }).format(amount);
}

function fmtDate(iso: string): string {
  return new Intl.DateTimeFormat('fr-FR', { day: '2-digit', month: 'short', year: 'numeric' }).format(
    new Date(iso),
  );
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
        <table className="donations-table" aria-label={t('tableLabel')}>
          <caption className="sr-only">{t('tableLabel')}</caption>
          <thead>
            <tr>
              <th scope="col">{t('table.date')}</th>
              <th scope="col">{t('table.project')}</th>
              <th scope="col">{t('table.association')}</th>
              <th scope="col">{t('table.amount')}</th>
              <th scope="col">{t('table.usedAmount')}</th>
              <th scope="col">{t('table.traceability')}</th>
              <th scope="col">{t('table.receipt')}</th>
            </tr>
          </thead>
          <tbody>
            {donations.map((donation) => (
              <tr key={donation.id}>
                <td>{fmtDate(donation.donatedAt)}</td>
                <td>
                  <Link href={`/${locale}${ROUTES.DONOR_CAMPAIGN_REPORT(donation.campaignId)}`}>
                    {donation.campaignEmoji} {donation.campaignName}
                  </Link>
                </td>
                <td>{donation.associationName}</td>
                <td>{fmtEur(donation.amount)}</td>
                <td>
                  <span className="badge badge-active">
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
            ))}
          </tbody>
        </table>
      </div>

      {/* ── Mobile: stacked cards ──────────────────────────────────────────── */}
      <div className="flex flex-col gap-3 md:hidden donations-cards" aria-label={t('tableLabel')}>
        {donations.map((donation) => (
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
                <span className="badge badge-active">
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
