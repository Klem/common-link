import { getTranslations } from 'next-intl/server';
import { LegalContent } from '@/components/layout/LegalContent';
import { LegalSubnav } from '@/components/layout/LegalSubnav';
import { LegalArticles } from '@/components/layout/LegalArticles';

export async function generateMetadata({ params }: { params: Promise<{ locale: string }> }) {
  const { locale } = await params;
  const t = await getTranslations({ locale, namespace: 'metadata.modelAgreement' });
  return {
    title: t('title'),
    description: t('description'),
    alternates: {
      canonical: 'https://www.common-link.org/contrat-type',
      languages: { fr: '/contrat-type', en: '/en/contrat-type' },
    },
  };
}

export default async function ModelAgreementPage({ params }: { params: Promise<{ locale: string }> }) {
  const { locale } = await params;
  const t = await getTranslations({ locale, namespace: 'legal.modelAgreement' });

  return (
    <>
      <LegalSubnav active="modelAgreement" />
      <LegalContent title={t('title')} meta={t('meta')}>
        <p>{t('intro')}</p>

        <h2>{t('parties.title')}</h2>
        <p>{t('parties.p1')}</p>
        <p>{t('parties.donor')}</p>
        <p>{t('parties.org')}</p>

        <h2>{t('intermediary.title')}</h2>
        <p>{t('intermediary.p1')}</p>
        <p>{t('intermediary.p2')}</p>

        <h2>{t('complaintsService.title')}</h2>
        <div className="contact-block">{t('complaintsService.p1')}</div>

        <LegalArticles articles={t.raw('articles')} />
      </LegalContent>
    </>
  );
}
