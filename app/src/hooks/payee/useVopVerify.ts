'use client';

import { useState, useCallback } from 'react';
import { verifyIbanVop } from '@/lib/api/payee';
import { VopResult } from '@/types/payee';
import { useToastStore } from '@/stores/toastStore';

/** Return type of {@link useVopVerify}. */
export interface UseVopVerifyReturn {
  /** True while a VOP verification call is in-flight. */
  isVerifying: boolean;
  /** UUID of the IBAN currently being verified, or null. */
  verifyingIbanId: string | null;
  /**
   * Triggers VOP verification for a payee IBAN.
   * Shows a toast on completion and calls `onComplete` callback.
   * @param payeeId - UUID of the payee.
   * @param ibanId - UUID of the IBAN to verify.
   * @param onComplete - Called after successful verification to refresh state.
   */
  verify: (payeeId: string, ibanId: string, onComplete: () => Promise<void>) => Promise<void>;
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
      payeeId: string,
      ibanId: string,
      onComplete: () => Promise<void>,
    ): Promise<void> => {
      setIsVerifying(true);
      setVerifyingIbanId(ibanId);
      try {
        const result = await verifyIbanVop(payeeId, ibanId);
        const toastType =
          result.vopResult === VopResult.MATCH
            ? 'success'
            : result.vopResult === VopResult.CLOSE_MATCH
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
