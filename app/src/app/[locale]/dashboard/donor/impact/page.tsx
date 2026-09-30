'use client';

import { useTranslations } from 'next-intl';
import { Topbar } from '@/components/dashboard';
import { ImpactGallery } from '@/components/donor/ImpactGallery';

export default function DonorImpactPage() {
  const t = useTranslations('dashboard.donor.impact');

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

        <ImpactGallery />
      </div>
    </div>
  );
}
