'use client';

import { useTranslations } from 'next-intl';
import type { SectionVariance } from '@/types/reporting';

interface VarianceTableProps {
  sections: SectionVariance[];
  /** true = charges (expense) colour logic; false = produits (revenue) colour logic */
  isCharges: boolean;
}

const fmt = new Intl.NumberFormat('fr-FR', { style: 'currency', currency: 'EUR' });

function varianceColor(variance: number, isCharges: boolean): string {
  if (isCharges) {
    return variance > 0 ? 'var(--warm-coral)' : 'var(--teal-dark)';
  }
  return variance >= 0 ? 'var(--teal-dark)' : 'var(--warm-coral)';
}

/**
 * Table showing planned vs actual vs variance per budget section.
 * Colour logic: charges → over-spend is red; produits → under-collect is red.
 */
export function VarianceTable({ sections, isCharges }: VarianceTableProps) {
  const t = useTranslations('dashboard');

  if (sections.length === 0) {
    return (
      <p style={{ color: 'var(--slate-lavender)', fontSize: '13px', padding: '12px' }}>
        {t('reporting.tab.empty' as Parameters<typeof t>[0])}
      </p>
    );
  }

  return (
    <div className="tw">
      <table className="cm-table">
        <thead>
          <tr>
            <th>{t('reporting.tab.colItem' as Parameters<typeof t>[0])}</th>
            <th>{t('reporting.tab.colPlanned' as Parameters<typeof t>[0])}</th>
            <th>{t('reporting.tab.colActual' as Parameters<typeof t>[0])}</th>
            <th>{t('reporting.tab.colVariance' as Parameters<typeof t>[0])}</th>
            <th>%</th>
          </tr>
        </thead>
        <tbody>
          {sections.map((s) => {
            const pct = s.planned !== 0
              ? Math.round((s.variance / s.planned) * 100)
              : s.actual !== 0 ? 100 : 0;
            const color = varianceColor(s.variance, isCharges);
            const sign = s.variance >= 0 ? '+' : '';
            return (
              <tr key={s.sectionCode}>
                <td>{s.sectionName}</td>
                <td>{fmt.format(s.planned)}</td>
                <td>{fmt.format(s.actual)}</td>
                <td style={{ color }}>{sign}{fmt.format(s.variance)}</td>
                <td style={{ color }}>{sign}{pct}%</td>
              </tr>
            );
          })}
        </tbody>
      </table>
    </div>
  );
}
