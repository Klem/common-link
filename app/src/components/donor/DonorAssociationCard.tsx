'use client';

import { useState } from 'react';
import Link from 'next/link';
import { useLocale, useTranslations } from 'next-intl';
import { apiUrl } from '@/lib/api';
import { ROUTES } from '@/lib/routes';
import { DonationCta } from '@/components/donor/DonationCta';
import { DonorCampaignStatus, type DonorAssociationDto } from '@/types/donor';
import { useCauseLabel } from '@/lib/campaignCause';

interface Props {
  association: DonorAssociationDto;
  /** Rotates the pastel avatar color (a1/a2/a3), by position — not tied to the association's identity. */
  colorIndex: number;
}

function fmtEur(amount: number): string {
  return new Intl.NumberFormat('fr-FR', { style: 'currency', currency: 'EUR' }).format(amount);
}

function getInitials(name: string): string {
  const parts = name.trim().split(/\s+/);
  if (parts.length >= 2) return (parts[0][0] + parts[parts.length - 1][0]).toUpperCase();
  return parts[0]?.[0]?.toUpperCase() ?? '?';
}

/** A supported association, shown as a card with its logo (or initials fallback). */
export function DonorAssociationCard({ association, colorIndex }: Props) {
  const t = useTranslations('dashboard.donor.associations');
  const causeLabel = useCauseLabel();
  const locale = useLocale();
  const [logoFailed, setLogoFailed] = useState(false);
  const avatarVariant = `a${(colorIndex % 3) + 1}`;

  const campaignHref = association.campaignId
    ? `/${locale}${ROUTES.DONOR_CAMPAIGN_REPORT(association.campaignId)}`
    : null;

  return (
    <article className="card">
      <div className="card-body" style={{ padding: 28 }}>
        <div className="flex items-center gap-3.5 mb-4">
          {logoFailed ? (
            <div
              className={`asso-avatar asso-avatar-lg ${avatarVariant} font-display font-extrabold`}
              aria-hidden="true"
            >
              {getInitials(association.name)}
            </div>
          ) : (
            <img
              src={apiUrl(`/api/public/associations/${association.associationId}/logo`)}
              alt=""
              className="asso-avatar asso-avatar-lg object-cover"
              onError={() => setLogoFailed(true)}
            />
          )}
          <div>
            <h3 className="text-[17px] mb-0.5">{association.name}</h3>
            {association.category && (
              <span className="text-[13px]" style={{ color: 'var(--slate-readable)' }}>
                {causeLabel(association.category)}
              </span>
            )}
          </div>
        </div>

        <div className="asso-mini-stats">
          <div className="asso-mini-stat">
            <div className="asso-mini-stat-val">{fmtEur(association.totalDonated)}</div>
            <div className="asso-mini-stat-label">{t('totalDonated')}</div>
          </div>
          <div className="asso-mini-stat">
            <div className="asso-mini-stat-val teal">{association.publishedPayoutCount}</div>
            <div className="asso-mini-stat-label">{t('publishedPayoutCount')}</div>
          </div>
        </div>

        {association.campaignStatus !== DonorCampaignStatus.NONE && (
          <div className="text-[13px] mb-3 leading-relaxed" style={{ color: 'var(--slate-readable)' }}>
            <span className="freshness-tag mb-1.5">
              {association.campaignStatus === DonorCampaignStatus.LIVE
                ? t('campaignStatus.live')
                : t('campaignStatus.completed')}
            </span>
            {association.campaignName && (
              <>
                <br />
                {association.campaignStatus === DonorCampaignStatus.LIVE
                  ? t('campaignStatus.liveDescription', { campaignName: association.campaignName })
                  : t('campaignStatus.completedDescription', { campaignName: association.campaignName })}
              </>
            )}
          </div>
        )}

        <div className="flex gap-2">
          {campaignHref && association.campaignStatus !== DonorCampaignStatus.NONE && (
            <Link
              href={campaignHref}
              className="btn btn-sm btn-primary flex-1"
            >
              {association.campaignStatus === DonorCampaignStatus.LIVE
                ? t('campaignStatus.ctaLive')
                : t('campaignStatus.ctaCompleted')}
            </Link>
          )}
          <DonationCta
            associationName={association.name}
            donationUrl={association.donationUrl}
            className="btn btn-sm btn-secondary"
          />
        </div>
      </div>
    </article>
  );
}
