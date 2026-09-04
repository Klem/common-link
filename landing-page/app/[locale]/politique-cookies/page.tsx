import { getTranslations } from 'next-intl/server';
import { LegalContent } from '@/components/layout/LegalContent';
import { LegalSubnav } from '@/components/layout/LegalSubnav';

export async function generateMetadata({ params }: { params: Promise<{ locale: string }> }) {
  const { locale } = await params;
  const t = await getTranslations({ locale, namespace: 'metadata.cookies' });
  return {
    title: t('title'),
    description: t('description'),
  alternates: {
    canonical: 'https://www.common-link.org/politique-cookies',
    languages: { fr: '/politique-cookies', en: '/en/politique-cookies' },
  },
  };
}

export default async function PolitiqueCookiesPage({ params }: { params: Promise<{ locale: string }> }) {
  const { locale } = await params;
  const t = await getTranslations({ locale, namespace: 'legal.politiqueCookies' });

  return (
    <>
      <LegalSubnav active="cookies" />
      <LegalContent title={t('title')} meta={t('lastUpdated')}>
      <p>{t('intro')}</p>

      <h2>{t('whatIs.title')}</h2>
      <p>{t('whatIs.p1')}</p>

      <h2>{t('used.title')}</h2>
      <p>{t('used.p1')}</p>
      <p>{t('used.p2')}</p>
      <ul>
        <li>{t('used.item0')}</li>
        <li>{t('used.item1')}</li>
        <li>{t('used.item2')}</li>
        <li>{t('used.item3')}</li>
        <li>{t('used.item4')}</li>
        <li>{t('used.item5')}</li>
      </ul>
      <p>{t('used.p3')}</p>

      <h2>{t('notUsed.title')}</h2>
      <p>{t('notUsed.p1')}</p>
      <ul>
        <li>{t('notUsed.item0')}</li>
        <li>{t('notUsed.item1')}</li>
        <li>{t('notUsed.item2')}</li>
        <li>{t('notUsed.item3')}</li>
        <li>{t('notUsed.item4')}</li>
        <li>{t('notUsed.item5')}</li>
        <li>{t('notUsed.item6')}</li>
        <li>{t('notUsed.item7')}</li>
        <li>{t('notUsed.item8')}</li>
      </ul>
      <p>{t('notUsed.p2')}</p>

      <h2>{t('payment.title')}</h2>
      <p>{t('payment.p1')}</p>

      <h2>{t('consent.title')}</h2>
      <p>{t('consent.p1')}</p>

      <h2>{t('duration.title')}</h2>
      <p>{t('duration.p1')}</p>

      <h2>{t('browser.title')}</h2>
      <p>{t('browser.p1')}</p>

      <h2>{t('changes.title')}</h2>
      <p>{t('changes.p1')}</p>
      </LegalContent>
    </>
  );
}
