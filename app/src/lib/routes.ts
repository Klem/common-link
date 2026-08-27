/**
 * Centralised route constants for the application.
 * Use these instead of hard-coded strings so path changes only require a
 * single update here.
 *
 * Note: these are locale-agnostic path segments. The Next.js `[locale]`
 * segment is prepended automatically by the middleware and navigation helpers.
 */
export const ROUTES = {
  LOGIN: '/login',
  CHECK_EMAIL: '/auth/check-email',
  VERIFY_EMAIL: '/auth/verify-email',
  DONOR_DASHBOARD: '/dashboard/donor',
  DONOR_PROFILE: '/dashboard/donor/profile',
  ASSOCIATION_DASHBOARD: '/dashboard/association',
  ASSOCIATION_PROFILE: '/dashboard/association/profile',
  ASSOCIATION_PAYEES: '/dashboard/association/payees',
  ASSOCIATION_CAMPAIGNS: '/dashboard/association/campaigns',
  ASSOCIATION_REPORTING: '/dashboard/association/reporting',
  admin: {
    root: '/admin',
    verifications: '/admin/verifications',
    verificationDetail: (id: string) => `/admin/verifications/${id}`,
  },
  compliance: {
    root: '/compliance',
    alerts: '/compliance/alerts',
    alertDetail: (id: string) => `/compliance/alerts/${id}`,
    associations: '/compliance/associations',
    associationDetail: (id: string) => `/compliance/associations/${id}`,
    campaignDetail: (associationId: string, campaignId: string) =>
      `/compliance/associations/${associationId}/campaigns/${campaignId}`,
  },
} as const;

/**
 * Returns the locale-prefixed home path for a given role.
 * Centralises role → landing page logic so all auth hooks stay in sync.
 */
export function getHomePath(locale: string, role: string): string {
  if (role === 'CURATOR') return `/${locale}${ROUTES.admin.root}`;
  if (role === 'COMPLIANCE_OFFICER') return `/${locale}${ROUTES.compliance.root}`;
  return `/${locale}/dashboard/${role.toLowerCase()}`;
}
