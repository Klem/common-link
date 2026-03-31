'use client';

import { useState } from 'react';
import { isAxiosError } from 'axios';
import api from '@/lib/api';

/** User role discriminator — determines which dashboard and profile are created. */
type UserRole = 'ASSOCIATION' | 'DONOR';

/**
 * Association registration data included in the signup payload when the user
 * selects an existing association from the search step.
 */
interface AssociationData {
  name: string;
  /** SIREN registration identifier. */
  identifier: string;
  city?: string;
  postalCode?: string;
}

/**
 * RFC 7807 Problem Detail shape returned by the backend on registration errors.
 */
interface ProblemDetail {
  code?: string;
  [key: string]: unknown;
}

/**
 * Hook that handles email + password account registration via `POST /api/auth/register`.
 *
 * After a successful call, the pending email is saved to `sessionStorage` under
 * `cl-pending-email` so the "check your inbox" screen can display it, and the
 * `sent` flag is set to `true` to trigger the confirmation UI.
 *
 * On 409 / `EMAIL_ALREADY_EXISTS`, maps to the `errors.accountExists` i18n key.
 * Errors are re-thrown after being set so callers can react if needed.
 *
 * @returns `register` function, `loading` / `error` / `sent` state, and a `reset`
 *          function to clear the sent state (e.g. for a "resend" action).
 */
export function useEmailRegister() {
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [sent, setSent] = useState(false);

  const register = async (email: string, password: string, role: UserRole, associationData?: AssociationData): Promise<void> => {
    setLoading(true);
    setError(null);
    try {
      await api.post('/api/auth/register', {
        email,
        password,
        role,
        ...(associationData && { associationProfile: associationData }),
      });
      sessionStorage.setItem('cl-pending-email', email);
      setSent(true);
    } catch (err) {
      if (isAxiosError(err)) {
        const problemDetail = err.response?.data as ProblemDetail | undefined;
        if (err.response?.status === 409 || problemDetail?.code === 'EMAIL_ALREADY_EXISTS') {
          setError('errors.accountExists');
        } else {
          setError('errors.genericError');
        }
      } else {
        setError('errors.genericError');
      }
      throw err;
    } finally {
      setLoading(false);
    }
  };

  const reset = () => {
    setSent(false);
    setError(null);
  };

  return { register, loading, error, sent, reset };
}
