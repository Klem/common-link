import type { Metadata } from 'next';
import { getTranslations } from 'next-intl/server';
import { ReportForm } from './ReportForm';

interface Props {
  params: Promise<{ locale: string; widgetToken: string }>;
}

export async function generateMetadata({ params }: Props): Promise<Metadata> {
  const { locale } = await params;
  const t = await getTranslations({ locale, namespace: 'landing.report' });
  return { title: t('title'), robots: { index: false, follow: false } };
}

/**
 * Standalone "report this campaign" page (IC-44). Exists alongside the in-page modal on
 * `lp/[widgetToken]` because the static HTML export served from `/api/gtm-export` strips all
 * scripts and cannot host a React modal — its footer links here instead.
 */
export default async function ReportPage({ params }: Props) {
  const { widgetToken } = await params;

  return (
    <div className="min-h-screen flex items-center justify-center px-4 py-12">
      <div className="card card-no-hover p-8 max-w-md w-full">
        <ReportForm widgetToken={widgetToken} />
      </div>
    </div>
  );
}
