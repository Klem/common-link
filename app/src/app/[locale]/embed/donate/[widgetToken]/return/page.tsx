import { EmbedDonateReturnClient } from './EmbedDonateReturnClient';

interface Props {
  params: Promise<{ locale: string; widgetToken: string }>;
  searchParams: Promise<{ cancelled?: string; source?: string }>;
}

export default async function EmbedDonateReturnPage({ params, searchParams }: Props) {
  const { locale, widgetToken } = await params;
  const { cancelled, source } = await searchParams;
  return (
    <EmbedDonateReturnClient
      widgetToken={widgetToken}
      locale={locale}
      cancelled={cancelled === 'true'}
      source={source ? decodeURIComponent(source) : null}
    />
  );
}
