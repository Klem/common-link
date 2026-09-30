'use client';

import { useTranslations } from 'next-intl';
import { ImpactGallery } from '@/components/donor/ImpactGallery';

export default function DonorImpactPage() {
  const t = useTranslations('dashboard.donor.impact');

  return (
    <div>
      <div className="mb-8">
        <h1 className="font-display font-black text-2xl md:text-3xl">{t('title')}</h1>
        <p className="text-text-2 mt-1">{t('subtitle')}</p>
      </div>

      <ImpactGallery />
    </div>
  );
}
