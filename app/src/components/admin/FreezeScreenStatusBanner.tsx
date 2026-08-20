'use client';

import { useState, useEffect } from 'react';
import { useTranslations, useLocale } from 'next-intl';
import { getFreezeScreenStatus } from '@/lib/api/admin';
import { FreezeScreenStatus } from '@/types/admin';
import type { FreezeScreenStatusDto } from '@/types/admin';

interface Props {
  associationId: string;
  /** Called once the status is resolved (including NOT_PERFORMED). */
  onStatusLoaded?: (status: FreezeScreenStatus) => void;
}

const STYLE_BY_STATUS: Record<
  FreezeScreenStatus,
  { borderColor: string; background: string; labelColor: string }
> = {
  NOT_PERFORMED: {
    borderColor: 'var(--color-border)',
    background: 'var(--color-bg-2, rgba(0,0,0,0.03))',
    labelColor: 'var(--color-text-2)',
  },
  PASSED: {
    borderColor: 'rgba(39,174,96,0.4)',
    background: 'rgba(39,174,96,0.07)',
    labelColor: '#27ae60',
  },
  HIT: {
    borderColor: 'rgba(230,126,34,0.4)',
    background: 'rgba(230,126,34,0.07)',
    labelColor: '#e67e22',
  },
  // Neutral, not green: the register did match, the correspondence was simply ruled upon.
  HIT_CLEARED: {
    borderColor: 'var(--color-border)',
    background: 'var(--color-bg-2, rgba(0,0,0,0.03))',
    labelColor: 'var(--color-text-2)',
  },
  UNAVAILABLE: {
    borderColor: 'rgba(231,76,60,0.35)',
    background: 'rgba(231,76,60,0.06)',
    labelColor: 'var(--color-error)',
  },
};

export function FreezeScreenStatusBanner({ associationId, onStatusLoaded }: Props) {
  const tf = useTranslations('curator.freezeScreen');
  const locale = useLocale();
  const [data, setData] = useState<FreezeScreenStatusDto | null>(null);

  useEffect(() => {
    getFreezeScreenStatus(associationId)
      .then((dto) => {
        setData(dto);
        onStatusLoaded?.(dto.status);
      })
      .catch(() => {
        // Non-critical: silently omit the banner on error.
      });
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [associationId]);

  if (!data) return null;

  const { borderColor, background, labelColor } = STYLE_BY_STATUS[data.status];

  const formatDate = (iso: string) =>
    new Date(iso).toLocaleDateString(locale === 'fr' ? 'fr-FR' : 'en-GB', {
      day: '2-digit',
      month: 'short',
      year: 'numeric',
    });

  const datedStatus =
    data.status === FreezeScreenStatus.PASSED || data.status === FreezeScreenStatus.HIT_CLEARED;

  const label =
    datedStatus && data.checkedAt
      ? tf(data.status, { date: formatDate(data.checkedAt) })
      : tf(data.status);

  return (
    <div
      style={{
        border: `1px solid ${borderColor}`,
        background,
        borderRadius: 8,
        padding: '10px 14px',
        marginBottom: 16,
        display: 'flex',
        alignItems: 'center',
        gap: 8,
        fontSize: 13,
      }}
    >
      <span
        style={{
          fontWeight: 700,
          color: labelColor,
          whiteSpace: 'nowrap',
          flexShrink: 0,
        }}
      >
        {tf('title')}
      </span>
      <span style={{ color: labelColor }}>{label}</span>
    </div>
  );
}
