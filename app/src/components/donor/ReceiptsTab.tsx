'use client';

import { useTranslations } from 'next-intl';
import { EmptyStateCard } from '@/components/dashboard';
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
 * one row per annual summary with a PDF download button. Markup follows the maquette's own
 * (icon-less) 3-column stat-card variant for this tab (dashboard-donateur.html:1058-1062) —
 * different from the 4-column, icon'd stat cards on the Historique tab.
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
          🧾
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
          <div className="stats-grid" style={{ gridTemplateColumns: 'repeat(3, 1fr)' }}>
            <div className="stat-card">
              <div className="stat-value">{fmtEur(totalAmount)}</div>
              <div className="stat-label">{t('stats.totalReceipted')}</div>
            </div>
            <div className="stat-card">
              <div className="stat-value" style={{ color: 'var(--teal-dark)' }}>
                {fmtEur(totalDeduction)}
              </div>
              <div className="stat-label">{t('stats.totalDeduction')}</div>
            </div>
            <div className="stat-card">
              <div className="stat-value">{totalReceipts}</div>
              <div className="stat-label">{t('stats.receiptCount')}</div>
            </div>
          </div>

          <div className="card">
            <div className="card-head">
              <h3>{t('list.title')}</h3>
            </div>
            <div className="card-body">
              {years.map((y) => (
                <div key={y.year} className="receipt-item">
                  <div className="receipt-icon" aria-hidden="true">
                    📄
                  </div>
                  <div className="receipt-info">
                    <h4>{y.year}</h4>
                    <span>
                      {t('list.donationCount', { count: y.donationCount })} · {fmtEur(y.totalAmount)}
                    </span>
                  </div>
                  <button
                    type="button"
                    className="btn btn-sm btn-primary"
                    onClick={() => handleDownload(y.year)}
                    aria-label={t('list.downloadAria', { year: y.year })}
                  >
                    {t('list.download')}
                  </button>
                </div>
              ))}
            </div>
          </div>

          <p className="text-center text-xs" style={{ marginTop: 20, color: 'var(--slate-readable)' }}>
            {t('list.footnote')}
          </p>
        </>
      )}
    </div>
  );
}
