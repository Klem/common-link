'use client';

import { useState, useEffect } from 'react';
import { getDonorProfile, updateDonorProfile } from '@/lib/api/donor';
import { useToastStore } from '@/stores/toastStore';
import type { DonorProfileDto, UpdateDonorProfileRequest } from '@/types/donor';

interface UseDonorProfileReturn {
  profile: DonorProfileDto | null;
  isLoading: boolean;
  error: string | null;
  isSuccess: boolean;
  updateProfile: (data: UpdateDonorProfileRequest) => Promise<void>;
}

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
