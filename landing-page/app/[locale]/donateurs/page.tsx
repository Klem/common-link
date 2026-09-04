import { getTranslations, setRequestLocale } from 'next-intl/server';
import { DonorHero } from '@/components/sections/DonorHero';
import { DonorJourney } from '@/components/sections/DonorJourney';
import { DonorSteps } from '@/components/sections/DonorSteps';

export async function generateMetadata({ params }: { params: Promise<{ locale: string }> }) {
  const { locale } = await params;
  const t = await getTranslations({ locale, namespace: 'metadata.donors' });
  return {
    title: t('title'),
    description: t('description'),
    keywords: t('keywords'),
    alternates: {
      canonical: 'https://www.common-link.org/donateurs',
      languages: { fr: '/donateurs', en: '/donors' },
    },
  };
}

export default async function DonorsPage({ params }: { params: Promise<{ locale: string }> }) {
  const { locale } = await params;
  setRequestLocale(locale);

  return (
    <main>
      <DonorHero />
      <DonorJourney />
      <DonorSteps />
    </main>
  );
}
