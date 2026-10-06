import createMiddleware from 'next-intl/middleware';
import { NextRequest, NextResponse } from 'next/server';
import { routing } from './i18n/routing';
import { ROUTES } from './lib/routes';
import { getRedirectForRole } from './lib/routeGuard';

const intlMiddleware = createMiddleware(routing);

const PROTECTED_PATHS = ['/dashboard', '/settings', '/admin'];

const API_URL = process.env.NEXT_PUBLIC_API_URL || 'http://localhost:8080';

/**
 * Content-Security-Policy, generated per-request (security audit 2026-10-06, finding #1).
 *
 * `'strict-dynamic'` + a per-request nonce replaces the old static `'unsafe-inline'
 * 'unsafe-eval'`: a `<script nonce={nonce}>` is trusted, and Google Tag Manager's own gtm.js
 * propagates that trust to every tag it subsequently injects (this is GTM's documented CSP
 * integration — see developers.google.com/tag-platform/security/guides/csp), so configured GTM
 * tags keep working. `'unsafe-eval'` is dropped entirely; Next.js's production bundle doesn't
 * need it, only `next dev`'s HMR does.
 *
 * This narrows the attack surface for unrelated/future inline-script injection bugs. It does
 * NOT stop a malicious Custom HTML tag configured inside an association's *own* GTM container —
 * GTM propagates the nonce to those too, by design. Closing that specific vector needs isolating
 * `/lp` and `/embed` on a separate site, tracked separately (not a pure code fix).
 */
function buildCsp(pathname: string, nonce: string): string {
  const barePath = stripLocale(pathname);
  const frameAncestors =
    barePath.startsWith('/embed/') || barePath.startsWith('/lp/') ? '*' : "'self'";
  const devEval = process.env.NODE_ENV !== 'production' ? " 'unsafe-eval'" : '';

  return [
    "default-src 'self'",
    `script-src 'self' 'nonce-${nonce}' 'strict-dynamic' https://accounts.google.com/gsi/ https://www.googletagmanager.com${devEval}`,
    "style-src 'self' 'unsafe-inline' https://accounts.google.com/gsi/",
    "font-src 'self'",
    // Google OAuth frames + GTM's noscript fallback iframe (ns.html)
    "frame-src 'self' https://accounts.google.com/ https://www.googletagmanager.com",
    // Both public registries are queried straight from the browser during association sign-up:
    // JOAFE for RNA numbers, Recherche d'entreprises for associations that only have a SIREN.
    `connect-src 'self' ${API_URL} https://accounts.google.com/ https://journal-officiel-datadila.opendatasoft.com https://recherche-entreprises.api.gouv.fr https://geo.api.gouv.fr`,
    `img-src 'self' data: https: ${API_URL}`,
    `frame-ancestors ${frameAncestors}`,
  ].join('; ');
}

function withSecurityHeaders(response: NextResponse, csp: string): NextResponse {
  response.headers.set('Content-Security-Policy', csp);
  return response;
}

/** Edge-runtime-safe random nonce (no `Buffer` — Web Crypto + `btoa` only). */
function generateNonce(): string {
  const bytes = crypto.getRandomValues(new Uint8Array(16));
  let binary = '';
  for (const byte of bytes) binary += String.fromCharCode(byte);
  return btoa(binary);
}

function isProtectedPath(pathname: string): boolean {
  // Strip the locale prefix (e.g. /fr/dashboard → /dashboard)
  const withoutLocale = pathname.replace(/^\/[a-z]{2}(\/|$)/, '/');
  return PROTECTED_PATHS.some(
    (p) => withoutLocale === p || withoutLocale.startsWith(`${p}/`),
  );
}

function isLoginPath(pathname: string): boolean {
  const withoutLocale = pathname.replace(/^\/[a-z]{2}(\/|$)/, '/');
  return withoutLocale === ROUTES.LOGIN;
}

/** Strip locale prefix and return the bare path (e.g. /fr/dashboard/donor → /dashboard/donor) */
function stripLocale(pathname: string): string {
  return pathname.replace(/^\/[a-z]{2}(\/|$)/, '/');
}

interface AuthSession {
  userId: string;
  role: string;
}

function parseAuthSession(raw: string): AuthSession | null {
  try {
    const parsed = JSON.parse(raw) as unknown;
    if (
      typeof parsed === 'object' &&
      parsed !== null &&
      'role' in parsed &&
      typeof (parsed as Record<string, unknown>).role === 'string'
    ) {
      return parsed as AuthSession;
    }
    return null;
  } catch {
    return null;
  }
}

export function middleware(request: NextRequest) {
  const { pathname } = request.nextUrl;

  // Per-request CSP nonce (security audit 2026-10-06, finding #1).
  //
  // Set on the REQUEST, not just the response: Next.js's own renderer looks for a
  // `content-security-policy` request header to auto-nonce its own framework/hydration scripts
  // (see `getScriptNonceFromHeader` in `next/dist/server/app-render/app-render.js`) — without it,
  // every script Next.js itself injects has no nonce and `'strict-dynamic'` blocks it outright,
  // breaking the whole page's JS, not just GTM. `x-nonce` is a second, separate header: it's how
  // *our own* Server Components (GtmSnippet, the consent/JSON-LD scripts) read the same nonce via
  // `headers()` to stamp it on the `<script>` tags we author ourselves.
  const nonce = generateNonce();
  const csp = buildCsp(pathname, nonce);
  request.headers.set('x-nonce', nonce);
  request.headers.set('content-security-policy', csp);

  // API routes are never locale-prefixed: falling through to `intlMiddleware` below would
  // redirect e.g. `/api/gtm-export/x` to `/fr/api/gtm-export/x`, a path no route handler
  // matches (route handlers live outside `[locale]`) — a silent 404 for every API call.
  if (pathname.startsWith('/api/') || pathname === '/api') {
    return withSecurityHeaders(NextResponse.next(), csp);
  }

  const locale = pathname.match(/^\/([a-z]{2})(\/|$)/)?.[1] ?? routing.defaultLocale;
  const barePath = stripLocale(pathname);

  const rawCookie = request.cookies.get('auth-session')?.value ?? null;
  const session = rawCookie ? parseAuthSession(rawCookie) : null;
  const role = session?.role ?? null;

  // ── Protected path: require valid session ────────────────────────────────
  if (isProtectedPath(pathname)) {
    if (!session) {
      const loginUrl = new URL(`/${locale}${ROUTES.LOGIN}`, request.url);
      loginUrl.searchParams.set('redirect', pathname);
      return withSecurityHeaders(NextResponse.redirect(loginUrl), csp);
    }
  }

  // ── Role-based redirect (protected paths + login bounce) ─────────────────
  const redirect = getRedirectForRole(barePath, role);
  if (redirect) {
    return withSecurityHeaders(
      NextResponse.redirect(new URL(`/${locale}${redirect}`, request.url)),
      csp,
    );
  }

  return withSecurityHeaders(intlMiddleware(request), csp);
}

export const config = {
  matcher: [
    // Match all routes except Next.js internals and static files
    '/((?!_next|[^?]*\\.(?:html?|css|js(?!on)|jpe?g|webp|png|gif|svg|ttf|woff2?|ico|csv|docx?|xlsx?|zip|webmanifest)).*)',
    '/(api|trpc)(.*)',
  ],
};
