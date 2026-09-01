'use client';

import { useState } from 'react';
import api from '@/lib/api';

/** Lifecycle states of a "forgot password" link request. */
type ForgotPasswordStatus = 'idle' | 'sending' | 'sent' | 'error';

/**
 * Hook that requests a password-reset link via `POST /api/auth/forgot-password`.
 *
 * The backend always responds 204 whether or not the email has an account (anti-enumeration),
 * so this always resolves to `sent` — there is no error state to distinguish "unknown email" from
 * "link sent", by design. The link is verified on arrival by `useMagicLinkVerify`, same as a login
 * magic link; the difference is server-side (`AuthResponseDto.passwordResetPending`).
 *
 * @returns `sendLink` function and `status`.
 */
export function useForgotPassword() {
  const [status, setStatus] = useState<ForgotPasswordStatus>('idle');

  const sendLink = async (email: string): Promise<void> => {
    setStatus('sending');
    try {
      await api.post('/api/auth/forgot-password', { email });
    } catch {
      // Anti-enumeration: a transient failure here (network, 5xx) must not read differently from
      // "link sent" — the global toast interceptor already surfaces 429/500+ separately.
    } finally {
      setStatus('sent');
    }
  };

  const reset = () => setStatus('idle');

  return { status, sendLink, reset };
}
