'use client';

import { useState, useCallback } from 'react';
import { verifyIbanVop } from '@/lib/api/beneficiary';
import { useToastStore } from '@/stores/toastStore';

/** Return type of {@link useVopVerify}. */
export interface UseVopVerifyReturn {
  /** True while a VOP verification call is in-flight. */
  isVerifying: boolean;
  /** UUID of the IBAN currently being verified, or null. */
  verifyingIbanId: string | null;
  /**
   * Triggers VOP verification for a beneficiary IBAN.
   * Shows a toast on completion and calls `onComplete` callback.
   * @param beneficiaryId - UUID of the beneficiary.
   * @param ibanId - UUID of the IBAN to verify.
   * @param onComplete - Called after successful verification to refresh state.
   */
  verify: (beneficiaryId: string, ibanId: string, onComplete: () => Promise<void>) => Promise<void>;
}

/**
 * Hook managing the Verification of Payee (VOP) flow for a single IBAN.
 *
 * Tracks which IBAN is being verified, fires a toast based on the result,
 * and delegates state refresh to the caller via the `onComplete` callback.
 */
export function useVopVerify(): UseVopVerifyReturn {
  const [isVerifying, setIsVerifying] = useState(false);
  const [verifyingIbanId, setVerifyingIbanId] = useState<string | null>(null);
  const { addToast } = useToastStore();

  const verify = useCallback(
    async (
      beneficiaryId: string,
      ibanId: string,
      onComplete: () => Promise<void>,
    ): Promise<void> => {
      setIsVerifying(true);
      setVerifyingIbanId(ibanId);
      try {
        const result = await verifyIbanVop(beneficiaryId, ibanId);
        const toastType =
          result.vopResult === 'MATCH'
            ? 'success'
            : result.vopResult === 'CLOSE_MATCH'
              ? 'warning'
              : 'error';
        addToast(toastType, 'vopVerified');
        await onComplete();
      } catch {
        addToast('error', 'errors.serverError');
      } finally {
        setIsVerifying(false);
        setVerifyingIbanId(null);
      }
    },
    [addToast],
  );

  return { isVerifying, verifyingIbanId, verify };
}
