/**
 * CommonLink Donation Landing Page Loader
 *
 * Drop-in snippet — one script tag injects the full donation landing page
 * (header, sections, donation form, legal footer) as an iframe hosted on CommonLink.
 * The iframe height follows the page content: the landing posts its height on every
 * layout change, so the host page never shows an inner scrollbar.
 *
 * Standalone by design: this file shares no code with `widget.js`. The embedded widget
 * is meant to evolve inside content-rich host sites; the landing page is meant to be
 * pulled into a blank page. Coupling the two loaders would tie those roadmaps together.
 *
 * Usage:
 *   <script src="{FRONT_URL}/landing.js"
 *           data-widget-token="clk_xxx"
 *           async></script>
 *
 * Optional attributes:
 *   data-width   — iframe CSS width (default: "100%")
 *   data-height  — initial CSS height, used until the first height message
 *                  (default: "1200px")
 */
(function () {
  'use strict';

  var MESSAGE_TYPE = 'cl-landing-height';

  // Mirrors GTM_EVENT_MESSAGE_TYPE in app/src/lib/gtm.ts — must stay a literal match (this file
  // shares no code with widget.js by design, so the literal is duplicated rather than imported).
  var GTM_MESSAGE_TYPE = 'cl-widget-gtm-event';

  // DEBUG (removable): with ?clDebug=1 on the host page (kept for the tab, ?clDebug=0 to stop), log
  // the current dataLayer once and then every host `dataLayer.push` together with the full state.
  // Delete this block to remove it.
  try {
    // ?clDebug=1 turns it on and is remembered for this tab (sessionStorage) so it survives the
    // Mollie round trip back to the host page; ?clDebug=0 turns it off again.
    var dbgParam = new URLSearchParams(window.location.search).get('clDebug');
    if (dbgParam === '1') sessionStorage.setItem('clDebug', '1');
    else if (dbgParam === '0') sessionStorage.removeItem('clDebug');
    if (sessionStorage.getItem('clDebug') === '1') {
      window.dataLayer = window.dataLayer || [];
      var dbgLayer = window.dataLayer;
      var dbgOrigPush = dbgLayer.push;
      console.log('[CL dataLayer] initial state', dbgLayer.slice());
      dbgLayer.push = function () {
        console.log('%c[CL dataLayer] push', 'background:#0a7;color:#fff;padding:1px 6px;border-radius:3px', arguments[0]);
        var result = dbgOrigPush.apply(dbgLayer, arguments);
        console.log('[CL dataLayer] state', dbgLayer.slice());
        return result;
      };
    }
  } catch (e) {
    // cross-origin restriction — debug logger skipped
  }

  var script = document.currentScript;
  if (!script) return;

  // Idempotent: skip if already injected by this script tag
  if (script.getAttribute('data-cl-injected')) return;
  script.setAttribute('data-cl-injected', '1');

  var token = script.getAttribute('data-widget-token');
  if (!token) {
    console.warn('[CommonLink Landing] Missing required attribute: data-widget-token');
    return;
  }

  // Back from a confirmed payment, `/return` sends the donor here with
  // `?cl_donation=success&cl_ref=<ref>` (literal copies of DONATION_RESULT_PARAM / DONATION_REF_PARAM
  // in EmbedDonateReturnClient.tsx). Both are removed from the address bar right away — before this
  // page's URL is captured below and before a reload could see them — and the ref is handed to the
  // iframe as `clRef`: the iframe reads the confirmed values from the server and relays the GA4
  // `purchase` event to this page's dataLayer. A ref already relayed in this tab is not passed again.
  var PURCHASE_DONE_PREFIX = 'cl-purchase-';
  var donationRef = null;
  try {
    var returnQuery = new URLSearchParams(window.location.search);
    if (returnQuery.get('cl_donation') === 'success') donationRef = returnQuery.get('cl_ref');
    if (donationRef && !/^[0-9a-f-]{36}$/i.test(donationRef)) donationRef = null;
    if (returnQuery.has('cl_donation') || returnQuery.has('cl_ref')) {
      returnQuery.delete('cl_donation');
      returnQuery.delete('cl_ref');
      var remainingQuery = returnQuery.toString();
      window.history.replaceState(
        window.history.state,
        '',
        window.location.pathname + (remainingQuery ? '?' + remainingQuery : '') + window.location.hash
      );
    }
    if (donationRef && window.sessionStorage.getItem(PURCHASE_DONE_PREFIX + donationRef)) donationRef = null;
  } catch (e) {
    // sandboxed or storage-less context — no purchase relay
  }

  var width = script.getAttribute('data-width') || '100%';
  var height = script.getAttribute('data-height') || '1200px';

  // Derive FRONT_URL from this script's own src — never hardcode the environment.
  // e.g. "https://app.common-link.org/landing.js" → "https://app.common-link.org"
  var scriptSrc = script.src || '';
  var frontUrl = scriptSrc.substring(0, scriptSrc.lastIndexOf('/'));
  if (!frontUrl) {
    console.warn('[CommonLink Landing] Could not derive FRONT_URL from script src');
    return;
  }

  // Origin of the script, resolved by the browser's own URL parser. Used to authenticate
  // height messages: `event.origin` is always scheme://host[:port], so comparing it to
  // `frontUrl` would fail whenever the script is served from a sub-path.
  var resolver = document.createElement('a');
  resolver.href = scriptSrc;
  var frontOrigin = resolver.protocol + '//' + resolver.host;

  // The landing posts its height to this exact origin (never "*"), so the message cannot
  // be observed by another frame if the host page is itself embedded somewhere.
  var parentOrigin = '';
  try {
    parentOrigin = window.location.origin || '';
  } catch (e) {
    // cross-origin restriction in some sandboxes — auto-resize is then skipped
  }

  // Locale is pinned to "fr": the landing page is French-only for now (a data-locale
  // attribute will be added when the other locales are actually served).
  var iframeSrc = frontUrl + '/fr/lp/' + encodeURIComponent(token);
  var iframeParams = [];
  if (parentOrigin) {
    iframeParams.push('parentOrigin=' + encodeURIComponent(parentOrigin));
  }
  if (donationRef) {
    iframeParams.push('clRef=' + encodeURIComponent(donationRef));
  }
  if (iframeParams.length) {
    iframeSrc += '?' + iframeParams.join('&');
  }

  var iframe = document.createElement('iframe');
  iframe.src = iframeSrc;

  // Accessible title (screen-reader + DevTools label)
  iframe.title = 'Faire un don';

  // Responsive: use CSS, not HTML width/height attributes (supports "100%", "px", etc.)
  iframe.style.cssText =
    'width:' + width + ';height:' + height + ';border:0;display:block;';

  // Lazy by default, but not on the way back from a payment: an iframe below the fold would never
  // load, and the purchase relay with it.
  if (!donationRef) iframe.setAttribute('loading', 'lazy');
  iframe.setAttribute('referrerpolicy', 'no-referrer-when-downgrade');

  // sandbox flags (each justified):
  //   allow-scripts                        — run the Next.js bundle inside the iframe
  //   allow-forms                          — submit the donation form
  //   allow-popups                         — open Mollie checkout in a new tab (fallback)
  //   allow-top-navigation-by-user-activation — redirect the top window to Mollie on user click
  //   allow-same-origin                    — let the iframe call our own API (same origin as FRONT_URL)
  //
  // SECURITY NOTE: combining allow-same-origin with allow-scripts removes the sandbox
  // isolation for same-origin content. This is intentional and safe here because the
  // iframe src is our own controlled origin (FRONT_URL), not untrusted third-party
  // content. A malicious host page cannot exploit this to escape into CommonLink.
  iframe.setAttribute(
    'sandbox',
    'allow-scripts allow-forms allow-popups allow-top-navigation-by-user-activation allow-same-origin'
  );

  // Auto-resize and GTM event bridge — registered before insertion so the very first message
  // cannot be missed. Every guard matters: only our own iframe (source), only our own origin, only
  // our own message shapes, only a sane positive number.
  window.addEventListener('message', function (event) {
    if (event.source !== iframe.contentWindow) return;
    if (event.origin !== frontOrigin) return;
    var data = event.data;
    if (!data) return;

    if (data.type === GTM_MESSAGE_TYPE) {
      // The landing loads no GTM of its own once embedded here (parentOrigin set): this page's own
      // container — the association's — is the only one, and receives the GA4 ecommerce events
      // (`begin_checkout`, `purchase`) through this bridge, under this page's consent.
      window.dataLayer = window.dataLayer || [];
      window.dataLayer.push(data.payload);
      // Remember the relayed purchase for this tab: a reload must never send it again.
      if (data.payload && data.payload.event === 'purchase' && data.payload.ecommerce) {
        try {
          window.sessionStorage.setItem(PURCHASE_DONE_PREFIX + data.payload.ecommerce.transaction_id, '1');
        } catch (e) {
          // storage unavailable — the address bar was already cleaned, a plain reload is still safe
        }
      }
      return;
    }

    if (data.type !== MESSAGE_TYPE) return;
    var reported = Number(data.height);
    if (!isFinite(reported) || reported <= 0) return;
    iframe.style.height = Math.ceil(reported) + 'px';
  });

  // Insert immediately after the <script> tag (or at end of parent if last child)
  var parent = script.parentNode;
  if (parent) {
    parent.insertBefore(iframe, script.nextSibling);
  }
})();
