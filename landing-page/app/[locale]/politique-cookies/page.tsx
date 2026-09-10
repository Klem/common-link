import { getTranslations } from 'next-intl/server';
import { Link } from '@/i18n/navigation';
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
      <h2>{t('cookies.title')}</h2>
      <p>{t('cookies.p1')}</p>

      <h2>{t('deposited.title')}</h2>
      <p>{t('deposited.p1')}</p>
      <p>{t('deposited.p2')}</p>
      <p>{t('deposited.p3')}</p>
      <p>{t('deposited.p4')}</p>

      <h2>{t('notDeposited.title')}</h2>
      <p>{t('notDeposited.p1')}</p>

      <h2>{t('refuse.title')}</h2>
      <p>{t('refuse.p1')}</p>

      <h2>{t('logs.title')}</h2>
      <p>
        {t('logs.p1')}{' '}
        <Link href="/politique-confidentialite">{t('logs.linkLabel')}</Link>
      </p>

      <h2>{t('hostedPages.title')}</h2>
      <p>{t('hostedPages.p1')}</p>
      <p>{t('hostedPages.p2')}</p>

      <h2>{t('questions.title')}</h2>
      <p>{t('questions.p1')}</p>
      </LegalContent>
    </>
  );
}
