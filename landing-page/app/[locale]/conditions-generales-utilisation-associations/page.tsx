import { getTranslations } from 'next-intl/server';
import { LegalContent } from '@/components/layout/LegalContent';
import { LegalSubnav } from '@/components/layout/LegalSubnav';
import { LegalArticles } from '@/components/layout/LegalArticles';

export async function generateMetadata({ params }: { params: Promise<{ locale: string }> }) {
  const { locale } = await params;
  const t = await getTranslations({ locale, namespace: 'metadata.cguAssociations' });
  return {
    title: t('title'),
    description: t('description'),
    alternates: {
      canonical: 'https://www.common-link.org/conditions-generales-utilisation-associations',
      languages: { fr: '/conditions-generales-utilisation-associations', en: '/en/conditions-generales-utilisation-associations' },
    },
  };
}

export default async function CguAssociationsPage({ params }: { params: Promise<{ locale: string }> }) {
  const { locale } = await params;
  const t = await getTranslations({ locale, namespace: 'legal.cguAssociations' });

  return (
    <>
      <LegalSubnav active="cguAssociations" />
      <LegalContent title={t('title')} meta={t('lastUpdated')}>
        <p>{t('intro')}</p>
        <LegalArticles articles={t.raw('articles')} />
      </LegalContent>
    </>
  );
}
