'use client';

import { useCallback, useEffect, useRef, useState } from 'react';
import { getSignatory, getSignatoryImage, saveSignatory } from '@/lib/api/signatory';
import { useToastStore } from '@/stores/toastStore';
import type { SaveSignatoryRequest, SignatoryDto } from '@/types/signatory';

/** Return type of {@link useSignatory}. */
export interface UseSignatoryReturn {
  /** Saved signer state, or null while loading or when the read failed. */
  signatory: SignatoryDto | null;
  /** True while the initial read is in flight. */
  isLoading: boolean;
  /** Saved signature image, reused when the signer is re-saved in the same mode without a new image. */
  savedImage: Blob | null;
  /** Object URL of {@link savedImage} for previews; revoked automatically. */
  savedImageUrl: string | null;
  /**
   * Saves the signer and their signature.
   *
   * @return true on success; false when the API refused the request (the caller shows the error).
   */
  save: (request: SaveSignatoryRequest) => Promise<boolean>;
}

/**
 * Loads and saves the authorised signer of the fiscal receipts (`/api/association/me/signatory`).
 * Every saved signature is an image (UPLOAD or DRAWN); it is fetched once a signature is saved.
 */
export function useSignatory(): UseSignatoryReturn {
  const [signatory, setSignatory] = useState<SignatoryDto | null>(null);
  const [isLoading, setIsLoading] = useState(true);
  const [savedImage, setSavedImage] = useState<Blob | null>(null);
  const [savedImageUrl, setSavedImageUrl] = useState<string | null>(null);
  const { addToast } = useToastStore();
  const mounted = useRef(true);

  useEffect(() => {
    mounted.current = true;
    return () => {
      mounted.current = false;
    };
  }, []);

  useEffect(() => {
    if (!savedImage) {
      setSavedImageUrl(null);
      return;
    }
    const url = URL.createObjectURL(savedImage);
    setSavedImageUrl(url);
    return () => URL.revokeObjectURL(url);
  }, [savedImage]);

  const load = useCallback(async () => {
    try {
      const data = await getSignatory();
      const image = data.mode != null ? await getSignatoryImage().catch(() => null) : null;
      if (!mounted.current) return;
      setSignatory(data);
      setSavedImage(image);
    } catch {
      if (mounted.current) addToast('error', 'errors.serverError');
    } finally {
      if (mounted.current) setIsLoading(false);
    }
  }, [addToast]);

  useEffect(() => {
    load();
  }, [load]);

  const save = async (request: SaveSignatoryRequest): Promise<boolean> => {
    try {
      const updated = await saveSignatory(request);
      if (mounted.current) {
        setSignatory(updated);
        setSavedImage(request.file);
      }
      addToast('success', 'signatorySaved');
      return true;
    } catch {
      return false;
    }
  };

  return { signatory, isLoading, savedImage, savedImageUrl, save };
}
