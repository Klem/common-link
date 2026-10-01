'use client';

import { useEffect, useState } from 'react';
import { useTranslations } from 'next-intl';
import { DonationHistoryTable } from '@/components/donor/DonationHistoryTable';
import { DonationFilters } from '@/components/donor/DonationFilters';
import { DonationTraceabilityModal } from '@/components/donor/DonationTraceabilityModal';
import { ReceiptsTab } from '@/components/donor/ReceiptsTab';
import { DonationCta } from '@/components/donor/DonationCta';
import { Topbar } from '@/components/dashboard';
import { useDonorDonations } from '@/hooks/dashboard/useDonorDonations';
import { useDonorStats } from '@/hooks/dashboard/useDonorStats';
import { useDonorAssociations } from '@/hooks/dashboard/useDonorAssociations';
import { useDonorRecommendations } from '@/hooks/dashboard/useDonorRecommendations';
import { getDonorDonationFilters, downloadDonationReceipt } from '@/lib/api/donor';
import { useToastStore } from '@/stores/toastStore';
import type { DonorDonationDto, DonorDonationFiltersDto } from '@/types/donor';

type DonationsTab = 'history' | 'receipts';

function fmtEur(amount: number): string {
  return new Intl.NumberFormat('fr-FR', { style: 'currency', currency: 'EUR' }).format(amount);
}

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
  const tStats = useTranslations('dashboard.donor.stats');
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
  const { stats, isLoading: statsLoading } = useDonorStats();
  const { associations } = useDonorAssociations();
  const { recommendations } = useDonorRecommendations();
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

  // Same global "Faire un don" fallback rule as the home page (sprint 4 §2.2): first eligible
  // supported association, falling back to the top recommendation. Never a dead link.
  const eligibleAssociation = associations.find((a) => a.donationUrl !== null);
  const topRecommendation = recommendations[0] ?? null;
  const globalDonationTarget = eligibleAssociation
    ? { name: eligibleAssociation.name, url: eligibleAssociation.donationUrl }
    : topRecommendation
      ? { name: topRecommendation.associationName, url: topRecommendation.donationUrl }
      : null;

  return (
    <div>
      <Topbar title={t('title')} />

      <div className="page">
        <div className="page-head">
          <div>
            <h1>{t('title')}</h1>
          </div>
          {globalDonationTarget && (
            <DonationCta
              associationName={globalDonationTarget.name}
              donationUrl={globalDonationTarget.url}
              className="btn btn-primary"
            />
          )}
        </div>

        <div className="parcours-tabs" role="tablist">
          <button
            type="button"
            role="tab"
            aria-selected={activeTab === 'history'}
            aria-controls="tab-historique"
            id="tab-btn-historique"
            className={`parcours-tab${activeTab === 'history' ? ' active' : ''}`}
            onClick={() => setActiveTab('history')}
          >
            <span aria-hidden="true">📋</span> {t('tabs.history')}
          </button>
          <button
            type="button"
            role="tab"
            aria-selected={activeTab === 'receipts'}
            aria-controls="tab-jalons"
            id="tab-btn-jalons"
            className={`parcours-tab${activeTab === 'receipts' ? ' active' : ''}`}
            onClick={() => setActiveTab('receipts')}
          >
            <span aria-hidden="true">🧾</span> {t('tabs.receipts')}
          </button>
        </div>

        {activeTab === 'history' && (
          <div id="tab-historique" role="tabpanel" aria-labelledby="tab-btn-historique">
            <div className="stats-grid">
              <div className="stat-card">
                <div className="stat-card-top">
                  <div className="stat-icon teal" aria-hidden="true">💰</div>
                </div>
                <div className="stat-value">
                  {statsLoading ? '—' : fmtEur(stats?.totalDonated ?? 0)}
                </div>
                <div className="stat-label">{tStats('totalDonated')}</div>
              </div>
              <div className="stat-card">
                <div className="stat-card-top">
                  <div className="stat-icon coral" aria-hidden="true">♥</div>
                </div>
                <div className="stat-value">
                  {statsLoading ? '—' : (stats?.donationCount ?? 0)}
                </div>
                <div className="stat-label">{tStats('donationCount')}</div>
              </div>
              <div className="stat-card">
                <div className="stat-card-top">
                  <div className="stat-icon amber" aria-hidden="true">🏢</div>
                </div>
                <div className="stat-value">
                  {statsLoading ? '—' : (stats?.associationCount ?? 0)}
                </div>
                <div className="stat-label">{tStats('associationCount')}</div>
              </div>
              <div className="stat-card">
                <div className="stat-card-top">
                  <div className="stat-icon indigo" aria-hidden="true">🧾</div>
                </div>
                <div className="stat-value">
                  {statsLoading ? '—' : fmtEur(stats?.estimatedTaxReduction ?? 0)}
                </div>
                <div className="stat-label">{tStats('estimatedTaxReduction')}</div>
              </div>
            </div>

            <div className="card card-no-hover">
              <div className="card-head">
                <h3>{t('tableLabel')}</h3>
                <DonationFilters
                  associations={filters.associations}
                  years={filters.years}
                  associationId={associationId}
                  year={year}
                  onAssociationChange={setAssociationId}
                  onYearChange={setYear}
                />
              </div>
              <div className="card-body flush">
                <DonationHistoryTable
                  donations={donations}
                  isLoading={isLoading}
                  error={error}
                  onDownloadReceipt={handleDownloadReceipt}
                  onOpenTraceability={(donation) => setTraceabilityDonationId(donation.id)}
                />

                {totalPages > 1 && (
                  <nav
                    className="flex items-center justify-center gap-2 py-4"
                    aria-label={t('pagination')}
                  >
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
          <div id="tab-jalons" role="tabpanel" aria-labelledby="tab-btn-jalons">
            <ReceiptsTab />
          </div>
        )}
      </div>
    </div>
  );
}
