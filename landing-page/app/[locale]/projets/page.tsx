import { getTranslations, setRequestLocale } from 'next-intl/server';
import { Link } from '@/i18n/navigation';
import { CampaignCard } from '@/components/campaign/CampaignCard';
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
 * Page « Tous les projets » — liste les campagnes actuellement ouvertes aux dons.
 *
 * Markup et classes repris de la maquette (`PAGE 4 — PROJETS / CAMPAGNES`,
 * `CommonLink UI V2 Julian.html`). Les filtres et le tri de la maquette ne sont pas repris pour
 * cette itération : un filtre qui ne filtre pas se lit comme un bug.
 *
 * `fetchLiveCampaigns` ne lève jamais : une API indisponible produit une liste vide, donc le même
 * `.empty-state` que « aucune campagne en cours ». La page publique ne tombe pas avec l'API.
 */
export default async function ProjetsPage({ params }: { params: Promise<{ locale: string }> }) {
  const { locale } = await params;
  setRequestLocale(locale);

  const t = await getTranslations({ locale, namespace: 'landing.projects' });
  const campaigns = await fetchLiveCampaigns();

  return (
    <main>
      <section style={{ background: 'var(--soft-cream)', padding: '48px' }}>
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
            <div className="campaigns-grid">
              {campaigns.map((campaign) => (
                <CampaignCard key={campaign.campaignId} campaign={campaign} />
              ))}
            </div>
          )}
        </div>
      </section>
    </main>
  );
}
