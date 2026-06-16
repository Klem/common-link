'use client';

import { useLocale, useTranslations } from 'next-intl';
import type { MonthlyPoint } from '@/types/association';

interface DonationsBarChartProps {
  data: MonthlyPoint[];
}

function formatMonthLabel(month: string, locale: string): string {
  const [year, m] = month.split('-');
  const date = new Date(Number(year), Number(m) - 1, 1);
  return new Intl.DateTimeFormat(locale, { month: 'short' }).format(date);
}

/**
 * Bar chart showing confirmed donation amounts for the last 6 calendar months.
 * Uses CSS div bars with dynamic inline heights (permitted for calculated values).
 */
export function DonationsBarChart({ data }: DonationsBarChartProps) {
  const t = useTranslations('dashboard.association.home.chart');
  const locale = useLocale();

  const maxAmount = Math.max(...data.map((d) => d.amount), 1);

  return (
    <div className="card no-hover">
      <div className="card-h">
        <h3>{t('title')}</h3>
      </div>
      <div className="card-b">
        <div
          style={{
            height: '200px',
            display: 'flex',
            alignItems: 'flex-end',
            gap: '14px',
            padding: '0 8px',
          }}
        >
          {data.map((point) => {
            const heightPct = Math.max((point.amount / maxAmount) * 100, 1);
            return (
              <div
                key={point.month}
                style={{ flex: 1, display: 'flex', flexDirection: 'column', alignItems: 'center', gap: '6px' }}
              >
                <div
                  style={{
                    width: '100%',
                    height: `${heightPct}%`,
                    background: 'var(--bright-teal)',
                    borderRadius: '6px 6px 0 0',
                  }}
                  title={`${point.amount} €`}
                />
                <span style={{ fontSize: '11px', color: 'var(--slate-lavender)' }}>
                  {formatMonthLabel(point.month, locale)}
                </span>
              </div>
            );
          })}
        </div>
      </div>
    </div>
  );
}
