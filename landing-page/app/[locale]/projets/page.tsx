import { getTranslations, setRequestLocale } from 'next-intl/server';
import { Link } from '@/i18n/navigation';
import { ProjectsDiscovery } from '@/components/campaign/ProjectsDiscovery';
import { fetchLiveCampaigns } from '@/lib/api/campaigns';

export async function generateMetadata({ params }: { params: Promise<{ locale: string }> }) {
  const { locale } = await params;
  const t = await getTranslations({ locale, namespace: 'metadata.projects' });
  return {
    title: t('title'),
    description: t('description'),
    alternates: {
      canonical: 'https://www.common-link.org/projets',
      languages: { fr: '/projets', en: '/projets' },
    },
  };
}

/**
 * "Tous les projets" page — lists the campaigns currently open to donations.
 *
 * Markup and classes taken from the mockup (`PAGE 4 — PROJETS / CAMPAGNES`). The cause, scope
 * and proximity filters live in the client component {@link ProjectsDiscovery}, applied on the
 * campaigns fetched here. The mockup's sort select and status pills ("En cours", "Objectif
 * atteint") are still not ported: every listed campaign is live.
 *
 * `fetchLiveCampaigns` never throws: an unavailable API yields an empty list, hence the same
 * `.empty-state` as "no live campaign". The public page does not go down with the API.
 */
export default async function ProjetsPage({ params }: { params: Promise<{ locale: string }> }) {
  const { locale } = await params;
  setRequestLocale(locale);

  const t = await getTranslations({ locale, namespace: 'landing.projects' });
  const campaigns = await fetchLiveCampaigns();

  return (
    <main>
      <section className="section-sm" style={{ background: 'var(--soft-cream)' }}>
        <div className="max-w">
          <div className="breadcrumb">
            <Link href="/">{t('breadcrumbHome')}</Link>
            <span className="sep">›</span>
            <span className="current">{t('breadcrumbCurrent')}</span>
          </div>

          <div className="flex justify-between items-center mb-32">
            <div>
              <h1 style={{ fontSize: '36px' }}>{t('title')}</h1>
              <p style={{ color: 'var(--slate-lavender)', marginTop: '4px' }}>{t('subtitle')}</p>
            </div>
          </div>

          {campaigns.length === 0 ? (
            <div className="empty-state">
              <div className="empty-emoji">🌱</div>
              <h3>{t('emptyTitle')}</h3>
              <p>{t('emptyText')}</p>
            </div>
          ) : (
            <ProjectsDiscovery campaigns={campaigns} />
          )}
        </div>
      </section>
    </main>
  );
}
