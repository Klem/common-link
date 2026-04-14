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
  ASSOCIATION_BENEFICIARIES: '/dashboard/association/beneficiaries',
  ASSOCIATION_CAMPAIGNS: '/dashboard/association/campaigns',
} as const;
