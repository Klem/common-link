import createMiddleware from 'next-intl/middleware';
import { NextRequest, NextResponse } from 'next/server';
import { routing } from './i18n/routing';
import { ROUTES } from './lib/routes';
import { getRedirectForRole } from './lib/routeGuard';

const intlMiddleware = createMiddleware(routing);

const PROTECTED_PATHS = ['/dashboard', '/settings'];

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
      return NextResponse.redirect(loginUrl);
    }
  }

  // ── Role-based redirect (protected paths + login bounce) ─────────────────
  const redirect = getRedirectForRole(barePath, role);
  if (redirect) {
    return NextResponse.redirect(new URL(`/${locale}${redirect}`, request.url));
  }

  return intlMiddleware(request);
}

export const config = {
  matcher: [
    // Match all routes except Next.js internals and static files
    '/((?!_next|[^?]*\\.(?:html?|css|js(?!on)|jpe?g|webp|png|gif|svg|ttf|woff2?|ico|csv|docx?|xlsx?|zip|webmanifest)).*)',
    '/(api|trpc)(.*)',
  ],
};
