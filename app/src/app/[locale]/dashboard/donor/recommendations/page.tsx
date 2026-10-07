'use client';

import { useTranslations } from 'next-intl';
import { Topbar } from '@/components/dashboard';
import { RecommendationsGallery } from '@/components/donor/RecommendationsGallery';

export default function DonorRecommendationsPage() {
  const t = useTranslations('dashboard.donor.recommendations');

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

        <RecommendationsGallery />
      </div>
    </div>
  );
}
