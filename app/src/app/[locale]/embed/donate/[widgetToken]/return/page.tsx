import { getWidget } from '@/lib/api/public';
import { GtmSnippet } from '@/components/GtmSnippet';
import { EmbedDonateReturnClient } from './EmbedDonateReturnClient';

interface Props {
  params: Promise<{ locale: string; widgetToken: string }>;
  searchParams: Promise<{
    cancelled?: string;
    source?: string;
    ref?: string;
    amount?: string;
    currency?: string;
    campaignId?: string;
    campaignName?: string;
    associationName?: string;
    anonymous?: string;
  }>;
}

export default async function EmbedDonateReturnPage({ params, searchParams }: Props) {
  const { locale, widgetToken } = await params;
  const {
    cancelled, source, ref, amount, currency, campaignId, campaignName, associationName, anonymous,
  } = await searchParams;

  // Best-effort: a failed fetch here must not break the return page, it only means no GTM
  // injection — the same tolerance getWidget already gets client-side for source validation.
  let gtmId: string | null = null;
  try {
    const widget = await getWidget(widgetToken);
    gtmId = widget.gtmContainerId;
  } catch {
    gtmId = null;
  }

  return (
    <>
      <GtmSnippet id={gtmId} />
      <EmbedDonateReturnClient
        widgetToken={widgetToken}
        locale={locale}
        cancelled={cancelled === 'true'}
        source={source ? decodeURIComponent(source) : null}
        tracking={
          ref && amount && currency && campaignId && campaignName && associationName
            ? {
                ref,
                amount: Number(amount),
                currency,
                campaignId,
                campaignName: decodeURIComponent(campaignName),
                associationName: decodeURIComponent(associationName),
                anonymous: anonymous === 'true',
              }
            : null
        }
      />
    </>
  );
}
