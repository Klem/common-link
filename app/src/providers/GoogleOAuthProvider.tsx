'use client';

import { GoogleOAuthProvider as BaseGoogleOAuthProvider } from '@react-oauth/google';

const clientId = process.env.NEXT_PUBLIC_GOOGLE_CLIENT_ID ?? '';

export function GoogleOAuthProvider({ children }: { children: React.ReactNode }) {
  return (
    <BaseGoogleOAuthProvider clientId={clientId}>
      {children}
    </BaseGoogleOAuthProvider>
  );
}
