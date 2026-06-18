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
    return variance > 0 ? 'var(--color-coral)' : 'var(--color-green)';
  }
  return variance >= 0 ? 'var(--color-green)' : 'var(--color-coral)';
}

/**
 * Table showing planned vs actual vs variance per budget section.
 * Colour logic: charges → over-spend is red; produits → under-collect is red.
 */
export function VarianceTable({ sections, isCharges }: VarianceTableProps) {
  const t = useTranslations('dashboard');

  if (sections.length === 0) {
    return (
      <p className="text-[13px] text-[var(--color-text-2)] p-[12px]">
        {t('reporting.tab.empty' as Parameters<typeof t>[0])}
      </p>
    );
  }

  return (
    <div className="overflow-x-auto">
      <table className="w-full text-[12px]">
        <thead>
          <tr className="text-[var(--color-text-2)] border-b border-[var(--color-border)]">
            <th className="text-left py-[6px] pr-[8px] font-semibold">Poste</th>
            <th className="text-right py-[6px] px-[8px] font-semibold min-w-[70px]">
              {t('reporting.tab.colPlanned' as Parameters<typeof t>[0])}
            </th>
            <th className="text-right py-[6px] px-[8px] font-semibold min-w-[70px]">
              {t('reporting.tab.colActual' as Parameters<typeof t>[0])}
            </th>
            <th className="text-right py-[6px] px-[8px] font-semibold min-w-[70px]">
              {t('reporting.tab.colVariance' as Parameters<typeof t>[0])}
            </th>
            <th className="text-right py-[6px] pl-[8px] font-semibold min-w-[45px]">%</th>
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
              <tr
                key={s.sectionCode}
                className="border-b border-[var(--color-border)] last:border-0"
              >
                <td className="py-[8px] pr-[8px] text-[var(--color-text-2)]">{s.sectionName}</td>
                <td className="py-[8px] px-[8px] text-right">{fmt.format(s.planned)}</td>
                <td className="py-[8px] px-[8px] text-right font-semibold">{fmt.format(s.actual)}</td>
                <td className="py-[8px] px-[8px] text-right font-semibold" style={{ color }}>
                  {sign}{fmt.format(s.variance)}
                </td>
                <td className="py-[8px] pl-[8px] text-right text-[11px]" style={{ color }}>
                  {sign}{pct}%
                </td>
              </tr>
            );
          })}
        </tbody>
      </table>
    </div>
  );
}
