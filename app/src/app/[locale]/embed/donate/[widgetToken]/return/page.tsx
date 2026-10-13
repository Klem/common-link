import { EmbedDonateReturnClient } from './EmbedDonateReturnClient';

interface Props {
  params: Promise<{ locale: string; widgetToken: string }>;
  searchParams: Promise<{
    cancelled?: string;
    source?: string;
    ref?: string;
  }>;
}

/**
 * Post-payment landing from Mollie. Deliberately loads no GTM: this page runs on CommonLink's
 * origin, outside the association's page and its consent banner, so the `purchase` event is sent
 * from the association's own page after the redirect back (see `EmbedDonateReturnClient`).
 */
export default async function EmbedDonateReturnPage({ params, searchParams }: Props) {
  const { locale, widgetToken } = await params;
  const { cancelled, source, ref } = await searchParams;

  return (
    <EmbedDonateReturnClient
      widgetToken={widgetToken}
      locale={locale}
      cancelled={cancelled === 'true'}
      source={source ? decodeURIComponent(source) : null}
      donationRef={ref ?? null}
    />
  );
}
