'use client';

import { useState, useEffect } from 'react';
import { getDonorProfile, updateDonorProfile } from '@/lib/api/donor';
import { useToastStore } from '@/stores/toastStore';
import type { DonorProfileDto, UpdateDonorProfileRequest } from '@/types/donor';

/** Return type of {@link useDonorProfile}. */
interface UseDonorProfileReturn {
  /** The loaded profile, or null while loading or on error. */
  profile: DonorProfileDto | null;
  /** True while the initial `GET /api/donor/me` fetch is in-flight. */
  isLoading: boolean;
  /** i18n error key set when the initial fetch fails, or null. */
  error: string | null;
  /** True for one render cycle after a successful `updateProfile` call. */
  isSuccess: boolean;
  /**
   * Sends a `PATCH /api/donor/me` request and updates local state on success.
   * Enqueues a success or error toast via `useToastStore`.
   */
  updateProfile: (data: UpdateDonorProfileRequest) => Promise<void>;
}

/**
 * Hook that loads and manages the authenticated donor's profile.
 *
 * Fetches the profile once on mount. The `cancelled` flag inside the effect
 * prevents state updates if the component unmounts before the fetch resolves
 * (avoids React "update on unmounted component" warnings).
 *
 * @returns Profile data, loading/error/success state, and an `updateProfile` action.
 */
export function useDonorProfile(): UseDonorProfileReturn {
  const [profile, setProfile] = useState<DonorProfileDto | null>(null);
  const [isLoading, setIsLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [isSuccess, setIsSuccess] = useState(false);
  const { addToast } = useToastStore();

  useEffect(() => {
    let cancelled = false;
    getDonorProfile()
      .then((data) => {
        if (!cancelled) setProfile(data);
      })
      .catch(() => {
        if (!cancelled) setError('common.errors.serverError');
      })
      .finally(() => {
        if (!cancelled) setIsLoading(false);
      });
    return () => {
      cancelled = true;
    };
  }, []);

  const updateProfile = async (data: UpdateDonorProfileRequest): Promise<void> => {
    setIsSuccess(false);
    try {
      const updated = await updateDonorProfile(data);
      setProfile(updated);
      setIsSuccess(true);
      addToast('success', 'profileUpdated');
    } catch {
      addToast('error', 'common.errors.serverError');
    }
  };

  return { profile, isLoading, error, isSuccess, updateProfile };
}
