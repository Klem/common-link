import { getTranslations } from 'next-intl/server';
import { LegalContent } from '@/components/layout/LegalContent';

export async function generateMetadata({ params }: { params: Promise<{ locale: string }> }) {
  const { locale } = await params;
  const t = await getTranslations({ locale, namespace: 'metadata.tarifs' });
  return {
    title: t('title'),
    description: t('description'),
  };
}

export default async function TarifsPage({ params }: { params: Promise<{ locale: string }> }) {
  const { locale } = await params;
  const t = await getTranslations({ locale, namespace: 'legal.tarifs' });

  return (
    <LegalContent title={t('title')} lastUpdated={t('lastUpdated')}>
      <p>{t('intro')}</p>

      <h2>{t('donors.title')}</h2>
      <p><strong>{t('donors.p1')}</strong></p>
      <p>{t('donors.p2')}</p>

      <h2>{t('orgs.title')}</h2>
      <p>{t('orgs.p1')}</p>
      <p><strong>{t('orgs.fee')}</strong></p>
      <p>{t('orgs.p2')}</p>

      <h2>{t('payment.title')}</h2>
      <p>{t('payment.p1')}</p>
      <p><strong>{t('payment.fee')}</strong></p>
      <p>{t('payment.p2')}</p>
      <ul>
        <li>{t('payment.item0')}</li>
        <li>{t('payment.item1')}</li>
        <li>{t('payment.item2')}</li>
        <li>{t('payment.item3')}</li>
      </ul>

      <h2>{t('example.title')}</h2>
      <p>{t('example.intro')}</p>
      <table>
        <thead>
          <tr>
            <th>{t('example.col1')}</th>
            <th style={{ textAlign: 'right' }}>{t('example.col2')}</th>
          </tr>
        </thead>
        <tbody>
          <tr><td>{t('example.row0col1')}</td><td style={{ textAlign: 'right' }}>{t('example.row0col2')}</td></tr>
          <tr><td>{t('example.row1col1')}</td><td style={{ textAlign: 'right' }}>{t('example.row1col2')}</td></tr>
          <tr><td>{t('example.row2col1')}</td><td style={{ textAlign: 'right' }}>{t('example.row2col2')}</td></tr>
          <tr><td>{t('example.row3col1')}</td><td style={{ textAlign: 'right' }}>{t('example.row3col2')}</td></tr>
          <tr>
            <td><strong>{t('example.row4col1')}</strong></td>
            <td style={{ textAlign: 'right' }}><strong>{t('example.row4col2')}</strong></td>
          </tr>
        </tbody>
      </table>
      <p>{t('example.note')}</p>

      <h2>{t('free.title')}</h2>
      <p>{t('free.p1')}</p>

      <h2>{t('specific.title')}</h2>
      <p>{t('specific.p1')}</p>

      <h2>{t('noHidden.title')}</h2>
      <p>{t('noHidden.p1')}</p>

      <h2>{t('billing.title')}</h2>
      <p>{t('billing.p1')}</p>
    </LegalContent>
  );
}
