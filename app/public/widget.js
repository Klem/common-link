/**
 * CommonLink Donation Widget Loader
 *
 * Drop-in snippet — one script tag injects a sandboxed iframe hosted on CommonLink.
 *
 * Usage:
 *   <script src="{FRONT_URL}/widget.js"
 *           data-widget-token="clk_xxx"
 *           async></script>
 *
 * The iframe height follows the form's content: it posts its height on every layout change
 * (field validation errors, address autocomplete, etc.), so the host page never shows an inner
 * scrollbar.
 *
 * Optional attributes:
 *   data-width   — iframe CSS width (default: "100%")
 *   data-height  — initial CSS height, used until the first height message (default: "640px")
 *   data-locale  — UI language: "fr" or "en" (default: "fr")
 *   data-embed   — set to "html" by the standalone HTML export only; reported as the `embed_type`
 *                  of the donation events (default: "widget")
 */
(function () {
  'use strict';

  var MESSAGE_TYPE = 'cl-widget-height';

  // Mirrors GTM_EVENT_MESSAGE_TYPE in app/src/lib/gtm.ts — must stay a literal match.
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
    console.warn('[CommonLink Widget] Missing required attribute: data-widget-token');
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
  var height = script.getAttribute('data-height') || '640px';
  var locale = script.getAttribute('data-locale') || 'fr';
  // Integration mode reported on the donation events (`embed_type`) — the page validates it.
  var embedType = script.getAttribute('data-embed') === 'html' ? 'html' : 'widget';

  // Derive FRONT_URL from this script's own src — never hardcode the environment.
  // e.g. "https://app.common-link.org/widget.js" → "https://app.common-link.org"
  var scriptSrc = script.src || '';
  var frontUrl = scriptSrc.substring(0, scriptSrc.lastIndexOf('/'));
  if (!frontUrl) {
    console.warn('[CommonLink Widget] Could not derive FRONT_URL from script src');
    return;
  }

  // Capture the full host page URL (best-effort, non-trusted — backend sanitises).
  // Persisted as donations.source_site and used as the post-payment redirect target.
  // Never used for security decisions: the redirect is gated server-side/client-side
  // by the association's widgetAllowedOrigin (origin match), not by this value.
  var sourceUrl = '';
  try {
    sourceUrl = window.location.href || '';
  } catch (e) {
    // cross-origin restriction in some sandboxes
  }
  if (!sourceUrl && document.referrer) {
    // referrer is already a full URL — keep it as-is
    sourceUrl = document.referrer;
  }

  // Origin of the script, resolved by the browser's own URL parser. Used to authenticate
  // height messages: `event.origin` is always scheme://host[:port], so comparing it to
  // `frontUrl` would fail whenever the script is served from a sub-path.
  var resolver = document.createElement('a');
  resolver.href = scriptSrc;
  var frontOrigin = resolver.protocol + '//' + resolver.host;

  // The widget posts its height to this exact origin (never "*"), so the message cannot be
  // observed by another frame if the host page is itself embedded somewhere.
  var parentOrigin = '';
  try {
    parentOrigin = window.location.origin || '';
  } catch (e) {
    // cross-origin restriction in some sandboxes — auto-resize is then skipped
  }

  var iframeSrc =
    frontUrl +
    '/' +
    locale +
    '/embed/donate/' +
    encodeURIComponent(token);

  var iframeParams = ['clEmbed=' + embedType];
  if (sourceUrl) {
    iframeParams.push('source=' + encodeURIComponent(sourceUrl));
  }
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
  iframe.title = 'Formulaire de don';

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
  // SECURITY NOTE: combining allow-same-origin with allow-scripts removes the
  // sandbox isolation for same-origin content.  This is intentional and safe here
  // because the iframe src is our own controlled origin (FRONT_URL), not
  // untrusted third-party content.  A malicious host page cannot exploit this to
  // escape into CommonLink — the risk runs the other direction (our iframe
  // could reach the host page's DOM), which is already possible without sandbox
  // when both pages share an origin.
  iframe.setAttribute(
    'sandbox',
    'allow-scripts allow-forms allow-popups allow-top-navigation-by-user-activation allow-same-origin'
  );

  // Auto-resize and GTM event bridge — registered before insertion so the very first message
  // cannot be missed. Every guard matters: only our own iframe (source), only our own origin, only
  // our own message shapes.
  window.addEventListener('message', function (event) {
    if (event.source !== iframe.contentWindow) return;
    if (event.origin !== frontOrigin) return;
    var data = event.data;
    if (!data) return;

    if (data.type === MESSAGE_TYPE) {
      var reported = Number(data.height);
      if (!isFinite(reported) || reported <= 0) return;
      iframe.style.height = Math.ceil(reported) + 'px';
      return;
    }

    if (data.type === GTM_MESSAGE_TYPE) {
      // The form loads no GTM of its own once embedded here (parentOrigin set): this page's own
      // container — the association's — is the only one, and receives the GA4 ecommerce events
      // (`begin_checkout`, `purchase`) through this bridge. Push them into its `dataLayer` so a
      // Custom Event trigger picks them up.
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
    }
  });

  // Insert immediately after the <script> tag (or at end of parent if last child)
  var parent = script.parentNode;
  if (parent) {
    parent.insertBefore(iframe, script.nextSibling);
  }
})();
