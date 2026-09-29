'use client';

import { useState } from 'react';
import { useTranslations } from 'next-intl';
import { apiUrl } from '@/lib/api';
import type { DonorAssociationDto } from '@/types/donor';

interface Props {
  association: DonorAssociationDto;
}

function fmtEur(amount: number): string {
  return new Intl.NumberFormat('fr-FR', { style: 'currency', currency: 'EUR' }).format(amount);
}

function fmtDate(iso: string | null): string {
  if (!iso) return '—';
  return new Intl.DateTimeFormat('fr-FR', { day: '2-digit', month: 'short', year: 'numeric' }).format(
    new Date(iso),
  );
}

function getInitials(name: string): string {
  const parts = name.trim().split(/\s+/);
  if (parts.length >= 2) return (parts[0][0] + parts[parts.length - 1][0]).toUpperCase();
  return parts[0]?.[0]?.toUpperCase() ?? '?';
}

/** A supported association, shown as a card with its logo (or initials fallback). */
export function DonorAssociationCard({ association }: Props) {
  const t = useTranslations('dashboard.donor.associations');
  const [logoFailed, setLogoFailed] = useState(false);

  return (
    <div className="card card-no-hover">
      <div className="card-body flex flex-col gap-4">
        <div className="flex items-center gap-3">
          {logoFailed ? (
            <div className="avatar avatar-md avatar-teal font-display font-extrabold" aria-hidden="true">
              {getInitials(association.name)}
            </div>
          ) : (
            <img
              src={apiUrl(`/api/public/associations/${association.associationId}/logo`)}
              alt=""
              className="avatar avatar-md object-cover"
              onError={() => setLogoFailed(true)}
            />
          )}
          <div>
            <p className="font-display font-bold text-base text-text leading-tight">
              {association.name}
            </p>
            {association.category && (
              <p className="text-xs text-text-2 mt-0.5">{association.category}</p>
            )}
          </div>
        </div>

        <dl className="grid grid-cols-2 gap-3 text-sm">
          <div>
            <dt className="text-text-2 text-xs">{t('totalDonated')}</dt>
            <dd className="font-display font-bold text-text">{fmtEur(association.totalDonated)}</dd>
          </div>
          <div>
            <dt className="text-text-2 text-xs">{t('campaignCount')}</dt>
            <dd className="font-display font-bold text-text">{association.campaignCount}</dd>
          </div>
          <div>
            <dt className="text-text-2 text-xs">{t('publishedPayoutCount')}</dt>
            <dd className="font-display font-bold text-text">{association.publishedPayoutCount}</dd>
          </div>
          <div>
            <dt className="text-text-2 text-xs">{t('lastDonationAt')}</dt>
            <dd className="font-display font-bold text-text">{fmtDate(association.lastDonationAt)}</dd>
          </div>
        </dl>
      </div>
    </div>
  );
}
