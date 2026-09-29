'use client';

import { useTranslations } from 'next-intl';
import { EmptyStateCard } from '@/components/dashboard';
import { DonorAssociationCard } from '@/components/donor/DonorAssociationCard';
import { useDonorAssociations } from '@/hooks/dashboard/useDonorAssociations';

export default function DonorAssociationsPage() {
  const t = useTranslations('dashboard.donor.associations');
  const { associations, isLoading, error } = useDonorAssociations();

  return (
    <div>
      <div className="mb-8">
        <h1 className="font-display font-black text-2xl md:text-3xl">{t('title')}</h1>
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
        <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-4">
          {associations.map((association) => (
            <DonorAssociationCard key={association.associationId} association={association} />
          ))}
        </div>
      )}
    </div>
  );
}
