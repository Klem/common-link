'use client';

import { useState } from 'react';
import { updateLandingConfig } from '@/lib/api/association';
import { useToastStore } from '@/stores/toastStore';
import type { UpdateLandingConfigRequest } from '@/types/association';

/**
 * Sends one landing-config field per interaction and refreshes the profile — no Save button to
 * understand. The backend leaves every field it did not receive untouched.
 */
export function useLandingConfigPatch(onProfileChanged: () => Promise<void>) {
  const { addToast } = useToastStore();
  const [isSaving, setIsSaving] = useState(false);

  const patchConfig = async (data: UpdateLandingConfigRequest) => {
    setIsSaving(true);
    try {
      await updateLandingConfig(data);
      await onProfileChanged();
      addToast('success', 'landingConfigSaved');
    } catch {
      addToast('error', 'errors.serverError');
    } finally {
      setIsSaving(false);
    }
  };

  return { isSaving, setIsSaving, patchConfig };
}
