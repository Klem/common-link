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
  if (parentOrigin) {
    iframeSrc += '?parentOrigin=' + encodeURIComponent(parentOrigin);
  }

  var iframe = document.createElement('iframe');
  iframe.src = iframeSrc;

  // Accessible title (screen-reader + DevTools label)
  iframe.title = 'Faire un don';

  // Responsive: use CSS, not HTML width/height attributes (supports "100%", "px", etc.)
  iframe.style.cssText =
    'width:' + width + ';height:' + height + ';border:0;display:block;';

  iframe.setAttribute('loading', 'lazy');
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

  // Auto-resize — registered before insertion so the very first message cannot be missed.
  // Every guard matters: only our own iframe (source), only our own origin, only our own
  // message shape, only a sane positive number.
  window.addEventListener('message', function (event) {
    if (event.source !== iframe.contentWindow) return;
    if (event.origin !== frontOrigin) return;
    var data = event.data;
    if (!data || data.type !== MESSAGE_TYPE) return;
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
