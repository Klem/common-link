'use client';

import { useEffect, useState } from 'react';
import { useTranslations } from 'next-intl';
import { apiUrl } from '@/lib/api';
import { getWidget } from '@/lib/api/public';
import type { PublicWidgetDto } from '@/lib/api/public';
import { DonationForm } from '@/components/donation/DonationForm';

interface Props {
  widgetToken: string;
  sourceSite: string | null;
  locale: string;
}

export function EmbedDonateClient({ widgetToken, sourceSite, locale }: Props) {
  const t = useTranslations('widget');

  const [widget, setWidget] = useState<PublicWidgetDto | null>(null);
  const [widgetLoading, setWidgetLoading] = useState(true);
  const [widgetError, setWidgetError] = useState<string | null>(null);

  useEffect(() => {
    getWidget(widgetToken)
      .then(setWidget)
      .catch((err: unknown) => {
        const status =
          err && typeof err === 'object' && 'response' in err
            ? (err as { response?: { status?: number } }).response?.status
            : undefined;
        setWidgetError(status === 404 ? t('unavailable') : t('errors.submitFailed'));
      })
      .finally(() => setWidgetLoading(false));
  }, [widgetToken, t]);

  if (widgetLoading) {
    return (
      <div style={styles.container}>
        <p style={styles.muted}>{t('loading')}</p>
      </div>
    );
  }

  if (widgetError || !widget) {
    return (
      <div style={styles.container}>
        <p style={styles.error}>{widgetError ?? t('unavailable')}</p>
      </div>
    );
  }

  const progressPct = widget.goal > 0 ? Math.min((widget.raised / widget.goal) * 100, 100) : 0;

  return (
    <div style={styles.container}>
      {/* Campaign chrome */}
      {widget.campaignCoverImage && (
        <img
          src={apiUrl(widget.campaignCoverImage)}
          alt={widget.campaignName}
          style={styles.cover}
        />
      )}
      <div style={styles.campaignHeader}>
        <p style={styles.assocName}>{widget.associationName}</p>
        <h1 style={styles.campaignTitle}>
          {widget.campaignEmoji} {widget.campaignName}
        </h1>
        {widget.campaignDescription && (
          <p style={styles.campaignDesc}>{widget.campaignDescription}</p>
        )}
        <div style={styles.progressBar}>
          <div style={{ ...styles.progressFill, width: `${progressPct}%` }} />
        </div>
        <p style={styles.progressLabel}>
          <strong>{widget.raised.toLocaleString()} {widget.currency}</strong>{' '}
          {t('campaign.raised')} · {t('campaign.goal')} {widget.goal.toLocaleString()} {widget.currency}
        </p>
      </div>

      <DonationForm
        widgetToken={widgetToken}
        sourceSite={sourceSite}
        locale={locale}
        skin="default"
        remainingCapacity={widget.remainingCapacity}
      />
    </div>
  );
}

const styles = {
  container: {
    maxWidth: 480,
    margin: '0 auto',
    padding: '16px',
    fontFamily: 'var(--font-body)',
    color: 'var(--color-text)',
  },
  cover: {
    width: '100%',
    height: 160,
    objectFit: 'cover' as const,
    borderRadius: 8,
    marginBottom: 12,
  },
  campaignHeader: {
    marginBottom: 20,
  },
  assocName: {
    fontSize: 12,
    color: 'var(--color-text-2)',
    textTransform: 'uppercase' as const,
    letterSpacing: '0.05em',
    marginBottom: 4,
  },
  campaignTitle: {
    fontSize: 20,
    fontWeight: 700,
    marginBottom: 8,
  },
  campaignDesc: {
    fontSize: 14,
    color: 'var(--color-text-2)',
    marginBottom: 12,
  },
  progressBar: {
    height: 6,
    background: 'var(--color-bg-2)',
    borderRadius: 3,
    overflow: 'hidden' as const,
    marginBottom: 6,
  },
  progressFill: {
    height: '100%',
    background: 'var(--bright-teal)',
    borderRadius: 3,
    transition: 'width 0.3s',
  },
  progressLabel: {
    fontSize: 13,
    color: 'var(--color-text-2)',
  },
  muted: {
    color: 'var(--color-text-2)',
    fontSize: 14,
  },
  error: {
    color: 'var(--color-error)',
    fontSize: 14,
  },
} as const;
