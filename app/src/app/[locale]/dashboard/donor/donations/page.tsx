'use client';

import { useEffect, useState } from 'react';
import { useTranslations } from 'next-intl';
import { DonationHistoryTable } from '@/components/donor/DonationHistoryTable';
import { DonationFilters } from '@/components/donor/DonationFilters';
import { DonationTraceabilityModal } from '@/components/donor/DonationTraceabilityModal';
import { ReceiptsTab } from '@/components/donor/ReceiptsTab';
import { useDonorDonations } from '@/hooks/dashboard/useDonorDonations';
import { getDonorDonationFilters, downloadDonationReceipt } from '@/lib/api/donor';
import { useToastStore } from '@/stores/toastStore';
import type { DonorDonationDto, DonorDonationFiltersDto } from '@/types/donor';

type DonationsTab = 'history' | 'receipts';

async function handleDownloadReceipt(donation: DonorDonationDto): Promise<void> {
  try {
    const blob = await downloadDonationReceipt(donation.id);
    const url = URL.createObjectURL(blob);
    const a = document.createElement('a');
    a.href = url;
    a.download = `recu-${donation.receiptNumber ?? donation.id}.pdf`;
    a.click();
    URL.revokeObjectURL(url);
  } catch {
    useToastStore.getState().addToast('error', 'errors.receiptDownloadFailed');
  }
}

export default function DonorDonationsPage() {
  const t = useTranslations('dashboard.donor.donations');
  const {
    donationsPage,
    page,
    associationId,
    year,
    isLoading,
    error,
    setPage,
    setAssociationId,
    setYear,
  } = useDonorDonations();
  const [filters, setFilters] = useState<DonorDonationFiltersDto>({ associations: [], years: [] });
  const [traceabilityDonationId, setTraceabilityDonationId] = useState<string | null>(null);
  const [activeTab, setActiveTab] = useState<DonationsTab>('history');

  useEffect(() => {
    let cancelled = false;
    getDonorDonationFilters()
      .then((data) => {
        if (!cancelled) setFilters(data);
      })
      .catch(() => {
        /* filters are a progressive enhancement — the page still works without them */
      });
    return () => {
      cancelled = true;
    };
  }, []);

  const donations = donationsPage?.content ?? [];
  const totalPages = donationsPage?.totalPages ?? 0;

  return (
    <div>
      <div className="mb-8">
        <h1 className="font-display font-black text-2xl md:text-3xl">{t('title')}</h1>
      </div>

      <div className="set-tabs">
        <button
          type="button"
          className={`set-tab${activeTab === 'history' ? ' active' : ''}`}
          onClick={() => setActiveTab('history')}
        >
          📜 {t('tabs.history')}
        </button>
        <button
          type="button"
          className={`set-tab${activeTab === 'receipts' ? ' active' : ''}`}
          onClick={() => setActiveTab('receipts')}
        >
          🧾 {t('tabs.receipts')}
        </button>
      </div>

      {activeTab === 'history' && (
        <div className="set-tab-content active">
          <div className="card card-no-hover">
            <div className="card-body flex flex-col gap-6">
              <DonationFilters
                associations={filters.associations}
                years={filters.years}
                associationId={associationId}
                year={year}
                onAssociationChange={setAssociationId}
                onYearChange={setYear}
              />

              <DonationHistoryTable
                donations={donations}
                isLoading={isLoading}
                error={error}
                onDownloadReceipt={handleDownloadReceipt}
                onOpenTraceability={(donation) => setTraceabilityDonationId(donation.id)}
              />

              {totalPages > 1 && (
                <nav className="flex items-center justify-center gap-2" aria-label={t('pagination')}>
                  <button
                    type="button"
                    className="btn btn-ghost btn-sm"
                    disabled={page === 0}
                    onClick={() => setPage(page - 1)}
                  >
                    {t('previous')}
                  </button>
                  <span className="text-sm text-text-2">
                    {t('pageOf', { page: page + 1, totalPages })}
                  </span>
                  <button
                    type="button"
                    className="btn btn-ghost btn-sm"
                    disabled={page >= totalPages - 1}
                    onClick={() => setPage(page + 1)}
                  >
                    {t('next')}
                  </button>
                </nav>
              )}
            </div>
          </div>

          <DonationTraceabilityModal
            donationId={traceabilityDonationId}
            onClose={() => setTraceabilityDonationId(null)}
          />
        </div>
      )}

      {activeTab === 'receipts' && (
        <div className="set-tab-content active">
          <ReceiptsTab />
        </div>
      )}
    </div>
  );
}
