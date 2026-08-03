import type { Metadata } from 'next';
import { getTranslations } from 'next-intl/server';
import { getLanding, type PublicLandingDto } from '@/lib/api/public';
import { LandingHeader } from './LandingHeader';
import { LandingHero } from './LandingHero';
import './landing.css';

interface Props {
  params: Promise<{ locale: string; widgetToken: string }>;
}

function truncateDescription(text: string): string {
  if (text.length <= 155) return text;
  const cut = text.lastIndexOf(' ', 155);
  return (cut > 0 ? text.slice(0, cut) : text.slice(0, 155)) + '…';
}

export async function generateMetadata({ params }: Props): Promise<Metadata> {
  const { widgetToken } = await params;
  try {
    const data = await getLanding(widgetToken);
    const rawDesc = data.campaignDescription ?? data.legalObject ?? '';
    return {
      title: `${data.campaignName} — ${data.associationName}`,
      description: rawDesc ? truncateDescription(rawDesc) : undefined,
      robots: { index: false, follow: false },
    };
  } catch {
    return { robots: { index: false, follow: false } };
  }
}

export default async function LandingPage({ params }: Props) {
  const { locale, widgetToken } = await params;
  const t = await getTranslations({ locale, namespace: 'landing' });

  let data: PublicLandingDto | null = null;
  let errorKey: 'error.notFound' | 'error.notLive' | null = null;

  try {
    data = await getLanding(widgetToken);
  } catch (err: unknown) {
    const status = (err as { response?: { status: number } }).response?.status;
    if (status === 404) errorKey = 'error.notFound';
    else if (status === 409) errorKey = 'error.notLive';
    else throw err;
  }

  if (errorKey || !data) {
    return (
      <div className="lp-root">
        <div className="lp-error">
          <p>{t(errorKey ?? 'error.notFound')}</p>
        </div>
      </div>
    );
  }

  const description = data.campaignDescription ?? data.legalObject;
  const jsonLd = {
    '@context': 'https://schema.org',
    '@type': 'NGO',
    name: data.associationName,
    ...(description ? { description } : {}),
    potentialAction: {
      '@type': 'DonateAction',
      name: 'Faire un don',
    },
  };

  return (
    <div className="lp-root">
      <script
        type="application/ld+json"
        dangerouslySetInnerHTML={{ __html: JSON.stringify(jsonLd).replace(/</g, '\\u003c') }}
      />
      <LandingHeader associationName={data.associationName} />
      <LandingHero
        campaignName={data.campaignName}
        campaignCategory={data.campaignCategory}
        campaignReason={data.campaignReason}
        campaignDescription={data.campaignDescription}
        associationRna={data.associationRna}
        taxReductionRate={data.taxReductionRate}
        campaignId={data.campaignId}
        coverImage={data.coverImage}
      />
      <div className="lp-layout">
        <main className="lp-main">
          {/* Prompt 8 — ProjectSection */}
          {/* Prompt 9 — TransparencySection */}
          {/* Prompt 10 — TrustSection */}
        </main>
        <aside className="lp-sidebar">
          <div className="lp-sidebar-sticky">
            {/* Prompt 11 — DonationPanel */}
          </div>
        </aside>
      </div>
      {/* Prompt 12 — StickyBar */}
      {/* Prompt 13 — Footer */}
    </div>
  );
}
