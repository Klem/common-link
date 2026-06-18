'use client';

import { useTranslations } from 'next-intl';
import { useReporting } from '@/hooks/campaign/useReporting';
import { VarianceTable } from './VarianceTable';
import { Donut, DONUT_PALETTE } from '@/components/ui/Donut';
import type { CampaignDto } from '@/types/campaign';

interface Props {
  campaign: CampaignDto;
}

const fmt = new Intl.NumberFormat('fr-FR', { style: 'currency', currency: 'EUR' });

/**
 * Reporting tab for the campaign editor.
 * Displays planned vs actual budget variance (charges + produits) with
 * per-section tables and donut charts.
 */
export function CampaignReportingTab({ campaign }: Props) {
  const t = useTranslations('dashboard');
  const { data, isLoading, error } = useReporting(campaign.id);

  if (isLoading) {
    return (
      <div className="flex items-center justify-center p-[48px]">
        <div className="w-[32px] h-[32px] rounded-full border-2 border-[var(--color-green)]/30 border-t-[var(--color-green)] animate-spin" />
      </div>
    );
  }

  if (error || !data) {
    return (
      <div className="alert alert-error">
        <span className="alert-icon">⚠️</span>
        <div>{t('reporting.tab.loadError' as Parameters<typeof t>[0])}</div>
      </div>
    );
  }

  const { charges, produits, totals } = data;

  const chargeSlices = charges
    .filter((s) => s.actual > 0)
    .map((s, i) => ({ label: s.sectionName, value: s.actual, color: DONUT_PALETTE[i % DONUT_PALETTE.length] }));

  const produitSlices = produits
    .filter((s) => s.actual > 0)
    .map((s, i) => ({ label: s.sectionName, value: s.actual, color: DONUT_PALETTE[i % DONUT_PALETTE.length] }));

  const ecartCharges = totals.totalActualCharges - totals.totalPlannedCharges;
  const ecartProduits = totals.totalActualProduits - totals.totalPlannedProduits;

  return (
    <div className="flex flex-col gap-[24px]">
      {/* KPI stats */}
      <div className="cm-stats">
        <div className="cm-stat" style={{ borderColor: 'rgba(255,107,91,.2)' }}>
          <div className="cm-stat-icon">📉</div>
          <div className="cm-stat-lbl">{t('reporting.tab.statsChargesPlanned' as Parameters<typeof t>[0])}</div>
          <div className="cm-stat-val" style={{ color: 'var(--color-coral)' }}>
            {fmt.format(totals.totalPlannedCharges)}
          </div>
        </div>
        <div className="cm-stat">
          <div className="cm-stat-icon">📉</div>
          <div className="cm-stat-lbl">{t('reporting.tab.statsChargesActual' as Parameters<typeof t>[0])}</div>
          <div className="cm-stat-val" style={{ color: 'var(--color-coral)' }}>
            {fmt.format(totals.totalActualCharges)}
          </div>
          <div
            className="cm-stat-sub"
            style={{ color: ecartCharges > 0 ? 'var(--color-coral)' : 'var(--color-green)' }}
          >
            {ecartCharges >= 0 ? '+' : ''}{fmt.format(ecartCharges)}
          </div>
        </div>
        <div className="cm-stat" style={{ borderColor: 'rgba(78,205,196,.2)' }}>
          <div className="cm-stat-icon">📈</div>
          <div className="cm-stat-lbl">{t('reporting.tab.statsProduitsPlanned' as Parameters<typeof t>[0])}</div>
          <div className="cm-stat-val" style={{ color: 'var(--color-green)' }}>
            {fmt.format(totals.totalPlannedProduits)}
          </div>
        </div>
        <div className="cm-stat">
          <div className="cm-stat-icon">📈</div>
          <div className="cm-stat-lbl">{t('reporting.tab.statsProduitsActual' as Parameters<typeof t>[0])}</div>
          <div className="cm-stat-val" style={{ color: 'var(--color-green)' }}>
            {fmt.format(totals.totalActualProduits)}
          </div>
          <div
            className="cm-stat-sub"
            style={{ color: ecartProduits >= 0 ? 'var(--color-green)' : 'var(--color-coral)' }}
          >
            {ecartProduits >= 0 ? '+' : ''}{fmt.format(ecartProduits)}
          </div>
        </div>
      </div>

      {/* Variance tables */}
      <div className="row2">
        <div className="cm-card">
          <div className="cm-card-title" style={{ justifyContent: 'space-between' }}>
            <span>{t('reporting.tab.chargesTitle' as Parameters<typeof t>[0])}</span>
            <span className="text-[10px] text-[var(--color-text-2)] font-normal normal-case">
              {t('reporting.tab.colPlanned' as Parameters<typeof t>[0])} ·{' '}
              {t('reporting.tab.colActual' as Parameters<typeof t>[0])} ·{' '}
              {t('reporting.tab.colVariance' as Parameters<typeof t>[0])}
            </span>
          </div>
          <VarianceTable sections={charges} isCharges={true} />
        </div>
        <div className="cm-card">
          <div className="cm-card-title" style={{ justifyContent: 'space-between' }}>
            <span>{t('reporting.tab.produitsTitle' as Parameters<typeof t>[0])}</span>
            <span className="text-[10px] text-[var(--color-text-2)] font-normal normal-case">
              {t('reporting.tab.colPlanned' as Parameters<typeof t>[0])} ·{' '}
              {t('reporting.tab.colActual' as Parameters<typeof t>[0])} ·{' '}
              {t('reporting.tab.colVariance' as Parameters<typeof t>[0])}
            </span>
          </div>
          <VarianceTable sections={produits} isCharges={false} />
        </div>
      </div>

      {/* Donut charts */}
      <div className="row2">
        <div className="cm-card">
          <div className="cm-card-title">
            {t('reporting.tab.chargesDonutTitle' as Parameters<typeof t>[0])}
          </div>
          <div className="flex justify-center py-[16px]">
            <Donut slices={chargeSlices} />
          </div>
        </div>
        <div className="cm-card">
          <div className="cm-card-title">
            {t('reporting.tab.produitsDonutTitle' as Parameters<typeof t>[0])}
          </div>
          <div className="flex justify-center py-[16px]">
            <Donut slices={produitSlices} />
          </div>
        </div>
      </div>
    </div>
  );
}
