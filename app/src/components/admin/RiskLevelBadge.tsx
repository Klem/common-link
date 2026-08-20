'use client';

import { useTranslations } from 'next-intl';
import { RiskLevel } from '@/types/admin';

const RISK_STYLE: Record<RiskLevel, React.CSSProperties> = {
  LOW: { background: 'rgba(39,174,96,0.12)', color: '#27ae60', border: '1px solid rgba(39,174,96,0.3)' },
  STANDARD: { background: 'rgba(41,128,185,0.12)', color: '#2980b9', border: '1px solid rgba(41,128,185,0.3)' },
  HIGH: { background: 'rgba(231,76,60,0.12)', color: 'var(--color-error)', border: '1px solid rgba(231,76,60,0.3)' },
};

interface Props {
  riskLevel: RiskLevel;
}

export function RiskLevelBadge({ riskLevel }: Props) {
  const t = useTranslations('curator.dossier');

  return (
    <span
      style={{
        display: 'inline-flex',
        alignItems: 'center',
        gap: 6,
        padding: '2px 10px',
        borderRadius: 12,
        fontSize: 12,
        fontWeight: 700,
        letterSpacing: '0.03em',
        ...RISK_STYLE[riskLevel],
      }}
    >
      <span style={{ fontSize: 10 }}>{t('risk.title')}</span>
      {t(`risk.${riskLevel}`)}
    </span>
  );
}
