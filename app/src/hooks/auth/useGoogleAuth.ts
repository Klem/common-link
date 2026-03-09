'use client';

import { useState } from 'react';
import { useRouter } from 'next/navigation';
import { useLocale } from 'next-intl';
import { isAxiosError } from 'axios';
import api from '@/lib/api';
import { useAuthStore } from '@/stores/authStore';
import type { AuthResponseDto } from '@/types/auth';

type UserRole = 'ASSOCIATION' | 'DONOR';

interface GoogleAuthState {
  loading: boolean;
  error: string | null;
}

export function useGoogleAuth() {
  const router = useRouter();
  const locale = useLocale();
  const { setAuth } = useAuthStore();
  const [state, setState] = useState<GoogleAuthState>({ loading: false, error: null });

  const login = async (idToken: string): Promise<void> => {
    setState({ loading: true, error: null });
    try {
      const { data } = await api.post<AuthResponseDto>('/api/auth/login/google', { idToken });
      setAuth(data.accessToken, data.refreshToken, data.user);
      router.push(`/${locale}/dashboard/${data.user.role.toLowerCase()}`);
    } catch (err) {
      if (isAxiosError(err) && err.response?.status === 401) {
        setState({ loading: false, error: 'auth.errors.noAccount' });
      } else {
        setState({ loading: false, error: 'auth.errors.genericError' });
      }
      throw err;
    }
  };

  const signUp = async (idToken: string, role: UserRole): Promise<void> => {
    setState({ loading: true, error: null });
    try {
      const { data } = await api.post<AuthResponseDto>('/api/auth/signup/google', { idToken, role });
      setAuth(data.accessToken, data.refreshToken, data.user);
      // Caller is responsible for next navigation (show setPassword or redirect)
    } catch (err) {
      if (isAxiosError(err) && err.response?.status === 409) {
        setState({ loading: false, error: 'auth.errors.accountExists' });
      } else {
        setState({ loading: false, error: 'auth.errors.genericError' });
      }
      throw err;
    }
    setState({ loading: false, error: null });
  };

  return { login, signUp, ...state };
}
