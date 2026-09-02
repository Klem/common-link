import { NextRequest, NextResponse } from 'next/server';
import { parse } from 'node-html-parser';
import { GTM_ID_PATTERN, gtmHeadScript, gtmNoscriptIframe } from '@/lib/gtm';
import { consentBannerStandaloneHtml, consentBootstrapScript } from '@/lib/consentMode';

interface RouteParams {
  params: Promise<{ widgetToken: string }>;
}

function escapeHtml(text: string): string {
  return text
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;');
}

/**
 * Turns the real, server-rendered `/fr/lp/{widgetToken}` page into a standalone export: same
 * markup (header, hero, sections, legal footer), inlined CSS, and GTM tags — with every script
 * stripped and the donation form swapped for the `widget.js` loader.
 *
 * The strip is deliberate, not a shortcut: Next.js content-hashes `_next` chunks and prunes old
 * ones on every deploy, so a copy referencing them would break silently after the next release.
 * `widget.js` and the CSS (inlined as text, not linked) have no such expiry — this is what makes
 * the exported file survive being pasted into a blank page on the association's own site forever.
 *
 * Public and unauthenticated like `/api/public/landing/{widgetToken}` itself: everything returned
 * here is already visible to anyone who opens the real landing page.
 */
export async function GET(request: NextRequest, { params }: RouteParams) {
  const { widgetToken } = await params;
  const gtmId = request.nextUrl.searchParams.get('gtmId') ?? '';
  if (!GTM_ID_PATTERN.test(gtmId)) {
    return NextResponse.json({ error: 'Invalid or missing gtmId' }, { status: 400 });
  }

  // The exported file is a permanent artifact — pasted into the association's own site, or just
  // sitting in a "Downloads" folder — that must keep working regardless of how *this* export
  // request happened to reach the server. `request.nextUrl.origin` is the Host/proto of that one
  // request, not the platform's real public address: whoever triggers this export might be going
  // through a local tunnel, a port-forward, or any other indirection that never matches what a
  // donor's browser will actually load. NEXT_PUBLIC_APP_URL is the fixed, deploy-time source of
  // truth instead; the request origin is only a fallback for environments that don't set it
  // (e.g. local dev).
  const origin = process.env.NEXT_PUBLIC_APP_URL || request.nextUrl.origin;
  // Fetching the page itself hairpins back through the platform's reverse proxy — on Clever Cloud
  // that self-loop is unreachable from inside the very container serving it ("fetch failed"), so
  // this internal call always goes to the local port instead, independent of `origin` above.
  const internalOrigin = `http://127.0.0.1:${process.env.PORT || 3000}`;
  const pageUrl = `${internalOrigin}/fr/lp/${encodeURIComponent(widgetToken)}`;

  let pageRes: Response;
  try {
    pageRes = await fetch(pageUrl);
  } catch (err) {
    return NextResponse.json(
      { error: 'Could not reach the landing page', detail: err instanceof Error ? err.message : String(err) },
      { status: 502 },
    );
  }
  if (!pageRes.ok) {
    return NextResponse.json(
      { error: 'Landing page unavailable', upstreamStatus: pageRes.status },
      { status: pageRes.status === 404 ? 404 : 502 },
    );
  }
  const pageHtml = await pageRes.text();
  const doc = parse(pageHtml);

  const lpRoot = doc.querySelector('.lp-root');
  if (!lpRoot) {
    return NextResponse.json({ error: 'Landing content not found' }, { status: 502 });
  }

  // Strip everything JS-dependent — see the file-level doc comment for why.
  lpRoot.querySelectorAll('script, noscript, next-route-announcer').forEach((el) => el.remove());

  // JS-driven mobile CTA (scroll listener, visibility toggle) — dead chrome without React.
  lpRoot.querySelector('.lp-sticky-bar')?.remove();

  // Budget bars grow from 0% on scroll-into-view (IntersectionObserver in TransparencySection),
  // so the SSR markup always has `width:0%` — there is no scroll event to trigger it here. Set
  // the real width directly from the percentage already rendered as text next to each bar.
  lpRoot.querySelectorAll('.lp-budget-item').forEach((item) => {
    const pctText = item.querySelector('.lp-budget-pct')?.text ?? '';
    const pct = pctText.match(/(\d+(?:\.\d+)?)/)?.[1];
    const fill = item.querySelector('.lp-bar-fill');
    if (pct && fill) {
      fill.setAttribute('style', (fill.getAttribute('style') ?? '').replace(/width:[^;]*/, `width:${pct}%`));
    }
  });

  // Swap the React donation form for the stable widget.js loader. `id="don"` is preserved so the
  // hero's "#don" anchor (rendered above, untouched) still scrolls to the right place.
  const sidebar = lpRoot.querySelector('.lp-sidebar-sticky') ?? lpRoot.querySelector('.lp-sidebar');
  if (sidebar) {
    sidebar.innerHTML =
      `<div id="don"><script src="${origin}/widget.js" data-widget-token="${escapeHtml(widgetToken)}" async></script></div>`;
  }

  // The "report this campaign" footer button only works via React state (opens an in-page
  // modal) — with scripts stripped it would be dead markup. Point it at the standalone report
  // page instead, a real Next.js route that hydrates on its own regardless of this host page.
  const reportTrigger = lpRoot.querySelector('.lp-footer-report');
  if (reportTrigger) {
    const reportUrl = `${origin}/fr/report/${encodeURIComponent(widgetToken)}`;
    reportTrigger.replaceWith(
      `<a href="${reportUrl}" class="lp-footer-report" target="_blank" rel="noopener noreferrer">${escapeHtml(reportTrigger.text)}</a>`,
    );
  }

  // Appended as the last child of `.lp-root` — same position as the React banner (after
  // LegalFooter) — not as a sibling in `<body>`: `--lp-primary`/`--lp-bg`/etc. are custom
  // properties declared on `.lp-root` and its `[data-theme]` variant, and only inherit to
  // descendants. A sibling would render the banner with none of the theme's colors.
  lpRoot.innerHTML = lpRoot.innerHTML + consentBannerStandaloneHtml(widgetToken);

  // Inline every stylesheet's actual content — not a link to a hashed file — so styling survives
  // deploys too. landing.css has no @import, but it does declare @font-face rules with
  // root-relative `url(/_next/static/media/...)` sources: left as-is, those resolve against
  // whatever origin ends up hosting this exported file (the association's own site), not against
  // the Next.js app that actually serves the fonts — a silent 404 that falls back to system fonts.
  // Rewritten to the public origin here, the same way the widget.js and report links already are.
  const styleLinks = doc.querySelectorAll('link[rel="stylesheet"]');
  const cssChunks = await Promise.all(
    styleLinks.map(async (link) => {
      const href = link.getAttribute('href');
      if (!href) return '';
      const absoluteHref = href.startsWith('http') ? href : `${internalOrigin}${href}`;
      try {
        const cssRes = await fetch(absoluteHref);
        if (!cssRes.ok) return '';
        const css = await cssRes.text();
        return css.replace(/url\((['"]?)\//g, `url($1${origin}/`);
      } catch {
        return '';
      }
    }),
  );

  const title = escapeHtml(doc.querySelector('title')?.text ?? '');
  const description = escapeHtml(doc.querySelector('meta[name="description"]')?.getAttribute('content') ?? '');

  const html = [
    '<!DOCTYPE html>',
    '<html lang="fr">',
    '<head>',
    '<meta charset="UTF-8">',
    '<meta name="viewport" content="width=device-width, initial-scale=1">',
    title && `<title>${title}</title>`,
    description && `<meta name="description" content="${description}">`,
    cssChunks.some(Boolean) && `<style>${cssChunks.join('\n')}</style>`,
    // Consent default (denied) must be set before gtm.js loads — same ordering constraint as the
    // live page (see consentMode.ts). Without it, this exported file would load GTM completely
    // ungated by consent, defeating the whole point of Consent Mode v2 the moment it's pasted onto
    // the association's own site.
    `<script>${consentBootstrapScript(widgetToken)}</script>`,
    `<script>${gtmHeadScript(gtmId)}</script>`,
    '</head>',
    '<body>',
    `<noscript>${gtmNoscriptIframe(gtmId)}</noscript>`,
    lpRoot.outerHTML,
    '</body>',
    '</html>',
  ]
    .filter(Boolean)
    .join('\n');

  return NextResponse.json({ html });
}
