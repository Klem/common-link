import { describe, it, expect } from 'vitest';
import { getRedirectForRole } from '../routeGuard';
import { ROUTES } from '../routes';
import { UserRole } from '@/types/auth';

describe('getRedirectForRole', () => {
  // ── No cookie (unauthenticated) ──────────────────────────────────────────

  it('returns /login for /dashboard/donor when role is null', () => {
    expect(getRedirectForRole(ROUTES.DONOR_DASHBOARD, null)).toBe(ROUTES.LOGIN);
  });

  it('returns /login for /dashboard/association when role is null', () => {
    expect(getRedirectForRole(ROUTES.ASSOCIATION_DASHBOARD, null)).toBe(ROUTES.LOGIN);
  });

  it('returns /login for a nested donor path when role is null', () => {
    expect(getRedirectForRole(ROUTES.DONOR_PROFILE, null)).toBe(ROUTES.LOGIN);
  });

  it('returns null for /login when role is null (let login page render)', () => {
    expect(getRedirectForRole(ROUTES.LOGIN, null)).toBeNull();
  });

  // ── Cross-role redirects ─────────────────────────────────────────────────

  it('redirects DONOR on /dashboard/association → /dashboard/donor', () => {
    expect(getRedirectForRole(ROUTES.ASSOCIATION_DASHBOARD, UserRole.DONOR)).toBe(ROUTES.DONOR_DASHBOARD);
  });

  it('redirects ASSOCIATION on /dashboard/donor → /dashboard/association', () => {
    expect(getRedirectForRole(ROUTES.DONOR_DASHBOARD, UserRole.ASSOCIATION)).toBe(
      ROUTES.ASSOCIATION_DASHBOARD,
    );
  });

  it('redirects DONOR on a nested association path → /dashboard/donor', () => {
    expect(getRedirectForRole(ROUTES.ASSOCIATION_PROFILE, UserRole.DONOR)).toBe(ROUTES.DONOR_DASHBOARD);
  });

  // ── Correct-role access (no redirect) ────────────────────────────────────

  it('returns null for DONOR on /dashboard/donor', () => {
    expect(getRedirectForRole(ROUTES.DONOR_DASHBOARD, UserRole.DONOR)).toBeNull();
  });

  it('returns null for ASSOCIATION on /dashboard/association', () => {
    expect(getRedirectForRole(ROUTES.ASSOCIATION_DASHBOARD, UserRole.ASSOCIATION)).toBeNull();
  });

  it('returns null for DONOR on a nested donor path', () => {
    expect(getRedirectForRole(ROUTES.DONOR_PROFILE, UserRole.DONOR)).toBeNull();
  });

  // ── Authenticated user on /login (bounce to dashboard) ───────────────────

  it('redirects authenticated DONOR on /login → /dashboard/donor', () => {
    expect(getRedirectForRole(ROUTES.LOGIN, UserRole.DONOR)).toBe('/dashboard/donor');
  });

  it('redirects authenticated ASSOCIATION on /login → /dashboard/association', () => {
    expect(getRedirectForRole(ROUTES.LOGIN, UserRole.ASSOCIATION)).toBe('/dashboard/association');
  });

  // ── Admin path: unauthenticated ──────────────────────────────────────────

  it('returns /login for /admin when role is null', () => {
    expect(getRedirectForRole(ROUTES.admin.root, null)).toBe(ROUTES.LOGIN);
  });

  it('returns /login for a nested admin path when role is null', () => {
    expect(getRedirectForRole(ROUTES.admin.verifications, null)).toBe(ROUTES.LOGIN);
  });

  // ── Admin path: non-CURATOR blocked ──────────────────────────────────────

  it('redirects DONOR on /admin → /dashboard/donor', () => {
    expect(getRedirectForRole(ROUTES.admin.root, UserRole.DONOR)).toBe(ROUTES.DONOR_DASHBOARD);
  });

  it('redirects ASSOCIATION on /admin → /dashboard/association', () => {
    expect(getRedirectForRole(ROUTES.admin.root, UserRole.ASSOCIATION)).toBe(ROUTES.ASSOCIATION_DASHBOARD);
  });

  it('redirects DONOR on a nested admin path → /dashboard/donor', () => {
    expect(getRedirectForRole(ROUTES.admin.verifications, UserRole.DONOR)).toBe(ROUTES.DONOR_DASHBOARD);
  });

  // ── CURATOR: redirected away from user-facing areas ──────────────────────

  it('redirects CURATOR on /dashboard/donor → /admin', () => {
    expect(getRedirectForRole(ROUTES.DONOR_DASHBOARD, UserRole.CURATOR)).toBe(ROUTES.admin.root);
  });

  it('redirects CURATOR on /dashboard/association → /admin', () => {
    expect(getRedirectForRole(ROUTES.ASSOCIATION_DASHBOARD, UserRole.CURATOR)).toBe(ROUTES.admin.root);
  });

  it('redirects CURATOR on /login → /admin', () => {
    expect(getRedirectForRole(ROUTES.LOGIN, UserRole.CURATOR)).toBe(ROUTES.admin.root);
  });

  it('redirects CURATOR on /settings → /admin', () => {
    expect(getRedirectForRole('/settings', UserRole.CURATOR)).toBe(ROUTES.admin.root);
  });

  // ── CURATOR: allowed on admin paths ──────────────────────────────────────

  it('returns null for CURATOR on /admin (no redirect)', () => {
    expect(getRedirectForRole(ROUTES.admin.root, UserRole.CURATOR)).toBeNull();
  });

  it('returns null for CURATOR on /admin/verifications (no redirect)', () => {
    expect(getRedirectForRole(ROUTES.admin.verifications, UserRole.CURATOR)).toBeNull();
  });
});
