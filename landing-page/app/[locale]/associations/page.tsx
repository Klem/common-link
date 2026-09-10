import { getTranslations, setRequestLocale } from 'next-intl/server';
import { AssociationHero } from '@/components/sections/AssociationHero';
import { AssociationFeatures } from '@/components/sections/AssociationFeatures';

export async function generateMetadata({ params }: { params: Promise<{ locale: string }> }) {
  const { locale } = await params;
  const t = await getTranslations({ locale, namespace: 'metadata.associations' });
  return {
    title: t('title'),
    description: t('description'),
    keywords: t('keywords'),
    alternates: {
      canonical: 'https://www.common-link.org/associations',
      languages: { fr: '/associations', en: '/associations' },
    },
  };
}

export default async function AssociationsPage({ params }: { params: Promise<{ locale: string }> }) {
  const { locale } = await params;
  setRequestLocale(locale);

  return (
    <main>
      <AssociationHero />
      <AssociationFeatures />
    </main>
  );
}
