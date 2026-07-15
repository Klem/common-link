import { EmbedDonateClient } from './EmbedDonateClient';

interface Props {
  params: Promise<{ widgetToken: string }>;
  searchParams: Promise<{ source?: string }>;
}

export default async function EmbedDonatePage({ params, searchParams }: Props) {
  const { widgetToken } = await params;
  const { source } = await searchParams;
  return <EmbedDonateClient widgetToken={widgetToken} sourceSite={source ?? null} />;
}
