import { getTranslations } from 'next-intl/server';
import { Link } from '@/i18n/navigation';
import { LegalContent } from '@/components/layout/LegalContent';

export async function generateMetadata({ params }: { params: Promise<{ locale: string }> }) {
  const { locale } = await params;
  const t = await getTranslations({ locale, namespace: 'metadata.cgu' });
  return {
    title: t('title'),
    description: t('description'),
  };
}

export default async function CGUPage({ params }: { params: Promise<{ locale: string }> }) {
  const { locale } = await params;
  const t = await getTranslations({ locale, namespace: 'legal.cgu' });

  return (
    <LegalContent title={t('title')} lastUpdated={t('lastUpdated')}>
      <p>{t('intro')}</p>

      <h2>{t('s1.title')}</h2>
      <p>{t('s1.intro')}</p>
      <p><strong>CommonLink</strong> : {t('s1.commonlink')}</p>
      <p><strong>Site</strong> : {t('s1.site')}</p>
      <p><strong>Plateforme</strong> : {t('s1.platform')}</p>
      <p><strong>Utilisateur</strong> : {t('s1.user')}</p>
      <p><strong>Donateur</strong> : {t('s1.donor')}</p>
      <p><strong>Organisme bénéficiaire</strong> : {t('s1.org')}</p>
      <p><strong>Campagne</strong> : {t('s1.campaign')}</p>

      <h2>{t('s2.title')}</h2>
      <p>{t('s2.p1')}</p>
      <p>{t('s2.p2')}</p>
      <ul>
        <li>{t('s2.item0')}</li>
        <li>{t('s2.item1')}</li>
        <li>{t('s2.item2')}</li>
        <li>{t('s2.item3')}</li>
        <li>{t('s2.item4')}</li>
        <li>{t('s2.item5')}</li>
        <li>{t('s2.item6')}</li>
      </ul>

      <h2>{t('s3.title')}</h2>
      <p>{t('s3.p1')}</p>
      <p>{t('s3.p2')}</p>
      <p>{t('s3.p3')}</p>
      <p>{t('s3.p4')}</p>

      <h2>{t('s4.title')}</h2>
      <p>{t('s4.p1')}</p>
      <p>{t('s4.p2')}</p>
      <p>{t('s4.p3')}</p>

      <h2>{t('s5.title')}</h2>
      <p>{t('s5.p1')}</p>
      <p>{t('s5.p2')}</p>

      <h2>{t('s6.title')}</h2>
      <p>{t('s6.p1')}</p>
      <p>{t('s6.p2')}</p>
      <p>{t('s6.p3')}</p>

      <h2>{t('s7.title')}</h2>
      <p>{t('s7.p1')}</p>
      <p>{t('s7.p2')}</p>

      <h2>{t('s8.title')}</h2>
      <p>{t('s8.p1')}</p>
      <p>{t('s8.p2')}</p>

      <h2>{t('s9.title')}</h2>
      <p>{t('s9.p1')}</p>
      <p>{t('s9.p2')}</p>

      <h2>{t('s10.title')}</h2>
      <p>{t('s10.p1')}</p>

      <h2>{t('s11.title')}</h2>
      <p>{t('s11.p1')}</p>
      <p>
        {t('s11.p2')}{' '}
        <Link href="/tarifs">{t('s11.tarifsLinkLabel')}</Link>
      </p>

      <h2>{t('s12.title')}</h2>
      <p>{t('s12.p1')}</p>
      <ul>
        <li>{t('s12.item0')}</li>
        <li>{t('s12.item1')}</li>
        <li>{t('s12.item2')}</li>
        <li>{t('s12.item3')}</li>
        <li>{t('s12.item4')}</li>
        <li>{t('s12.item5')}</li>
        <li>{t('s12.item6')}</li>
      </ul>
      <p>{t('s12.p2')}</p>

      <h2>{t('s13.title')}</h2>
      <p>{t('s13.p1')}</p>
      <p>{t('s13.p2')}</p>

      <h2>{t('s14.title')}</h2>
      <p>{t('s14.p1')}</p>
      <p>{t('s14.p2')}</p>

      <h2>{t('s15.title')}</h2>
      <p>{t('s15.p1')}</p>
      <p>{t('s15.p2')}</p>

      <h2>{t('s16.title')}</h2>
      <p>{t('s16.p1')}</p>

      <h2>{t('s17.title')}</h2>
      <p>{t('s17.p1')}</p>
      <p>{t('s17.p2')}</p>

      <h2>{t('s18.title')}</h2>
      <p>{t('s18.p1')}</p>

      <h2>{t('s19.title')}</h2>
      <p>{t('s19.p1')}</p>

      <h2>{t('s20.title')}</h2>
      <p>
        {t('s20.p1')}{' '}
        <Link href="/politique-confidentialite">{t('s20.privacyLinkLabel')}</Link>
      </p>
      <p>{t('s20.p2')}</p>

      <h2>{t('s21.title')}</h2>
      <p>
        {t('s21.p1')}{' '}
        <Link href="/politique-cookies">{t('s21.cookiesLinkLabel')}</Link>
      </p>

      <h2>{t('s22.title')}</h2>
      <p>{t('s22.p1')}</p>

      <h2>{t('s23.title')}</h2>
      <p>{t('s23.p1')}</p>

      <h2>{t('s24.title')}</h2>
      <p>{t('s24.p1')}</p>

      <h2>{t('s25.title')}</h2>
      <div className="contact-block">
        <a href={`mailto:${t('s25.email')}`}>{t('s25.email')}</a>
      </div>
      <p>{t('s25.response')}</p>

      <h2>{t('s26.title')}</h2>
      <p>{t('s26.p1')}</p>
    </LegalContent>
  );
}
