'use client';

import { useTranslations } from 'next-intl';
import { EmptyStateCard, Topbar } from '@/components/dashboard';
import { DonorAssociationCard } from '@/components/donor/DonorAssociationCard';
import { useDonorAssociations } from '@/hooks/dashboard/useDonorAssociations';

export default function DonorAssociationsPage() {
  const t = useTranslations('dashboard.donor.associations');
  const { associations, isLoading, error } = useDonorAssociations();

  return (
    <div>
      <Topbar title={t('title')} />

      <div className="page">
        <div className="page-head">
          <div>
            <h1>{t('title')}</h1>
            <p>{t('subtitle')}</p>
          </div>
        </div>

        {isLoading ? (
          <p className="text-sm text-text-2" aria-live="polite">
            {t('loading')}
          </p>
        ) : error ? (
          <p className="text-sm text-coral" role="alert">
            {t('error')}
          </p>
        ) : associations.length === 0 ? (
          <EmptyStateCard
            icon={t('empty.icon')}
            title={t('empty.title')}
            subtitle={t('empty.subtitle')}
          />
        ) : (
          <div className="association-grid">
            {associations.map((association, index) => (
              <DonorAssociationCard
                key={association.associationId}
                association={association}
                colorIndex={index}
              />
            ))}
          </div>
        )}
      </div>
    </div>
  );
}
