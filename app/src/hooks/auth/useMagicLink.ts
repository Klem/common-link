'use client';

import { useState } from 'react';
import { isAxiosError } from 'axios';
import api from '@/lib/api';

type MagicLinkStatus = 'idle' | 'sending' | 'sent' | 'error';
type UserRole = 'ASSOCIATION' | 'DONOR';

interface AssociationData {
  name: string;
  identifier: string;
  city?: string;
  postalCode?: string;
}

interface MagicLinkState {
  status: MagicLinkStatus;
  error: string | null;
}

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
