import { EmbedDonateReturnClient } from './EmbedDonateReturnClient';

interface Props {
  params: Promise<{ locale: string; widgetToken: string }>;
  searchParams: Promise<{ id?: string }>;
}

export default async function EmbedDonateReturnPage({ params, searchParams }: Props) {
  const { locale, widgetToken } = await params;
  const { id } = await searchParams;
  return (
    <EmbedDonateReturnClient
      paymentId={id ?? null}
      widgetToken={widgetToken}
      locale={locale}
    />
  );
}
