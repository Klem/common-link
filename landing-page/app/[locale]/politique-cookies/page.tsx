import { getTranslations } from 'next-intl/server';
import { Link } from '@/i18n/navigation';
import { LegalSubnav } from '@/components/layout/LegalSubnav';

/**
 * Politique cookies.
 * Markup et classes repris à l'identique de la maquette (`p20-cookies.html` :
 * `.legal-subnav`, `.legal-hero`, `.legal-body`).
 * Ne pas remplacer par des classes Tailwind : le CSS de la maquette est la source de vérité.
 */
export async function generateMetadata({ params }: { params: Promise<{ locale: string }> }) {
  const { locale } = await params;
  const t = await getTranslations({ locale, namespace: 'metadata.cookies' });
  return {
    title: t('title'),
    description: t('description'),
    alternates: {
      canonical: 'https://www.common-link.org/politique-cookies',
      languages: { fr: '/politique-cookies', en: '/en/politique-cookies' },
    },
  };
}

export default async function PolitiqueCookiesPage({ params }: { params: Promise<{ locale: string }> }) {
  const { locale } = await params;
  const t = await getTranslations({ locale, namespace: 'legal.politiqueCookies' });
  const bold = { b: (chunks: React.ReactNode) => <strong>{chunks}</strong> };

  return (
    <main>
      <LegalSubnav active="cookies" />

      <div className="legal-hero">
        <div className="max-w">
          <h1>{t('title')}</h1>
          <p className="legal-meta">{t('lastUpdated')}</p>
        </div>
      </div>

      <div className="legal-body">
        <h2>{t('cookies.title')}</h2>
        <p>{t('cookies.p1')}</p>

        <h2>{t('deposited.title')}</h2>
        <p>{t('deposited.p1')}</p>
        <p>{t.rich('deposited.p2', bold)}</p>
        <p>{t.rich('deposited.p3', bold)}</p>
        <p>{t('deposited.p4')}</p>

        <h2>{t('notDeposited.title')}</h2>
        <p>{t('notDeposited.p1')}</p>

        <h2>{t('refuse.title')}</h2>
        <p>{t('refuse.p1')}</p>

        <h2>{t('logs.title')}</h2>
        <p>
          {t.rich('logs.p1', {
            link: (chunks) => (
              <Link href="/politique-confidentialite" className="legal-link">
                {chunks}
              </Link>
            ),
          })}
        </p>

        <h2>{t('hostedPages.title')}</h2>
        <p>{t('hostedPages.p1')}</p>
        <p>{t('hostedPages.p2')}</p>

        <h2>{t('questions.title')}</h2>
        <p>{t('questions.p1')}</p>
      </div>
    </main>
  );
}
