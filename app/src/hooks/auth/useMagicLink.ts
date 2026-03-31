'use client';

import { useState } from 'react';
import { isAxiosError } from 'axios';
import api from '@/lib/api';

/** Lifecycle states of a magic link send request. */
type MagicLinkStatus = 'idle' | 'sending' | 'sent' | 'error';

/** User role discriminator — forwarded to the backend to scope the magic link. */
type UserRole = 'ASSOCIATION' | 'DONOR';

/**
 * Association registration data included in the magic link request payload
 * when the user has selected an existing association from the search step.
 */
interface AssociationData {
  name: string;
  /** SIREN registration identifier. */
  identifier: string;
  city?: string;
  postalCode?: string;
}

/** Internal state shape for the magic link hook. */
interface MagicLinkState {
  status: MagicLinkStatus;
  /** i18n error key, or null when no error is present. */
  error: string | null;
}

/**
 * Hook that requests a magic link email via `POST /api/auth/magic-link/request`.
 *
 * The backend sends a single-use tokenised link to the user's email address.
 * The link is verified on arrival by `useMagicLinkVerify`.
 *
 * `role` and `associationData` are optional: omit them for a pure login flow
 * (existing user), provide them for a signup flow (new user).
 *
 * 429 (rate limit) is surfaced as `errors.rateLimited` to the UI.
 *
 * @returns `sendLink` function, `status`, `error` (i18n key), and `reset`.
 */
export function useMagicLink() {
  const [state, setState] = useState<MagicLinkState>({ status: 'idle', error: null });

  const sendLink = async (email: string, role?: UserRole, associationData?: AssociationData): Promise<void> => {
    setState({ status: 'sending', error: null });
    try {
      await api.post('/api/auth/magic-link/request', {
        email,
        ...(role && { role }),
        ...(associationData && { associationProfile: associationData }),
      });
      setState({ status: 'sent', error: null });
    } catch (err) {
      if (isAxiosError(err) && err.response?.status === 429) {
        setState({ status: 'error', error: 'errors.rateLimited' });
      } else {
        setState({ status: 'error', error: 'errors.genericError' });
      }
    }
  };

  const reset = () => setState({ status: 'idle', error: null });

  return { ...state, sendLink, reset };
}
