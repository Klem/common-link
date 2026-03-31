'use client';

import { useState, useEffect } from 'react';
import { getAssociationProfile, updateAssociationProfile } from '@/lib/api/association';
import { useToastStore } from '@/stores/toastStore';
import type { AssociationProfileDto, UpdateAssociationProfileRequest } from '@/types/association';

interface UseAssociationProfileReturn {
  profile: AssociationProfileDto | null;
  isLoading: boolean;
  error: string | null;
  isSuccess: boolean;
  updateProfile: (data: UpdateAssociationProfileRequest) => Promise<void>;
}

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
