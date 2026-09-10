import { getTranslations, setRequestLocale } from 'next-intl/server';
import { TarifsHero } from '@/components/sections/TarifsHero';
import { TarifsAsso } from '@/components/sections/TarifsAsso';
import { TarifsDonor } from '@/components/sections/TarifsDonor';

export async function generateMetadata({ params }: { params: Promise<{ locale: string }> }) {
  const { locale } = await params;
  const t = await getTranslations({ locale, namespace: 'metadata.tarifs' });
  return {
    title: t('title'),
    description: t('description'),
    alternates: {
      canonical: 'https://www.common-link.org/tarifs',
      languages: { fr: '/tarifs', en: '/tarifs' },
    },
  };
}

export default async function TarifsPage({ params }: { params: Promise<{ locale: string }> }) {
  const { locale } = await params;
  setRequestLocale(locale);

  return (
    <main>
      <TarifsHero />
      <TarifsAsso />
      <TarifsDonor />
    </main>
  );
}
