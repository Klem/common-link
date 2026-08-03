'use client';

import { usePathname } from 'next/navigation';
import { GoogleOAuthProvider } from '@react-oauth/google';

const clientId = process.env.NEXT_PUBLIC_GOOGLE_CLIENT_ID ?? '';

/**
 * Mounts GoogleOAuthProvider (which injects accounts.google.com/gsi/client) only
 * on routes that need it. Landing pages (/lp/*) must be CDN-free for Ad Grants compliance.
 */
export function ConditionalGoogleOAuth({ children }: { children: React.ReactNode }) {
  const pathname = usePathname();

  if (/\/lp\//.test(pathname)) {
    return <>{children}</>;
  }

  return <GoogleOAuthProvider clientId={clientId}>{children}</GoogleOAuthProvider>;
}
