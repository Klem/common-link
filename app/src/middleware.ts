import createMiddleware from 'next-intl/middleware';
import { NextRequest, NextResponse } from 'next/server';
import { routing } from './i18n/routing';

const intlMiddleware = createMiddleware(routing);

const PROTECTED_PATHS = ['/dashboard', '/settings'];

function isProtectedPath(pathname: string): boolean {
  // Strip the locale prefix (e.g. /fr/dashboard → /dashboard)
  const withoutLocale = pathname.replace(/^\/[a-z]{2}(\/|$)/, '/');
  return PROTECTED_PATHS.some(
    (p) => withoutLocale === p || withoutLocale.startsWith(`${p}/`),
  );
}

export function middleware(request: NextRequest) {
  const { pathname } = request.nextUrl;

  if (isProtectedPath(pathname)) {
    const authSession = request.cookies.get('auth-session');
    if (!authSession?.value) {
      const locale = pathname.match(/^\/([a-z]{2})\//)?.[1] ?? routing.defaultLocale;
      const loginUrl = new URL(`/${locale}/login`, request.url);
      loginUrl.searchParams.set('redirect', pathname);
      return NextResponse.redirect(loginUrl);
    }
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
