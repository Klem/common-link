import type { NextConfig } from 'next';
import createNextIntlPlugin from 'next-intl/plugin';

const withNextIntl = createNextIntlPlugin('./src/i18n/request.ts');

// Content-Security-Policy is generated per-request in `src/middleware.ts` instead of here: a
// nonce-based policy needs a value that changes on every request, which this static `headers()`
// config cannot produce (security audit 2026-10-06, finding #1 — see middleware.ts for the
// directives themselves). Do not reintroduce a CSP here: Next.js would send both header values,
// and CSP combines multiple policies by intersection — a static 'unsafe-inline' here would
// silently blanket-permit what the nonce policy tries to restrict.
const nextConfig: NextConfig = {};

export default withNextIntl(nextConfig);
