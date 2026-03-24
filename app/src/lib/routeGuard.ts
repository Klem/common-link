import { ROUTES } from './routes';

/**
 * Pure function that determines whether a redirect is needed based on the
 * current path and the authenticated user's role.
 *
 * @returns The redirect path, or null if no redirect is needed.
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
