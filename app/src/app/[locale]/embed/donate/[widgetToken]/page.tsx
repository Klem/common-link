import { getWidget } from '@/lib/api/public';
import { GtmSnippet } from '@/components/GtmSnippet';
import { EmbedDonateClient } from './EmbedDonateClient';
import { EmbedWidgetHeightReporter } from './EmbedWidgetHeightReporter';
import { PurchaseRelay } from '@/components/donation/PurchaseRelay';
import { resolveDonationRef } from '@/lib/donation/donationRef';

interface Props {
  params: Promise<{ locale: string; widgetToken: string }>;
  searchParams: Promise<{ source?: string; parentOrigin?: string; clRef?: string }>;
}

/**
 * Validates the `parentOrigin` query parameter injected by `public/widget.js`.
 *
 * Returns the canonical origin when the value is a bare http(s) origin, `null` otherwise — an
 * unvalidated value would end up as a `postMessage` target origin. Mirrors
 * `lp/[widgetToken]/page.tsx`'s `resolveParentOrigin`; kept as a separate copy rather than a
 * shared util since `widget.js` and `landing.js` are independent loaders by design.
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

export default async function EmbedDonatePage({ params, searchParams }: Props) {
  const { locale, widgetToken } = await params;
  const { source, parentOrigin: rawParentOrigin, clRef } = await searchParams;
  const parentOrigin = resolveParentOrigin(rawParentOrigin);
  // Set by `widget.js` when the donor is back from a confirmed payment — see PurchaseRelay.
  const donationRef = resolveDonationRef(clRef);

  // Separate server-side fetch just for GTM: the script must be part of the initial HTML to
  // execute (see GtmSnippet), which rules out reusing EmbedDonateClient's client-side fetch.
  // Skipped entirely when embedded through `widget.js` (`parentOrigin` set): the host page runs
  // the association's container itself and receives every event through the postMessage bridge,
  // so loading it here too would make each event fire in two GTM instances.
  const gtmId = parentOrigin
    ? null
    : await getWidget(widgetToken)
      .then((w) => w.gtmContainerId)
      .catch((err: unknown) => {
        console.warn(
          `[CommonLink embed/donate] Failed to fetch widget config for GTM injection (widgetToken=${widgetToken}) — no GTM will be injected.`,
          err,
        );
        return null;
      });

  return (
    <>
      <GtmSnippet id={gtmId} />
      {parentOrigin && <EmbedWidgetHeightReporter parentOrigin={parentOrigin} />}
      {parentOrigin && donationRef && <PurchaseRelay donationRef={donationRef} parentOrigin={parentOrigin} />}
      <EmbedDonateClient
        widgetToken={widgetToken}
        sourceSite={source ?? null}
        locale={locale}
        parentOrigin={parentOrigin}
      />
    </>
  );
}
