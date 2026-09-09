import { getTranslations } from 'next-intl/server';
import { Link } from '@/i18n/navigation';
import { LegalContent } from '@/components/layout/LegalContent';
import { LegalSubnav } from '@/components/layout/LegalSubnav';

export async function generateMetadata({ params }: { params: Promise<{ locale: string }> }) {
  const { locale } = await params;
  const t = await getTranslations({ locale, namespace: 'metadata.privacy' });
  return {
    title: t('title'),
    description: t('description'),
  alternates: {
    canonical: 'https://www.common-link.org/politique-confidentialite',
    languages: { fr: '/politique-confidentialite', en: '/en/politique-confidentialite' },
  },
  };
}

export default async function PolitiqueConfidentialitePage({ params }: { params: Promise<{ locale: string }> }) {
  const { locale } = await params;
  const t = await getTranslations({ locale, namespace: 'legal.politiqueConfidentialite' });

  return (
    <>
    <LegalSubnav active="privacy" />
    <LegalContent title={t('title')} meta={t('lastUpdated')}>
      <h2>{t('protection.title')}</h2>
      <p>{t('protection.p1')}</p>

      <h2>{t('controller.title')}</h2>
      <p>{t('controller.p1')}</p>
      <p>{t('controller.p2')}</p>

      <h2>{t('collected.title')}</h2>
      <h3>{t('collected.donation.title')}</h3>
      <p>{t('collected.donation.p1')}</p>
      <p>{t('collected.donation.p2')}</p>
      <p>{t('collected.donation.p3')}</p>

      <h3>{t('collected.org.title')}</h3>
      <p>{t('collected.org.p1')}</p>
      <p>{t('collected.org.p2')}</p>
      <p>{t('collected.org.p3')}</p>
      <p>{t('collected.org.p4')}</p>

      <h3>{t('collected.browsing.title')}</h3>
      <p>{t('collected.browsing.p1')}</p>
      <p>
        {t('collected.browsing.p2')}{' '}
        <Link href="/politique-cookies">{t('collected.browsing.linkLabel')}</Link>
      </p>

      <h2>{t('basis.title')}</h2>
      <p>{t('basis.p1')}</p>
      <p>{t('basis.p2')}</p>

      <h2>{t('retention.title')}</h2>
      <table>
        <thead>
          <tr>
            <th>{t('retention.col1')}</th>
            <th>{t('retention.col2')}</th>
          </tr>
        </thead>
        <tbody>
          <tr><td>{t('retention.row0col1')}</td><td>{t('retention.row0col2')}</td></tr>
          <tr><td>{t('retention.row1col1')}</td><td>{t('retention.row1col2')}</td></tr>
          <tr><td>{t('retention.row2col1')}</td><td>{t('retention.row2col2')}</td></tr>
          <tr><td>{t('retention.row3col1')}</td><td>{t('retention.row3col2')}</td></tr>
          <tr><td>{t('retention.row4col1')}</td><td>{t('retention.row4col2')}</td></tr>
        </tbody>
      </table>
      <p>{t('retention.p1')}</p>

      <h2>{t('recipients.title')}</h2>
      <p>{t('recipients.p1')}</p>
      <p>{t('recipients.p2')}</p>
      <p>{t('recipients.p3')}</p>
      <p>{t('recipients.p4')}</p>

      <h2>{t('automated.title')}</h2>
      <p>{t('automated.p1')}</p>

      <h2>{t('rights.title')}</h2>
      <p>{t('rights.p1')}</p>
      <p>{t('rights.p2')}</p>
      <p>{t('rights.p3')}</p>
      <p>
        {t('rights.p4')}{' '}
        <a href="https://www.cnil.fr" target="_blank" rel="noopener noreferrer">www.cnil.fr</a>
      </p>

      <h2>{t('hostedPages.title')}</h2>
      <p>{t('hostedPages.p1')}</p>
      <p>{t('hostedPages.p2')}</p>

      <h2>{t('changes.title')}</h2>
      <p>{t('changes.p1')}</p>
    </LegalContent>
    </>
  );
}
