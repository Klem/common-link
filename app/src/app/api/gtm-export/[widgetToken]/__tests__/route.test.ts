import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import { NextRequest } from 'next/server';
import { GET } from '../route';

const FAKE_LANDING_HTML = `<!DOCTYPE html>
<html lang="fr">
<head>
<link rel="stylesheet" href="/_next/static/css/abc123.css">
<script src="/_next/static/chunks/webpack.js?v=1" id="_R_" async></script>
<title>Ma campagne — Mon Asso</title>
<meta name="description" content="Description de la campagne">
</head>
<body>
<script>(self.__next_f=self.__next_f||[]).push([0])</script>
<div class="lp-root" data-theme="WARM">
<script type="application/ld+json">{"@context":"https://schema.org"}</script>
<header class="lp-header-bar">Mon Asso</header>
<section class="lp-hero"><a href="#don" class="lp-hero-cta">Faire un don</a></section>
<div class="lp-layout">
<main class="lp-main"><section class="lp-section lp-project">Contenu projet</section>
<div class="lp-budget-item">
<div class="lp-budget-row"><span class="lp-budget-label">Rémunérations</span><span class="lp-budget-pct">41&nbsp;%</span></div>
<div class="lp-bar-track"><div class="lp-bar-fill" style="width:0%;background:var(--lp-primary)"></div></div>
</div>
</main>
<aside class="lp-sidebar">
<div class="lp-sidebar-sticky">
<div id="don" class="lp-donation-panel">
<form><div><input type="number" /><div><button>Donner</button></div></div></form>
</div>
</div>
</aside>
</div>
<div class="lp-sticky-bar" aria-hidden="true">Sticky CTA</div>
<footer class="lp-footer">Mentions légales<button type="button" class="lp-footer-report">Signaler cette campagne</button></footer>
</div>
<next-route-announcer style="position: absolute;"></next-route-announcer>
</body>
</html>`;

const FAKE_CSS =
  ".lp-root { --lp-primary: #C2410C; } @font-face { src: url(/_next/static/media/abc123-s.woff2) format('woff2'); }";

function mockFetchSequence() {
  return vi.fn((url: string) => {
    if (url.includes('/_next/static/css/')) {
      return Promise.resolve({ ok: true, text: async () => FAKE_CSS } as Response);
    }
    return Promise.resolve({ ok: true, text: async () => FAKE_LANDING_HTML } as Response);
  });
}

function makeRequest(widgetToken: string, gtmId: string) {
  const url = `https://app.common-link.org/api/gtm-export/${widgetToken}?gtmId=${encodeURIComponent(gtmId)}`;
  return { request: new NextRequest(url), params: Promise.resolve({ widgetToken }) };
}

