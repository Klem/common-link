import { getTranslations } from 'next-intl/server';
import { Link } from '@/i18n/navigation';
import { LegalSubnav } from '@/components/layout/LegalSubnav';

/**
 * Page Nous contacter.
 * Markup et classes repris à l'identique de la maquette (`p18-contact.html` :
 * `.legal-subnav`, `.legal-hero`, `.legal-body`).
 * Ne pas remplacer par des classes Tailwind : le CSS de la maquette est la source de vérité.
 */
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
    <main>
      <LegalSubnav active="contact" />

      <div className="legal-hero">
        <div className="max-w">
          <h1>{t('title')}</h1>
          <p className="legal-meta">{t('meta')}</p>
        </div>
      </div>

      <div className="legal-body">
        <h2>{t('details.title')}</h2>
        <p>{t('details.p1')}</p>
        <p>{t('details.p2')}</p>
        <p>{t('details.p3')}</p>
        <p>{t('details.p4')}</p>

        <h2>{t('complaint.title')}</h2>
        <p>{t('complaint.p1')}</p>
        <p>
          {/* La maquette n'applique pas `legal-link` sur ce lien : reproduit tel quel. */}
          {t.rich('complaint.p2', {
            link: (chunks) => <Link href="/reclamations">{chunks}</Link>,
          })}
        </p>

        <h2>{t('reportCampaign.title')}</h2>
        <p>{t('reportCampaign.p1')}</p>
      </div>
    </main>
  );
}
