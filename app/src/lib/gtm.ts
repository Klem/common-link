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

