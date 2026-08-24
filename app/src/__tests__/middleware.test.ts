import { describe, it, expect } from 'vitest';
import { NextRequest } from 'next/server';
import { middleware } from '../middleware';

function requestFor(path: string) {
  return new NextRequest(`http://localhost:3000${path}`);
}

describe('middleware — API bypass', () => {
  it('never redirects /api/* to a locale-prefixed path', async () => {
    // Regression: falling through to intlMiddleware here redirected /api/gtm-export/x to
    // /fr/api/gtm-export/x — a path no route handler matches (they live outside [locale]),
    // a silent 404 for every API call.
    const res = middleware(requestFor('/api/gtm-export/clk_abc?gtmId=GTM-ABC1234'));

    expect(res.status).not.toBe(307);
    expect(res.status).not.toBe(308);
    expect(res.headers.get('location')).toBeNull();
  });

  it('still redirects unauthenticated dashboard access to login', () => {
    // Sanity check: the /api bypass must not swallow the existing auth-guard behaviour.
    const res = middleware(requestFor('/fr/dashboard/association'));

    expect([307, 308]).toContain(res.status);
    expect(res.headers.get('location')).toContain('/login');
  });
});
