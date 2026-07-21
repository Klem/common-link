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
 * Optional attributes:
 *   data-width   — iframe CSS width  (default: "100%")
 *   data-height  — iframe CSS height (default: "640px")
 *   data-locale  — UI language: "fr" or "en" (default: "fr")
 */
(function () {
  'use strict';

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

  var width = script.getAttribute('data-width') || '100%';
  var height = script.getAttribute('data-height') || '640px';
  var locale = script.getAttribute('data-locale') || 'fr';

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

  var iframeSrc =
    frontUrl +
    '/' +
    locale +
    '/embed/donate/' +
    encodeURIComponent(token);

  if (sourceUrl) {
    iframeSrc += '?source=' + encodeURIComponent(sourceUrl);
  }

  var iframe = document.createElement('iframe');
  iframe.src = iframeSrc;

  // Accessible title (screen-reader + DevTools label)
  iframe.title = 'Formulaire de don';

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

  // TODO (optional): listen for postMessage from the iframe to auto-resize height.
  // Requires coordinating a postMessage send from EmbedDonateClient.tsx.
  // When implemented, verify message.origin === frontUrl before trusting the payload.

  // Insert immediately after the <script> tag (or at end of parent if last child)
  var parent = script.parentNode;
  if (parent) {
    parent.insertBefore(iframe, script.nextSibling);
  }
})();
