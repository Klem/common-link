/**
 * Google Tag Manager snippet builders — single source of truth shared by the landing page, the
 * embedded widget, and the copy/paste export offered in the settings tab.
 *
 * Mirrors the backend pattern (`UpdateLandingConfigRequest.gtmContainerId`, rule 8): a container ID
 * failing this pattern is rejected server-side too, since it is interpolated into an inline
 * `<script>` and an `iframe src` on our own origin.
 */
export const GTM_ID_PATTERN = /^GTM-[A-Z0-9]{4,10}$/;

/**
 * Official GTM head IIFE body — self-injects `gtm.js`, unmodified from Google's documented format.
 * Returned without a wrapping `<script>` tag so it can feed `dangerouslySetInnerHTML` directly
 * (a real `<script>` element already provides the tag).
 */
export function gtmHeadScript(id: string): string {
  return `(function(w,d,s,l,i){w[l]=w[l]||[];w[l].push({'gtm.start':new Date().getTime(),event:'gtm.js'});var f=d.getElementsByTagName(s)[0],j=d.createElement(s),dl=l!='dataLayer'?'&l='+l:'';j.async=true;j.src='https://www.googletagmanager.com/gtm.js?id='+i+dl;f.parentNode.insertBefore(j,f);})(window,document,'script','dataLayer','${id}');`;
}

/**
 * Official GTM noscript iframe — 0×0, placed immediately after the opening `<body>` tag. Returned
 * without a wrapping `<noscript>` tag, same rationale as {@link gtmHeadScript}.
 */
export function gtmNoscriptIframe(id: string): string {
  return `<iframe src="https://www.googletagmanager.com/ns.html?id=${id}" height="0" width="0" style="display:none;visibility:hidden"></iframe>`;
}

/** One line item in a GA4 ecommerce event's `items` array — a donation always has exactly one. */
export interface DonationItem {
  item_id: string;
  item_name: string;
}

/** GA4 ecommerce `ecommerce` object shared by `begin_checkout` and `purchase`. */
export interface DonationEcommercePayload {
  transaction_id: string;
  value: number;
  currency: string;
  items: DonationItem[];
  /** Association name — mapped to GA4's "supplier/store" ecommerce param. */
  affiliation: string;
}

const UTM_PARAM_NAMES = ['utm_source', 'utm_medium', 'utm_campaign', 'utm_term', 'utm_content'] as const;

/** UTM parameters captured from the current page's URL at donation time. */
export type UtmParams = Partial<Record<(typeof UTM_PARAM_NAMES)[number], string>>;

/**
 * Reads UTM params off `window.location.search`. Meaningful when the donation form's own URL is
 * where the donor actually landed — the case a `gtmContainerId` exists for in the first place
 * ("for Ad Grants tracking", see PublicWidgetDto/PublicLandingDto): a Google Ad Grants text ad
 * links straight to the landing page with `utm_*`/`gclid` params.
 *
 * Returns `{}` for the embed widget: that form runs inside a third-party host's iframe, so
 * `window.location.search` is `/embed/donate/{token}`'s own query string, never the host page's —
 * and the host's UTMs are cross-origin, unreadable from here. Not fixed by this function; would
 * need the embed snippet to forward them explicitly, the way it already forwards `parentOrigin`.
 *
 * Only meaningful at `begin_checkout` time: by the time `purchase` fires on `/return`, the URL is
 * this app's own return route, not wherever the donor actually arrived from.
 */
export function captureUtmParams(): UtmParams {
  if (typeof window === 'undefined') return {};
  const search = new URLSearchParams(window.location.search);
  const utm: UtmParams = {};
  for (const name of UTM_PARAM_NAMES) {
    const value = search.get(name);
    if (value) utm[name] = value;
  }
  return utm;
}

/** Donation-specific params outside the GA4-standard `ecommerce` object. */
export interface DonationEventExtras {
  anonymous: boolean;
  /** Payment method chosen on Mollie's page — only known once the donation is confirmed. */
  paymentMethod?: string;
  /** See {@link captureUtmParams} — only ever populated on `begin_checkout`. */
  utm?: UtmParams;
}

/**
 * Pushes a GA4 ecommerce donation event (`begin_checkout` or `purchase`) to `window.dataLayer`.
 *
 * Safe to call even when no GTM container is configured for this association: with nothing reading
 * it, the push is a harmless no-op. `window.dataLayer` is guarded rather than assumed present —
 * GTM's own IIFE (see {@link gtmHeadScript}) creates it, but this can run before that script has
 * (e.g. no container configured at all), or outside the browser (SSR).
 */
export function pushDonationEvent(
  event: 'begin_checkout' | 'purchase',
  ecommerce: DonationEcommercePayload,
  extras: DonationEventExtras,
): void {
  if (typeof window === 'undefined') return;
  window.dataLayer = window.dataLayer || [];
  window.dataLayer.push({
    event,
    ecommerce,
    anonymous: extras.anonymous,
    ...(extras.paymentMethod ? { payment_method: extras.paymentMethod } : {}),
    ...extras.utm,
  });
}

declare global {
  interface Window {
    dataLayer?: unknown[];
  }
}

