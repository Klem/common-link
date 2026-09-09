import { getTranslations } from 'next-intl/server';
import { Link } from '@/i18n/navigation';
import { LegalContent } from '@/components/layout/LegalContent';
import { LegalSubnav } from '@/components/layout/LegalSubnav';

export async function generateMetadata({ params }: { params: Promise<{ locale: string }> }) {
  const { locale } = await params;
  const t = await getTranslations({ locale, namespace: 'metadata.contact' });
  return {
    title: t('title'),
    description: t('description'),
  alternates: {
    canonical: 'https://www.common-link.org/contact',
    languages: { fr: '/contact', en: '/en/contact' },
  },
  };
}

export default async function ContactPage({ params }: { params: Promise<{ locale: string }> }) {
  const { locale } = await params;
  const t = await getTranslations({ locale, namespace: 'legal.contact' });

  return (
    <>
    <LegalSubnav active="contact" />
    <LegalContent title={t('title')} meta={t('meta')}>
      <h2>{t('details.title')}</h2>
      <p>{t('details.p1')}</p>
      <p>{t('details.p2')}</p>
      <p>{t('details.p3')}</p>
      <p>{t('details.p4')}</p>

      <h2>{t('complaint.title')}</h2>
      <p>{t('complaint.p1')}</p>
      <p>
        {t('complaint.p2')}{' '}
        <Link href="/reclamations">{t('complaint.linkLabel')}</Link>
        {t('complaint.p2After')}
      </p>

      <h2>{t('reportCampaign.title')}</h2>
      <p>{t('reportCampaign.p1')}</p>
    </LegalContent>
    </>
  );
}
