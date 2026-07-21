import { EmbedDonateClient } from './EmbedDonateClient';

interface Props {
  params: Promise<{ locale: string; widgetToken: string }>;
  searchParams: Promise<{ source?: string }>;
}

export default async function EmbedDonatePage({ params, searchParams }: Props) {
  const { locale, widgetToken } = await params;
  const { source } = await searchParams;
  return <EmbedDonateClient widgetToken={widgetToken} sourceSite={source ?? null} locale={locale} />;
}
