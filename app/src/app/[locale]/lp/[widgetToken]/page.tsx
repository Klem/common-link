import type { Metadata } from 'next';
import { getTranslations } from 'next-intl/server';
import { getLanding, type PublicLandingDto } from '@/lib/api/public';
import { LandingHeader } from './LandingHeader';
import { LandingHero } from './LandingHero';
import { ProjectSection } from './ProjectSection';
import { TransparencySection } from './TransparencySection';
import { TrustSection } from './TrustSection';
import { LandingClient } from './LandingClient';
import { LegalFooter } from './LegalFooter';
import { EmbedHeightReporter } from './EmbedHeightReporter';
import { PreviewBanner } from './PreviewBanner';
import { GtmSnippet } from '@/components/GtmSnippet';
import './landing.css';

interface Props {
  params: Promise<{ locale: string; widgetToken: string }>;
  searchParams: Promise<{ parentOrigin?: string; preview?: string }>;
}

/**
 * Validates the `parentOrigin` query parameter injected by `public/landing.js`.
 *
 * Returns the canonical origin when the value is a bare http(s) origin, `null` otherwise —
 * an unvalidated value would end up as a `postMessage` target origin.
 */
function resolveParentOrigin(raw: string | undefined): string | null {
  if (!raw) return null;
  try {
    const url = new URL(raw);
    if (url.protocol !== 'http:' && url.protocol !== 'https:') return null;
    return url.origin === raw ? url.origin : null;
  } catch {
    return null;
  }
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

export default async function LandingPage({ params, searchParams }: Props) {
  const { locale, widgetToken } = await params;
  const query = await searchParams;
  const parentOrigin = resolveParentOrigin(query.parentOrigin);
  // Kept for the render, not only for the fetch: the preview banner and the disabled form both
  // depend on it.
  const previewToken = query.preview ?? null;
  const t = await getTranslations({ locale, namespace: 'landing' });

  let data: PublicLandingDto | null = null;
  let errorKey: 'error.notFound' | 'error.notLive' | null = null;

  try {
    data = await getLanding(widgetToken, previewToken);
  } catch (err: unknown) {
    const status = (err as { response?: { status: number } }).response?.status;
    if (status === 404) errorKey = 'error.notFound';
    else if (status === 409) errorKey = 'error.notLive';
    else throw err;
  }

  if (errorKey || !data) {
    return (
      <div className="lp-root">
        {parentOrigin && <EmbedHeightReporter parentOrigin={parentOrigin} />}
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

  // Skipped in preview mode: the association's own preview views must not pollute its analytics.
  const gtmId = previewToken ? null : data.gtmContainerId;

  return (
    <div className="lp-root" data-theme={data.landingTheme}>
      <GtmSnippet id={gtmId} />
      {parentOrigin && <EmbedHeightReporter parentOrigin={parentOrigin} />}
      <script
        type="application/ld+json"
        dangerouslySetInnerHTML={{ __html: JSON.stringify(jsonLd).replace(/</g, '\\u003c') }}
      />
      {previewToken && (
        <PreviewBanner locale={locale} donationsEnabled={data.donationsEnabled} />
      )}
      <LandingHeader
        associationName={data.associationName}
        landingLogo={data.landingLogo}
      />
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
      <LandingClient
        widgetToken={widgetToken}
        sourceSite={data.widgetAllowedOrigin ?? null}
        locale={locale}
        campaignName={data.campaignName}
        tracking={{
          campaignId: data.campaignId,
          campaignName: data.campaignName,
          associationName: data.associationName,
          currency: data.currency,
        }}
        donationsEnabled={data.donationsEnabled}
        remainingCapacity={data.remainingCapacity}
      >
        {data.showProject && (
          <ProjectSection
            campaignName={data.campaignName}
            campaignDescription={data.campaignDescription}
            campaignImpactGoals={data.campaignImpactGoals}
          />
        )}
        {data.showTransparency && (
          <TransparencySection
            budget={data.budget}
            milestones={data.milestones}
          />
        )}
        {data.showTrust && <TrustSection taxReductionRate={data.taxReductionRate} />}
      </LandingClient>
      <LegalFooter
        associationName={data.associationName}
        addressLine1={data.addressLine1}
        postalCode={data.postalCode}
        city={data.city}
        associationRna={data.associationRna}
        legalObject={data.legalObject}
        taxReductionRate={data.taxReductionRate}
      />
    </div>
  );
}
