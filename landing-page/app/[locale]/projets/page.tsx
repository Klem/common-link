import { getTranslations, setRequestLocale } from 'next-intl/server';
import { ProjectsSection } from '@/components/sections/ProjectsSection';
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
 * Frame and empty state from the mockup ({@link ProjectsSection}). The mockup has no campaign
 * card: when live campaigns exist, the directory ({@link ProjectsDiscovery}, cause, scope and
 * proximity filters applied on the campaigns fetched here) replaces the empty state.
 *
 * `fetchLiveCampaigns` never throws: an unavailable API yields an empty list, hence the same
 * empty state as "no live campaign". The public page does not go down with the API.
 */
export default async function ProjetsPage({ params }: { params: Promise<{ locale: string }> }) {
  const { locale } = await params;
  setRequestLocale(locale);

  const campaigns = await fetchLiveCampaigns();

  return (
    <main>
      {campaigns.length === 0 ? (
        <ProjectsSection />
      ) : (
        <ProjectsSection>
          {/* The directory reuses the mockup's `.legal-page`-scoped classes (`.campaign-*`,
              `.filter-pill`, `.empty-state`): out of mockup, on the section's cream background. */}
          <div className="legal-page" style={{ background: 'transparent' }}>
            <ProjectsDiscovery campaigns={campaigns} />
          </div>
        </ProjectsSection>
      )}
    </main>
  );
}
