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

/**
 * Host-side bridge for the raw `<iframe src=".../fr/lp/{token}">` embed — the one embed with no
 * CommonLink loader (`landing.js`/`widget.js`) on the host page. Returned without a wrapping
 * `<script>` tag, same rationale as {@link gtmHeadScript}. Must be pasted right after the iframe.
 *
 * Mirrors what `landing.js` does for the script embed:
 *  1. Adds `?parentOrigin=<host origin>` to the iframe `src` (a copied `<iframe>` tag is static and
 *     cannot know the host origin) — the iframe will not forward events without it, and loads no
 *     GTM of its own once it is set. This reloads the iframe once; the tag keeps working without
 *     JavaScript, just without the bridge.
 *  2. Back from a confirmed payment (`?cl_donation=success&cl_ref=<ref>`, added by `/return`):
 *     removes both parameters from the address bar and passes the ref to the iframe as `clRef`, so
 *     it relays the `purchase` event with server-side values. A ref already relayed in this tab
 *     (`sessionStorage`) is not passed again, so a reload never repeats the event.
 *  3. Pushes each `cl-widget-gtm-event` message (`begin_checkout`, `purchase`) into the host
 *     `dataLayer`, accepting only our own iframe and our own origin.
 *
 * DEBUG (remove when no longer needed): with `?clDebug=1` on the host page (remembered for the tab
 * in `sessionStorage`, `?clDebug=0` to stop), every host `dataLayer.push` is logged to the console
 * together with the full current state.
 */
export function gtmIframeBridgeScript(frontOrigin: string, widgetToken: string): string {
  const origin = JSON.stringify(frontOrigin);
  const prefix = JSON.stringify(`${frontOrigin}/fr/lp/${widgetToken}`);
  return (
    `(function(){var ORIGIN=${origin},TYPE=${JSON.stringify(GTM_EVENT_MESSAGE_TYPE)},` +
    `f=document.querySelector('iframe[src^='+JSON.stringify(${prefix})+']');if(!f)return;` +
    `var p=new URLSearchParams(location.search).get('clDebug'),on=false;try{if(p==='1')sessionStorage.setItem('clDebug','1');else if(p==='0')sessionStorage.removeItem('clDebug');on=sessionStorage.getItem('clDebug')==='1';}catch(x){on=p==='1';}` +
    `if(on){window.dataLayer=window.dataLayer||[];var dl=window.dataLayer,orig=dl.push;console.log('[CL dataLayer] initial state',dl.slice());` +
    `dl.push=function(){console.log('%c[CL dataLayer] push','background:#0a7;color:#fff;padding:1px 6px;border-radius:3px',arguments[0]);` +
    `var r=orig.apply(dl,arguments);console.log('[CL dataLayer] state',dl.slice());return r;};}` +
    `var q=new URLSearchParams(location.search),ref=q.get('cl_donation')==='success'?q.get('cl_ref'):null;` +
    `if(ref&&!/^[0-9a-f-]{36}$/i.test(ref))ref=null;` +
    `if(q.has('cl_donation')||q.has('cl_ref')){q.delete('cl_donation');q.delete('cl_ref');var qs=q.toString();` +
    `history.replaceState(history.state,'',location.pathname+(qs?'?'+qs:'')+location.hash);}` +
    `try{if(ref&&sessionStorage.getItem('cl-purchase-'+ref))ref=null;}catch(x){}` +
    `var u=new URL(f.src),c=false;if(!u.searchParams.get('parentOrigin')){u.searchParams.set('parentOrigin',location.origin);c=true;}` +
    `if(ref){u.searchParams.set('clRef',ref);c=true;}if(c)f.src=u.toString();` +
    `window.addEventListener('message',function(e){if(e.source!==f.contentWindow||e.origin!==ORIGIN)return;` +
    `var d=e.data;if(!d||d.type!==TYPE)return;window.dataLayer=window.dataLayer||[];window.dataLayer.push(d.payload);` +
    `if(d.payload&&d.payload.event==='purchase'&&d.payload.ecommerce){try{sessionStorage.setItem('cl-purchase-'+d.payload.ecommerce.transaction_id,'1');}catch(x){}}});})();`
  );
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
 * Only meaningful at `begin_checkout` time: `purchase` is built from server-side values once the
 * donor is back from the payment page, and carries no UTM.
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
  /**
   * Validated host origin (scheme://host[:port]) to forward this event to via `postMessage`, when
   * running inside a CommonLink iframe (`widget.js`, `landing.js`, iframe bridge) — the host page's
   * own GTM cannot see into a cross-origin child document otherwise.
   */
  parentOrigin?: string | null;
}

