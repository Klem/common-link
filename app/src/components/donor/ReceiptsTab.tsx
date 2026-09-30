'use client';

import { useTranslations } from 'next-intl';
import { StatCard, EmptyStateCard } from '@/components/dashboard';
import { useDonorReceipts } from '@/hooks/dashboard/useDonorReceipts';
import { downloadAnnualReceiptsSummary } from '@/lib/api/donor';
import { useToastStore } from '@/stores/toastStore';

function fmtEur(amount: number): string {
  return new Intl.NumberFormat('fr-FR', { style: 'currency', currency: 'EUR' }).format(amount);
}

async function handleDownload(year: number): Promise<void> {
  try {
    const blob = await downloadAnnualReceiptsSummary(year);
    const url = URL.createObjectURL(blob);
    const a = document.createElement('a');
    a.href = url;
    a.download = `recapitulatif-fiscal-${year}.pdf`;
    a.click();
    URL.revokeObjectURL(url);
  } catch {
    useToastStore.getState().addToast('error', 'errors.receiptDownloadFailed');
  }
}

/**
 * "Reçus fiscaux" tab: explanatory banner, 3 cumulative stats (summed over the years shown), and
 * one row per annual summary with a PDF download button.
 */
export function ReceiptsTab() {
  const t = useTranslations('dashboard.donor.receipts');
  const { years, isLoading, error } = useDonorReceipts();

  const totalAmount = years.reduce((sum, y) => sum + y.totalAmount, 0);
  const totalReceipts = years.reduce((sum, y) => sum + y.donationCount, 0);
  const totalDeduction = years.reduce((sum, y) => sum + y.estimatedDeduction, 0);

  return (
    <div className="flex flex-col gap-6">
      <div className="alert alert-info">
        <span className="alert-icon" aria-hidden="true">
          ℹ️
        </span>
        <div>{t('banner')}</div>
      </div>

      {isLoading ? (
        <p className="text-sm text-text-2" aria-live="polite">
          {t('loading')}
        </p>
      ) : error ? (
        <EmptyStateCard icon="⚠️" title={t('error')} subtitle={t('errorSubtitle')} />
      ) : years.length === 0 ? (
        <EmptyStateCard icon={t('empty.icon')} title={t('empty.title')} subtitle={t('empty.subtitle')} />
      ) : (
        <>
          <div className="grid grid-cols-1 sm:grid-cols-3 gap-4">
            <StatCard icon="💰" label={t('stats.totalReceipted')} value={fmtEur(totalAmount)} variant="teal" />
            <StatCard icon="🧾" label={t('stats.receiptCount')} value={totalReceipts} variant="indigo" />
            <StatCard
              icon="📋"
              label={t('stats.totalDeduction')}
              value={fmtEur(totalDeduction)}
              subLabel={t('stats.totalDeductionHint')}
              variant="amber"
            />
          </div>

          <div className="card card-no-hover">
            <div className="card-h">
              <span className="font-display font-bold text-sm">{t('list.title')}</span>
            </div>
            <div className="card-b">
              <ul className="flex flex-col gap-3">
                {years.map((y) => (
                  <li key={y.year} className="flex items-center justify-between text-sm">
                    <span>
                      <span className="font-display font-bold">{y.year}</span>{' '}
                      <span className="text-text-2 text-xs">
                        — {t('list.donationCount', { count: y.donationCount })} — {fmtEur(y.totalAmount)}
                      </span>
                    </span>
                    <button
                      type="button"
                      className="btn btn-ghost btn-sm"
                      onClick={() => handleDownload(y.year)}
                      aria-label={t('list.downloadAria', { year: y.year })}
                    >
                      {t('list.download')}
                    </button>
                  </li>
                ))}
              </ul>
            </div>
          </div>
        </>
      )}
    </div>
  );
}
