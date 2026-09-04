import { useTranslations } from 'next-intl';
import { Link } from '@/i18n/navigation';
import { APP_URL } from '@/lib/constants';

export function Footer() {
  const t = useTranslations('footer');
  const currentYear = new Date().getFullYear();

  const linkClass = 'text-white/70 text-[0.9rem] hover:text-white transition-colors duration-200';

  return (
    <footer className="bg-primary-dark text-white/70 pt-16 pb-8">
      <div className="max-w-container mx-auto px-8">
        <div className="grid grid-cols-1 md:grid-cols-[1.6fr_1fr_1fr_1.6fr] gap-8 md:gap-10 pb-12 border-b border-white/10">
          {/* Brand */}
          <div>
            <div className="flex items-center gap-1 font-ui font-extrabold text-white text-[1.05rem] mb-4">
              <span>Common</span>
              <span className="italic-accent text-secondary">Link</span>
            </div>
            <p className="text-[0.9rem]">{t('brand.description')}</p>
            <div className="flex gap-2 mt-4">
              <span className="inline-flex items-center px-3 py-1 rounded-full bg-white/10 text-white text-[0.75rem] font-ui font-semibold">
                {t('brand.badgeSecure')}
              </span>
              <span className="inline-flex items-center px-3 py-1 rounded-full bg-white/10 text-white text-[0.75rem] font-ui font-semibold">
                {t('brand.badgeOrias')}
              </span>
            </div>
          </div>

          {/* Donors */}
          <div className="flex flex-col gap-2">
            <strong className="block text-white font-ui text-[0.95rem] mb-2">{t('donors.title')}</strong>
            <a href={APP_URL} className={linkClass}>{t('donors.donate')}</a>
            <Link href="/donateurs" className={linkClass}>{t('donors.howToGive')}</Link>
            <a href={APP_URL} className={linkClass}>{t('donors.mySpace')}</a>
          </div>

          {/* Associations */}
          <div className="flex flex-col gap-2">
            <strong className="block text-white font-ui text-[0.95rem] mb-2">{t('associations.title')}</strong>
            <a href={APP_URL} className={linkClass}>{t('associations.createCampaign')}</a>
            <Link href="/tarifs" className={linkClass}>{t('associations.tarifs')}</Link>
          </div>

          {/* Legal */}
          <div className="flex flex-col gap-2">
            <strong className="block text-white font-ui text-[0.95rem] mb-2">{t('legal.title')}</strong>
            <Link href="/mentions-legales" className={linkClass}>{t('legal.links.mentions')}</Link>
            <Link href="/transparence" className={linkClass}>{t('legal.links.transparency')}</Link>
            <Link href="/conditions-generales-utilisation" className={linkClass}>{t('legal.links.cguDonors')}</Link>
            <Link href="/conditions-generales-utilisation-associations" className={linkClass}>{t('legal.links.cguAssociations')}</Link>
            <Link href="/contrat-type" className={linkClass}>{t('legal.links.modelAgreement')}</Link>
            <Link href="/reclamations" className={linkClass}>{t('legal.links.complaints')}</Link>
            <Link href="/politique-confidentialite" className={linkClass}>{t('legal.links.privacy')}</Link>
            <Link href="/politique-cookies" className={linkClass}>{t('legal.links.cookies')}</Link>
            <Link href="/contact" className={linkClass}>{t('legal.links.contact')}</Link>
            <Link href="/faq" className={linkClass}>{t('legal.links.faq')}</Link>
          </div>
        </div>

        {/* Bottom */}
        <div className="pt-6 text-[0.8rem] opacity-50 space-y-1">
          <p>{t('bottom.legal', { year: currentYear })}</p>
          <p>{t('bottom.payments')}</p>
        </div>
      </div>
    </footer>
  );
}
