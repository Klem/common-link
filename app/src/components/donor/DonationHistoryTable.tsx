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
      <div className="hidden md:block overflow-x-auto">
        <table aria-label={t('tableLabel')}>
          <caption className="sr-only">{t('tableLabel')}</caption>
          <thead>
            <tr>
              <th scope="col">{t('table.date')}</th>
              <th scope="col">{t('table.project')}</th>
              <th scope="col">{t('table.association')}</th>
              <th scope="col">{t('table.amount')}</th>
              <th scope="col">{t('table.usedAmount')}</th>
              <th scope="col">{t('table.receipt')}</th>
              <th scope="col">{t('table.traceability')}</th>
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
                <td>{fmtEur(donation.usedAmount)}</td>
                <td>
                  {donation.receiptAvailable ? (
                    <button
                      type="button"
                      className="btn btn-ghost btn-sm"
                      onClick={() => onDownloadReceipt(donation)}
                    >
                      {t('table.downloadReceipt')}
                    </button>
                  ) : (
                    <span className="text-text-2 text-sm">{t('table.noReceipt')}</span>
                  )}
                </td>
                <td>
                  <button
                    type="button"
                    className="btn btn-ghost btn-sm"
                    onClick={() => onOpenTraceability(donation)}
                  >
                    {t('table.viewTraceability')}
                  </button>
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>

      {/* ── Mobile: stacked cards ──────────────────────────────────────────── */}
      <ul className="md:hidden flex flex-col gap-3">
        {donations.map((donation) => (
          <li key={donation.id} className="card card-no-hover">
            <div className="card-body flex flex-col gap-2">
              <div className="flex items-center justify-between">
                <Link
                  href={`/${locale}${ROUTES.DONOR_CAMPAIGN_REPORT(donation.campaignId)}`}
                  className="font-display font-bold text-sm"
                >
                  {donation.campaignEmoji} {donation.campaignName}
                </Link>
                <span className="font-display font-bold text-sm">{fmtEur(donation.amount)}</span>
              </div>
              <div className="text-sm text-text-2">{donation.associationName}</div>
              <div className="flex items-center justify-between text-sm text-text-2">
                <span>{fmtDate(donation.donatedAt)}</span>
                <span>
                  {t('table.usedAmount')}: {fmtEur(donation.usedAmount)}
                </span>
              </div>
              <div className="flex items-center justify-between text-sm">
                {donation.receiptAvailable ? (
                  <button
                    type="button"
                    className="btn btn-ghost btn-sm"
                    onClick={() => onDownloadReceipt(donation)}
                  >
                    {t('table.downloadReceipt')}
                  </button>
                ) : (
                  <span className="text-text-2">{t('table.noReceipt')}</span>
                )}
                <button
                  type="button"
                  className="btn btn-ghost btn-sm"
                  onClick={() => onOpenTraceability(donation)}
                >
                  {t('table.viewTraceability')}
                </button>
              </div>
            </div>
          </li>
        ))}
      </ul>
    </>
  );
}
