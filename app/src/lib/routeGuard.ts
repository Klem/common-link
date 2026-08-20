import { ROUTES } from './routes';
import { UserRole } from '@/types/auth';

/**
 * Pure function that determines whether a redirect is needed based on the
 * current path and the authenticated user's role.
 *
 * Rules:
 * - Unauthenticated users (`role === null`) on protected paths are sent to login.
 * - COMPLIANCE_OFFICER users on non-compliance paths are redirected to `/compliance`.
 * - CURATOR users on user-facing paths (dashboard, settings, login, compliance) are redirected to `/admin`.
 * - Non-COMPLIANCE_OFFICER users on `/compliance/**` are redirected to their home dashboard.
 * - Non-CURATOR users on `/admin/**` are redirected to their home dashboard.
 * - Authenticated users accessing a dashboard for the wrong role are cross-redirected.
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
  const isAdminPath = path === ROUTES.admin.root || path.startsWith(`${ROUTES.admin.root}/`);
  const isCompliancePath = path === ROUTES.compliance.root || path.startsWith(`${ROUTES.compliance.root}/`);
  const isSettingsPath = path === '/settings' || path.startsWith('/settings/');
  const isLoginPath = path === ROUTES.LOGIN;

  // Unauthenticated user on a protected path → send to login
  if (role === null) {
    if (isDonorPath || isAssociationPath || isAdminPath || isCompliancePath) {
      return ROUTES.LOGIN;
    }
    return null;
  }

  // COMPLIANCE_OFFICER: redirect away from all non-compliance areas to /compliance
  if (role === UserRole.COMPLIANCE_OFFICER) {
    if (isDonorPath || isAssociationPath || isAdminPath || isSettingsPath || isLoginPath) {
      return ROUTES.compliance.root;
    }
    return null; // COMPLIANCE_OFFICER on /compliance/** or any other path → no redirect
  }

  // CURATOR: redirect away from user-facing areas and compliance to the admin console
  if (role === UserRole.CURATOR) {
    if (isDonorPath || isAssociationPath || isSettingsPath || isLoginPath || isCompliancePath) {
      return ROUTES.admin.root;
    }
    return null; // CURATOR on /admin/** or any other path → no redirect
  }

  // Regular roles on admin or compliance path → redirect to their own dashboard
  if (isAdminPath || isCompliancePath) {
    return role === UserRole.DONOR ? ROUTES.DONOR_DASHBOARD : ROUTES.ASSOCIATION_DASHBOARD;
  }

  // Wrong-role access — cross-redirect
  if (isDonorPath && role !== UserRole.DONOR) {
    return ROUTES.ASSOCIATION_DASHBOARD;
  }
  if (isAssociationPath && role !== UserRole.ASSOCIATION) {
    return ROUTES.DONOR_DASHBOARD;
  }

  // Already authenticated user landing on login → bounce to their dashboard
  if (isLoginPath) {
    return `/dashboard/${role.toLowerCase()}`;
  }

  return null;
}
