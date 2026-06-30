import { getTranslations } from 'next-intl/server';
import { Link } from '@/i18n/navigation';
import { LegalContent } from '@/components/layout/LegalContent';

export async function generateMetadata({ params }: { params: Promise<{ locale: string }> }) {
  const { locale } = await params;
  const t = await getTranslations({ locale, namespace: 'metadata.privacy' });
  return {
    title: t('title'),
    description: t('description'),
  };
}

export default async function PolitiqueConfidentialitePage({ params }: { params: Promise<{ locale: string }> }) {
  const { locale } = await params;
  const t = await getTranslations({ locale, namespace: 'legal.politiqueConfidentialite' });

  return (
    <LegalContent title={t('title')} lastUpdated={t('lastUpdated')}>
      <p>{t('intro')}</p>

      <h2>{t('controller.title')}</h2>
      <div className="contact-block">
        <strong>{t('controller.name')}</strong><br />
        {t('controller.type')}<br />
        {t('controller.address')}<br />
        {t('controller.siren')}<br />
        Email : <a href={`mailto:${t('controller.email')}`}>{t('controller.email')}</a>
      </div>
      <p>{t('controller.referent')}</p>
      <p>{t('controller.noDpo')}</p>

      <h2>{t('data.title')}</h2>
      <h3>{t('data.donors.title')}</h3>
      <p>{t('data.donors.p1')}</p>
      <p>{t('data.donors.p2')}</p>

      <h3>{t('data.orgs.title')}</h3>
      <p>{t('data.orgs.p1')}</p>

      <h3>{t('data.technical.title')}</h3>
      <p>{t('data.technical.p1')}</p>

      <h2>{t('purposes.title')}</h2>
      <p>{t('purposes.intro')}</p>
      <ul>
        <li>{t('purposes.item0')}</li>
        <li>{t('purposes.item1')}</li>
        <li>{t('purposes.item2')}</li>
        <li>{t('purposes.item3')}</li>
        <li>{t('purposes.item4')}</li>
        <li>{t('purposes.item5')}</li>
        <li>{t('purposes.item6')}</li>
        <li>{t('purposes.item7')}</li>
        <li>{t('purposes.item8')}</li>
        <li>{t('purposes.item9')}</li>
        <li>{t('purposes.item10')}</li>
      </ul>

      <h2>{t('legalBasis.title')}</h2>
      <p>{t('legalBasis.intro')}</p>
      <ul>
        <li>{t('legalBasis.item0')}</li>
        <li>{t('legalBasis.item1')}</li>
        <li>{t('legalBasis.item2')}</li>
        <li>{t('legalBasis.item3')}</li>
        <li>{t('legalBasis.item4')}</li>
      </ul>

      <h2>{t('recipients.title')}</h2>
      <p>{t('recipients.intro')}</p>
      <ul>
        <li>{t('recipients.item0')}</li>
        <li>{t('recipients.item1')}</li>
        <li>{t('recipients.item2')}</li>
        <li>{t('recipients.item3')}</li>
        <li>{t('recipients.item4')}</li>
        <li>{t('recipients.item5')}</li>
        <li>{t('recipients.item6')}</li>
      </ul>
      <p>{t('recipients.monerium')}</p>

      <h2>{t('transfers.title')}</h2>
      <p>{t('transfers.p1')}</p>

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
          <tr><td>{t('retention.row5col1')}</td><td>{t('retention.row5col2')}</td></tr>
          <tr><td>{t('retention.row6col1')}</td><td>{t('retention.row6col2')}</td></tr>
        </tbody>
      </table>

      <h2>{t('newsletter.title')}</h2>
      <p>{t('newsletter.p1')}</p>
      <p>{t('newsletter.p2')}</p>

      <h2>{t('cookies.title')}</h2>
      <p>
        {t('cookies.p1')}{' '}
        <Link href="/politique-cookies">{t('cookies.linkLabel')}</Link>
      </p>

      <h2>{t('security.title')}</h2>
      <p>{t('security.p1')}</p>

      <h2>{t('rights.title')}</h2>
      <p>{t('rights.intro')}</p>
      <ul>
        <li>{t('rights.item0')}</li>
        <li>{t('rights.item1')}</li>
        <li>{t('rights.item2')}</li>
        <li>{t('rights.item3')}</li>
        <li>{t('rights.item4')}</li>
        <li>{t('rights.item5')}</li>
        <li>{t('rights.item6')}</li>
        <li>{t('rights.item7')}</li>
      </ul>
      <p>{t('rights.contact')}</p>
      <p>{t('rights.response')}</p>

      <h2>{t('cnil.title')}</h2>
      <p>
        {t('cnil.p1').split('CNIL')[0]}
        <a href="https://www.cnil.fr" target="_blank" rel="noopener noreferrer">CNIL</a>
        {t('cnil.p1').split('CNIL')[1] ?? ''}
      </p>

      <h2>{t('changes.title')}</h2>
      <p>{t('changes.p1')}</p>
    </LegalContent>
  );
}
