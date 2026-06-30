import { getTranslations } from 'next-intl/server';
import { LegalContent } from '@/components/layout/LegalContent';

export async function generateMetadata({ params }: { params: Promise<{ locale: string }> }) {
  const { locale } = await params;
  const t = await getTranslations({ locale, namespace: 'metadata.contact' });
  return {
    title: t('title'),
    description: t('description'),
  };
}

export default async function ContactPage({ params }: { params: Promise<{ locale: string }> }) {
  const { locale } = await params;
  const t = await getTranslations({ locale, namespace: 'legal.contact' });

  return (
    <LegalContent title={t('title')}>
      <p>{t('intro')}</p>

      <h2>{t('general.title')}</h2>
      <div className="contact-block">
        Email : <a href={`mailto:${t('general.email')}`}>{t('general.email')}</a><br />
        Téléphone : {t('general.phone')}
      </div>
      <p>{t('general.response')}</p>

      <h2>{t('legal.title')}</h2>
      <p>{t('legal.intro')}</p>
      <div className="contact-block">
        <a href={`mailto:${t('legal.email')}`}>{t('legal.email')}</a>
      </div>

      <h2>{t('gdpr.title')}</h2>
      <p>{t('gdpr.intro')}</p>
      <div className="contact-block">
        <a href={`mailto:${t('gdpr.email')}`}>{t('gdpr.email')}</a>
      </div>
      <p>{t('gdpr.response')}</p>

      <h2>{t('postal.title')}</h2>
      <div className="contact-block">
        {t('postal.line1')}<br />
        {t('postal.line2')}<br />
        {t('postal.line3')}<br />
        {t('postal.line4')}
      </div>

      <h2>{t('company.title')}</h2>
      <div className="contact-block">
        <strong>{t('company.name')}</strong><br />
        {t('company.type')}<br />
        {t('company.capital')}<br />
        {t('company.siren')}<br />
        {t('company.rcs')}<br />
        {t('company.tva')}
      </div>
    </LegalContent>
  );
}
