'use client';

import { useState, useEffect } from 'react';
import { getAssociationProfile, updateAssociationProfile } from '@/lib/api/association';
import { useToastStore } from '@/stores/toastStore';
import type { AssociationProfileDto, UpdateAssociationProfileRequest } from '@/types/association';

/** Return type of {@link useAssociationProfile}. */
interface UseAssociationProfileReturn {
  /** The loaded profile, or null while loading or on error. */
  profile: AssociationProfileDto | null;
  /** True while the initial `GET /api/association/me` fetch is in-flight. */
  isLoading: boolean;
  /** i18n error key set when the initial fetch fails, or null. */
  error: string | null;
  /** True for one render cycle after a successful `updateProfile` call. */
  isSuccess: boolean;
  /**
   * Sends a `PATCH /api/association/me` request and updates local state on success.
   * Enqueues a success or error toast via `useToastStore`.
   */
  updateProfile: (data: UpdateAssociationProfileRequest) => Promise<void>;
}

/**
 * Hook that loads and manages the authenticated association's profile.
 *
 * Fetches the profile once on mount. The `cancelled` flag inside the effect
 * prevents state updates if the component unmounts before the fetch resolves
 * (avoids React "update on unmounted component" warnings).
 *
 * @returns Profile data, loading/error/success state, and an `updateProfile` action.
 */
export function useAssociationProfile(): UseAssociationProfileReturn {
  const [profile, setProfile] = useState<AssociationProfileDto | null>(null);
  const [isLoading, setIsLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [isSuccess, setIsSuccess] = useState(false);
  const { addToast } = useToastStore();

  useEffect(() => {
    let cancelled = false;
    getAssociationProfile()
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

  const updateProfile = async (data: UpdateAssociationProfileRequest): Promise<void> => {
    setIsSuccess(false);
    try {
      const updated = await updateAssociationProfile(data);
      setProfile(updated);
      setIsSuccess(true);
      addToast('success', 'profileUpdated');
    } catch {
      addToast('error', 'common.errors.serverError');
    }
  };

  return { profile, isLoading, error, isSuccess, updateProfile };
}
