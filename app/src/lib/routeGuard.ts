import { ROUTES } from './routes';

/**
 * Pure function that determines whether a redirect is needed based on the
 * current path and the authenticated user's role.
 *
 * Rules:
 * - Unauthenticated users (`role === null`) on protected paths are sent to login.
 * - Authenticated users accessing a dashboard for the wrong role are cross-redirected
 *   to their own dashboard (e.g. a DONOR visiting `/dashboard/association` is sent to
 *   `/dashboard/donor`).
 * - Authenticated users landing on the login page are bounced to their dashboard.
 *
 * @param path - The current pathname (locale-stripped, e.g. `/dashboard/donor`).
 * @param role - The authenticated user's role, or `null` if unauthenticated.
 * @returns The redirect path, or `null` if no redirect is needed.
 */
export function getRedirectForRole(path: string, role: string | null): string | null {
  const isDonorPath = path === ROUTES.DONOR_DASHBOARD || path.startsWith(`${ROUTES.DONOR_DASHBOARD}/`);
  const isAssociationPath =
    path === ROUTES.ASSOCIATION_DASHBOARD || path.startsWith(`${ROUTES.ASSOCIATION_DASHBOARD}/`);
  const isLoginPath = path === ROUTES.LOGIN;

  // Unauthenticated user on a protected path → send to login
  if (role === null) {
    if (isDonorPath || isAssociationPath) {
      return ROUTES.LOGIN;
    }
    return null;
  }

  // Wrong-role access — cross-redirect
  if (isDonorPath && role !== 'DONOR') {
    return ROUTES.ASSOCIATION_DASHBOARD;
  }
  if (isAssociationPath && role !== 'ASSOCIATION') {
    return ROUTES.DONOR_DASHBOARD;
  }

  // Already authenticated user landing on login → bounce to their dashboard
  if (isLoginPath) {
    return `/dashboard/${role.toLowerCase()}`;
  }

  return null;
}
