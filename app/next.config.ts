import type { NextConfig } from 'next';
import createNextIntlPlugin from 'next-intl/plugin';

const withNextIntl = createNextIntlPlugin('./src/i18n/request.ts');

const apiUrl = process.env.NEXT_PUBLIC_API_URL || 'http://localhost:8080';

const cspDirectives = [
  "default-src 'self'",
  // Next.js dev + prod inline scripts + GTM's self-injected gtm.js (Google Ad Grants tracking)
  "script-src 'self' 'unsafe-inline' 'unsafe-eval' https://accounts.google.com/gsi/ https://www.googletagmanager.com",
  "style-src 'self' 'unsafe-inline' https://accounts.google.com/gsi/",
  "font-src 'self'",
  // Google OAuth frames + GTM's noscript fallback iframe (ns.html)
  "frame-src 'self' https://accounts.google.com/ https://www.googletagmanager.com",
  // Both public registries are queried straight from the browser during association sign-up:
  // JOAFE for RNA numbers, Recherche d'entreprises for associations that only have a SIREN.
  `connect-src 'self' ${apiUrl} https://accounts.google.com/ https://journal-officiel-datadila.opendatasoft.com https://recherche-entreprises.api.gouv.fr`,
  `img-src 'self' data: https: ${apiUrl}`,
];

const nextConfig: NextConfig = {
  async headers() {
    return [
      {
        // All routes: block framing by third parties (anti-clickjacking)
        source: '/(.*)',
        headers: [
          {
            key: 'Content-Security-Policy',
            value: [...cspDirectives, "frame-ancestors 'self'"].join('; '),
          },
        ],
      },
      {
        // Embed routes: allow framing from any origin (donation widget)
        // frame-ancestors * is intentional — the widget must be embeddable on partner sites.
        // X-Frame-Options is NOT set here: it does not support wildcards and would conflict.
        source: '/:locale/embed/:path*',
        headers: [
          {
            key: 'Content-Security-Policy',
            value: [...cspDirectives, 'frame-ancestors *'].join('; '),
          },
        ],
      },
      {
        // Landing pages: same exception as the embed routes. `public/landing.js` and the
        // JavaScript-free iframe fallback both frame this route from the association's own
        // domain, so the default `frame-ancestors 'self'` would break the published snippet.
        source: '/:locale/lp/:path*',
        headers: [
          {
            key: 'Content-Security-Policy',
            value: [...cspDirectives, 'frame-ancestors *'].join('; '),
          },
        ],
      },
    ];
  },
};

export default withNextIntl(nextConfig);
