import { useTranslations } from 'next-intl';
import { Link } from '@/i18n/navigation';
import { APP_URL } from '@/lib/constants';

const TITLE_STYLE = { fontSize: '12px', fontWeight: '700', letterSpacing: '1px', textTransform: 'uppercase', color: '#FFFFFF' } as const;
const LINK_STYLE = { fontSize: '14px', color: 'rgba(255,255,255,0.78)', textDecoration: 'none' };
const COLUMN_STYLE = { display: 'flex', flexDirection: 'column', gap: '10px' } as const;

/**
 * Shared footer, markup and inline styles taken from the mockup
 * (`commonlink-demo-nouveau-design.html`, `<footer>` of every page). The four columns wrap on
 * their own (`auto-fit` grid), as in the mockup.
 */
export function Footer() {
  const t = useTranslations('footer');
  const currentYear = new Date().getFullYear();

  return (
    <footer style={{ background: '#171744', color: 'rgba(255,255,255,0.78)', padding: '64px 48px 32px' }}>
      <div style={{ maxWidth: '1240px', margin: '0 auto', display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(min(100%, 220px), 1fr))', gap: '40px' }}>
        <div style={{ display: 'flex', flexDirection: 'column', gap: '12px' }}>
          <span style={{ fontFamily: "'Nunito Sans', sans-serif", fontWeight: '900', fontSize: '22px', color: '#FFFFFF' }}>
            {t.rich('logo', { accent: (chunks) => <span style={{ color: '#4ECDC4' }}>{chunks}</span> })}
          </span>
          <p style={{ margin: '0', fontSize: '14px', lineHeight: '1.6' }}>{t('brand.description')}</p>
          <span style={{ fontSize: '13px', fontWeight: '600', color: '#FFFFFF' }}>{t('brand.orias')}</span>
        </div>
        <div style={COLUMN_STYLE}>
          <span style={TITLE_STYLE}>{t('donors.title')}</span>
          <a href={APP_URL} style={LINK_STYLE}>{t('donors.donate')}</a>
          <Link href="/donateurs" style={LINK_STYLE}>{t('donors.howToGive')}</Link>
          <a href={APP_URL} style={LINK_STYLE}>{t('donors.mySpace')}</a>
        </div>
        <div style={COLUMN_STYLE}>
          <span style={TITLE_STYLE}>{t('associations.title')}</span>
          <Link href="/associations" style={LINK_STYLE}>{t('associations.createCampaign')}</Link>
          <Link href="/tarifs" style={LINK_STYLE}>{t('associations.tarifs')}</Link>
        </div>
        <div style={COLUMN_STYLE}>
          <span style={TITLE_STYLE}>{t('legal.title')}</span>
          <Link href="/mentions-legales" style={LINK_STYLE}>{t('legal.links.mentions')}</Link>
          <Link href="/transparence" style={LINK_STYLE}>{t('legal.links.transparency')}</Link>
          <Link href="/conditions-generales-utilisation" style={LINK_STYLE}>{t('legal.links.cguDonors')}</Link>
          <Link href="/conditions-generales-utilisation-associations" style={LINK_STYLE}>{t('legal.links.cguAssociations')}</Link>
          <Link href="/contrat-type" style={LINK_STYLE}>{t('legal.links.modelAgreement')}</Link>
          <Link href="/reclamations" style={LINK_STYLE}>{t('legal.links.complaints')}</Link>
          <Link href="/politique-confidentialite" style={LINK_STYLE}>{t('legal.links.privacy')}</Link>
          <Link href="/politique-cookies" style={LINK_STYLE}>{t('legal.links.cookies')}</Link>
          <Link href="/contact" style={LINK_STYLE}>{t('legal.links.contact')}</Link>
        </div>
      </div>
      <div style={{ maxWidth: '1240px', margin: '40px auto 0', paddingTop: '20px', borderTop: '1px solid rgba(255,255,255,0.12)', display: 'flex', flexWrap: 'wrap', justifyContent: 'space-between', gap: '12px', fontSize: '13px' }}>
        <span>{t('bottom.legal', { year: currentYear })}</span>
        <span>{t('bottom.payments')}</span>
      </div>
    </footer>
  );
}
