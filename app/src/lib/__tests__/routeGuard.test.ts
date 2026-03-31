import { describe, it, expect } from 'vitest';
import { getRedirectForRole } from '../routeGuard';
import { ROUTES } from '../routes';

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
    expect(getRedirectForRole(ROUTES.ASSOCIATION_DASHBOARD, 'DONOR')).toBe(ROUTES.DONOR_DASHBOARD);
  });

  it('redirects ASSOCIATION on /dashboard/donor → /dashboard/association', () => {
    expect(getRedirectForRole(ROUTES.DONOR_DASHBOARD, 'ASSOCIATION')).toBe(
      ROUTES.ASSOCIATION_DASHBOARD,
    );
  });

  it('redirects DONOR on a nested association path → /dashboard/donor', () => {
    expect(getRedirectForRole(ROUTES.ASSOCIATION_PROFILE, 'DONOR')).toBe(ROUTES.DONOR_DASHBOARD);
  });

  // ── Correct-role access (no redirect) ────────────────────────────────────

  it('returns null for DONOR on /dashboard/donor', () => {
    expect(getRedirectForRole(ROUTES.DONOR_DASHBOARD, 'DONOR')).toBeNull();
  });

  it('returns null for ASSOCIATION on /dashboard/association', () => {
    expect(getRedirectForRole(ROUTES.ASSOCIATION_DASHBOARD, 'ASSOCIATION')).toBeNull();
  });

  it('returns null for DONOR on a nested donor path', () => {
    expect(getRedirectForRole(ROUTES.DONOR_PROFILE, 'DONOR')).toBeNull();
  });

  // ── Authenticated user on /login (bounce to dashboard) ───────────────────

  it('redirects authenticated DONOR on /login → /dashboard/donor', () => {
    expect(getRedirectForRole(ROUTES.LOGIN, 'DONOR')).toBe('/dashboard/donor');
  });

  it('redirects authenticated ASSOCIATION on /login → /dashboard/association', () => {
    expect(getRedirectForRole(ROUTES.LOGIN, 'ASSOCIATION')).toBe('/dashboard/association');
  });
});
