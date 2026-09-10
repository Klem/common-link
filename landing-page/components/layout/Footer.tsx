import { useTranslations } from 'next-intl';
import { Link } from '@/i18n/navigation';
import { APP_URL } from '@/lib/constants';

/**
 * Pied de page partagé.
 * Markup et classes repris à l'identique de la maquette (`footer.footer`,
 * `CommonLink UI V2 Julian.html`). Ne pas remplacer par des classes Tailwind :
 * le CSS de la maquette est la source de vérité.
 */
export function Footer() {
  const t = useTranslations('footer');
  const currentYear = new Date().getFullYear();

  return (
    <footer className="footer">
      <div className="footer-grid max-w">
        <div className="footer-brand">
          <div className="flex gap-8 items-center mb-12">
            <span className="cl-logo" />
            <span className="nav-logo-text">
              Common<em>Link</em>
            </span>
          </div>
          <p>{t('brand.description')}</p>
          <div className="footer-badges mt-16">
            <span className="footer-badge">{t('brand.badgeSecure')}</span>
            <span className="footer-badge">{t('brand.badgeOrias')}</span>
          </div>
        </div>
        <div className="footer-col">
          <h4>{t('donors.title')}</h4>
          <a href={APP_URL}>{t('donors.donate')}</a>
          <Link href="/donateurs">{t('donors.howToGive')}</Link>
          <a href={APP_URL}>{t('donors.mySpace')}</a>
        </div>
        <div className="footer-col">
          <h4>{t('associations.title')}</h4>
          <Link href="/associations">{t('associations.createCampaign')}</Link>
          <Link href="/tarifs">{t('associations.tarifs')}</Link>
        </div>
        <div className="footer-col">
          <h4>{t('legal.title')}</h4>
          <Link href="/mentions-legales">{t('legal.links.mentions')}</Link>
          <Link href="/transparence">{t('legal.links.transparency')}</Link>
          <Link href="/conditions-generales-utilisation">{t('legal.links.cguDonors')}</Link>
          <Link href="/conditions-generales-utilisation-associations">
            {t('legal.links.cguAssociations')}
          </Link>
          <Link href="/contrat-type">{t('legal.links.modelAgreement')}</Link>
          <Link href="/reclamations">{t('legal.links.complaints')}</Link>
          <Link href="/politique-confidentialite">{t('legal.links.privacy')}</Link>
          <Link href="/politique-cookies">{t('legal.links.cookies')}</Link>
          <Link href="/contact">{t('legal.links.contact')}</Link>
        </div>
      </div>
      <div className="footer-bottom max-w">
        <span>{t('bottom.legal', { year: currentYear })}</span>
        <span>{t('bottom.payments')}</span>
      </div>
    </footer>
  );
}