describe('GET /api/gtm-export/[widgetToken]', () => {
  beforeEach(() => {
    vi.stubGlobal('fetch', mockFetchSequence());
  });

  afterEach(() => {
    vi.unstubAllEnvs();
  });

  it('rejects a malformed gtmId without fetching anything', async () => {
    const fetchSpy = vi.fn();
    vi.stubGlobal('fetch', fetchSpy);
    const { request, params } = makeRequest('clk_abc', 'not-a-gtm-id');

    const res = await GET(request, { params });

    expect(res.status).toBe(400);
    expect(fetchSpy).not.toHaveBeenCalled();
  });

  it('strips every script, inlines the stylesheet, swaps the donation panel for widget.js, and injects GTM', async () => {
    const { request, params } = makeRequest('clk_abc', 'GTM-ABC1234');

    const res = await GET(request, { params });
    const { html } = await res.json();

    // No script survives except the ones we deliberately add.
    expect(html).not.toContain('_next/static/chunks');
    expect(html).not.toContain('__next_f');
    expect(html).not.toContain('next-route-announcer');
    expect(html).not.toContain('application/ld+json');

    // CSS content inlined, not linked.
    expect(html).not.toContain('_next/static/css');
    expect(html).toContain('--lp-primary: #C2410C');

    // Font (and any other) root-relative url() references rewritten to the public origin — left
    // as `/_next/...` they'd resolve against whatever site this export gets pasted into, not the
    // app that actually serves the fonts.
    expect(html).not.toContain('url(/_next');
    expect(html).toContain('url(https://app.common-link.org/_next/static/media/abc123-s.woff2)');

    // Dead JS-driven chrome removed.
    expect(html).not.toContain('lp-sticky-bar');

    // Donation panel replaced by the stable widget.js loader, anchor preserved for the hero CTA.
    expect(html).not.toContain('lp-donation-panel');
    expect(html).toContain('<div id="don">');
    expect(html).toContain('src="https://app.common-link.org/widget.js" data-widget-token="clk_abc" async');

    // Real content survives.
    expect(html).toContain('Mon Asso');
    expect(html).toContain('Contenu projet');
    expect(html).toContain('Mentions légales');

    // The report button has no JS to open its modal in this export — swapped for a plain link
    // to the standalone report page, which hydrates on its own.
    expect(html).not.toContain('<button type="button" class="lp-footer-report">');
    expect(html).toContain(
      '<a href="https://app.common-link.org/fr/report/clk_abc" class="lp-footer-report" target="_blank" rel="noopener noreferrer">Signaler cette campagne</a>',
    );

    // Budget bar filled to its real percentage — SSR always renders 0% (scroll-triggered
    // animation, no JS in this export to trigger it).
    expect(html).toContain('width:41%;background:var(--lp-primary)');

    // GTM tags present, official placement.
    expect(html).toContain("'GTM-ABC1234'");
    expect(html).toContain('googletagmanager.com/ns.html?id=GTM-ABC1234');

    // No hydration here to run the React banner's useEffect — this export needs its own
    // vanilla-JS consent gate, wired the same way the live page is: default-denied before gtm.js,
    // banner nested inside `.lp-root` (not a body sibling) so it inherits the theme's CSS vars.
    const consentDefaultIdx = html.indexOf("gtag('consent','default'");
    const gtmLoaderIdx = html.indexOf('GTM-ABC1234');
    const lpRootCloseIdx = html.lastIndexOf('</div>');
    const bannerIdx = html.indexOf('id="cl-cookie-consent"');
    expect(consentDefaultIdx).toBeGreaterThan(-1);
    expect(consentDefaultIdx).toBeLessThan(gtmLoaderIdx);
    expect(bannerIdx).toBeGreaterThan(-1);
    expect(bannerIdx).toBeLessThan(lpRootCloseIdx);
    expect(html).toContain('cl-consent-clk_abc');

    expect(html).toContain('<!DOCTYPE html>');
    expect(html).toContain('<title>Ma campagne — Mon Asso</title>');
  });

  it('returns 404 when the landing page itself 404s', async () => {
    vi.stubGlobal('fetch', vi.fn(() => Promise.resolve({ ok: false, status: 404 } as Response)));
    const { request, params } = makeRequest('unknown', 'GTM-ABC1234');

    const res = await GET(request, { params });

    expect(res.status).toBe(404);
  });

  it('embeds NEXT_PUBLIC_APP_URL instead of the request origin when set', async () => {
    // Reproduces the real bug: an export triggered through a local tunnel/port-forward carries
    // a request origin like "http://localhost:8080" that never matches the public site — the
    // exported widget script and report link must still point at the real public app URL.
    vi.stubEnv('NEXT_PUBLIC_APP_URL', 'https://app.common-link.org');
    const url = `http://localhost:8080/api/gtm-export/clk_abc?gtmId=${encodeURIComponent('GTM-ABC1234')}`;
    const request = new NextRequest(url);

    const res = await GET(request, { params: Promise.resolve({ widgetToken: 'clk_abc' }) });
    const { html } = await res.json();

    expect(html).not.toContain('localhost:8080');
    expect(html).toContain('src="https://app.common-link.org/widget.js" data-widget-token="clk_abc" async');
    expect(html).toContain('href="https://app.common-link.org/fr/report/clk_abc"');
  });
});
