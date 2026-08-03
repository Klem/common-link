import type { NextConfig } from 'next';
import createNextIntlPlugin from 'next-intl/plugin';

const withNextIntl = createNextIntlPlugin('./src/i18n/request.ts');

const apiUrl = process.env.NEXT_PUBLIC_API_URL || 'http://localhost:8080';

const cspDirectives = [
  "default-src 'self'",
  // Next.js dev + prod inline scripts
  "script-src 'self' 'unsafe-inline' 'unsafe-eval' https://accounts.google.com/gsi/",
  "style-src 'self' 'unsafe-inline' https://accounts.google.com/gsi/",
  "font-src 'self'",
  // Google OAuth frames + API
  "frame-src https://accounts.google.com/",
  `connect-src 'self' ${apiUrl} https://accounts.google.com/ https://journal-officiel-datadila.opendatasoft.com`,
  "img-src 'self' data: https:",
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
    ];
  },
};

export default withNextIntl(nextConfig);