/**
 * Message contract with the host-side loaders (`public/widget.js`, `public/landing.js`, the iframe
 * bridge) for forwarding a GA4 ecommerce event to the host page's own `dataLayer`. Each loader keeps
 * a literal copy of this string.
 */
export const GTM_EVENT_MESSAGE_TYPE = 'cl-widget-gtm-event';

export const DonationEventName = {
  BEGIN_CHECKOUT: 'begin_checkout',
  PURCHASE: 'purchase',
} as const;
export type DonationEventName = (typeof DonationEventName)[keyof typeof DonationEventName];

/** The dataLayer object of a donation event — the same shape whether pushed locally or relayed. */
function buildDonationEventPayload(
  event: DonationEventName,
  ecommerce: DonationEcommercePayload,
  extras: DonationEventExtras,
) {
  return {
    event,
    ecommerce,
    anonymous: extras.anonymous,
    ...(extras.paymentMethod ? { payment_method: extras.paymentMethod } : {}),
    ...extras.utm,
  };
}

/**
 * Sends a GA4 ecommerce donation event to the host page only, via `postMessage` to [parentOrigin],
 * without pushing it into this iframe's own `dataLayer`. Used for `purchase`, which belongs to the
 * association's page and must run once, under that page's GTM and consent. A no-op when not
 * running inside an iframe.
 */
export function relayDonationEvent(
  event: DonationEventName,
  ecommerce: DonationEcommercePayload,
  extras: Omit<DonationEventExtras, 'parentOrigin'>,
  parentOrigin: string,
): void {
  if (typeof window === 'undefined' || window.parent === window) return;
  const payload = buildDonationEventPayload(event, ecommerce, extras);
  window.parent.postMessage({ type: GTM_EVENT_MESSAGE_TYPE, payload }, parentOrigin);
}

/**
 * Pushes a GA4 ecommerce donation event (`begin_checkout` or `purchase`) to `window.dataLayer`, and,
 * when `extras.parentOrigin` is set and this code is running inside an iframe, forwards the same
 * payload to the host page via `postMessage` so its own GTM container can push it into its own
 * `dataLayer` too.
 *
 * Safe to call even when no GTM container is configured for this association: with nothing reading
 * it, the push is a harmless no-op. `window.dataLayer` is guarded rather than assumed present —
 * GTM's own IIFE (see {@link gtmHeadScript}) creates it, but this can run before that script has
 * (e.g. no container configured at all), or outside the browser (SSR).
 */
export function pushDonationEvent(
  event: DonationEventName,
  ecommerce: DonationEcommercePayload,
  extras: DonationEventExtras,
): void {
  if (typeof window === 'undefined') return;
  const payload = buildDonationEventPayload(event, ecommerce, extras);
  window.dataLayer = window.dataLayer || [];
  window.dataLayer.push(payload);

  if (extras.parentOrigin && window.parent !== window) {
    window.parent.postMessage({ type: GTM_EVENT_MESSAGE_TYPE, payload }, extras.parentOrigin);
  }
}

declare global {
  interface Window {
    dataLayer?: unknown[];
    /** Set by `consentBootstrapScript` (see `lib/consentMode.ts`) before GTM loads. */
    gtag?: (...args: unknown[]) => void;
  }
}

