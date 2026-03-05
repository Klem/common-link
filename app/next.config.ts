import type { NextConfig } from 'next';
import createNextIntlPlugin from 'next-intl/plugin';

const withNextIntl = createNextIntlPlugin('./src/i18n/request.ts');

const nextConfig: NextConfig = {
  async headers() {
    return [
      {
        source: '/(.*)',
        headers: [
          {
            key: 'Content-Security-Policy',
            value: [
              "default-src 'self'",
              // Next.js dev + prod inline scripts
              "script-src 'self' 'unsafe-inline' 'unsafe-eval' https://accounts.google.com/gsi/",
              "style-src 'self' 'unsafe-inline' https://fonts.googleapis.com https://accounts.google.com/gsi/",
              "font-src 'self' https://fonts.gstatic.com",
              // Google OAuth frames + API
              "frame-src https://accounts.google.com/",
              "connect-src 'self' http://localhost:8080 https://accounts.google.com/ https://api.annuaire-entreprises.data.gouv.fr",
              "img-src 'self' data: https:",
            ].join('; '),
          },
        ],
      },
    ];
  },
};

export default withNextIntl(nextConfig);
