import { getTranslations } from 'next-intl/server';
import { Link } from '@/i18n/navigation';
import { LegalContent } from '@/components/layout/LegalContent';

export async function generateMetadata({ params }: { params: Promise<{ locale: string }> }) {
  const { locale } = await params;
  const t = await getTranslations({ locale, namespace: 'metadata.mentionsLegales' });
  return {
    title: t('title'),
    description: t('description'),
  };
}

export default async function MentionsLegalesPage({ params }: { params: Promise<{ locale: string }> }) {
  const { locale } = await params;
  const t = await getTranslations({ locale, namespace: 'legal.mentionsLegales' });

  return (
    <LegalContent title={t('title')} lastUpdated={t('lastUpdated')}>
      <p>{t('intro')}</p>

      <h2>{t('editor.title')}</h2>
      <div className="contact-block">
        <strong>{t('editor.name')}</strong><br />
        {t('editor.type')}<br />
        {t('editor.capital')}<br />
        {t('editor.address')}<br />
        {t('editor.siren')}<br />
        {t('editor.siret')}<br />
        {t('editor.rcs')}<br />
        {t('editor.tva')}<br />
        {t('editor.naf')}
      </div>
      <p>{t('editor.ess')}</p>
      <p>{t('editor.orias')}</p>

      <h2>{t('director.title')}</h2>
      <p>{t('director.content')}</p>

      <h2>{t('editorial.title')}</h2>
      <p>{t('editorial.content')}</p>

      <h2>{t('contact.title')}</h2>
      <div className="contact-block">
        Email : <a href={`mailto:${t('contact.email')}`}>{t('contact.email')}</a><br />
        {t('contact.phone')}<br />
        <a href={`mailto:${t('contact.legalEmail')}`}>{t('contact.legalEmail')}</a>
      </div>
      <p>{t('contact.response')}</p>

      <h2>{t('hosting.title')}</h2>
      <div className="contact-block">
        <strong>{t('hosting.name')}</strong><br />
        {t('hosting.type')}<br />
        {t('hosting.address')}<br />
        <a href={t('hosting.url')}>{t('hosting.url')}</a>
      </div>

      <h2>{t('domain.title')}</h2>
      <p>{t('domain.content')}</p>

      <h2>{t('ip.title')}</h2>
      <p>{t('ip.p1')}</p>
      <p>{t('ip.p2')}</p>

      <h2>{t('liability.title')}</h2>
      <p>{t('liability.p1')}</p>
      <p>{t('liability.p2')}</p>

      <h2>{t('privacy.title')}</h2>
      <p>
        {t('privacy.p1')}{' '}
        <Link href="/politique-confidentialite">{t('privacy.linkLabel')}</Link>
      </p>
      <p>{t('privacy.p2')}</p>

      <h2>{t('cookies.title')}</h2>
      <p>
        {t('cookies.p1')}{' '}
        <Link href="/politique-cookies">{t('cookies.linkLabel')}</Link>
      </p>
    </LegalContent>
  );
}
