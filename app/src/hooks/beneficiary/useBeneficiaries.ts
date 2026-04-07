'use client';

import { useState, useEffect, useCallback } from 'react';
import {
  getBeneficiaries,
  addIban,
  deleteIban,
  deleteBeneficiary,
} from '@/lib/api/beneficiary';
import type { BeneficiaryDto } from '@/types/beneficiary';

/** Return type of {@link useBeneficiaries}. */
export interface UseBeneficiariesReturn {
  /** Current list of beneficiaries for the association. */
  beneficiaries: BeneficiaryDto[];
  /** True while any API request is in-flight. */
  isLoading: boolean;
  /** Error message key, or null. */
  error: string | null;
  /** Re-fetches the full beneficiary list from the API. */
  fetchBeneficiaries: () => Promise<void>;
  /**
   * Adds an IBAN to a beneficiary and refreshes the list.
   * @param beneficiaryId - UUID of the beneficiary.
   * @param iban - IBAN string to add.
   */
  addBeneficiaryIban: (beneficiaryId: string, iban: string) => Promise<void>;
  /**
   * Removes an IBAN from a beneficiary and refreshes the list.
   * @param beneficiaryId - UUID of the beneficiary.
   * @param ibanId - UUID of the IBAN to remove.
   */
  removeBeneficiaryIban: (beneficiaryId: string, ibanId: string) => Promise<void>;
  /**
   * Deletes a beneficiary by ID and refreshes the list.
   * @param id - UUID of the beneficiary.
   */
  removeBeneficiary: (id: string) => Promise<void>;
  /**
   * Re-fetches the full list (simpler than a partial update).
   * @param id - UUID of the beneficiary to refresh (triggers a full list reload).
   */
  refreshBeneficiary: (id: string) => Promise<void>;
}

/**
 * Hook managing beneficiary CRUD operations for an association.
 *
 * Fetches the beneficiary list on mount and exposes actions
 * that mutate the remote state and keep the local list in sync.
 */
export function useBeneficiaries(): UseBeneficiariesReturn {
  const [beneficiaries, setBeneficiaries] = useState<BeneficiaryDto[]>([]);
  const [isLoading, setIsLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  /** Fetches the full list from the API. */
  const fetchBeneficiaries = useCallback(async (): Promise<void> => {
    setIsLoading(true);
    setError(null);
    try {
      const data = await getBeneficiaries();
      setBeneficiaries(data);
    } catch {
      setError('common.errors.serverError');
    } finally {
      setIsLoading(false);
    }
  }, []);

  /** Adds an IBAN and refreshes the list. */
  const addBeneficiaryIban = useCallback(
    async (beneficiaryId: string, iban: string): Promise<void> => {
      await addIban(beneficiaryId, { iban });
      await fetchBeneficiaries();
    },
    [fetchBeneficiaries],
  );

  /** Removes an IBAN and refreshes the list. */
  const removeBeneficiaryIban = useCallback(
    async (beneficiaryId: string, ibanId: string): Promise<void> => {
      await deleteIban(beneficiaryId, ibanId);
      await fetchBeneficiaries();
    },
    [fetchBeneficiaries],
  );

  /** Deletes a beneficiary and refreshes the list. */
  const removeBeneficiary = useCallback(
    async (id: string): Promise<void> => {
      await deleteBeneficiary(id);
      await fetchBeneficiaries();
    },
    [fetchBeneficiaries],
  );

  /** Re-fetches the full list (full reload, no partial update). */
  const refreshBeneficiary = useCallback(
    async (_id: string): Promise<void> => {
      await fetchBeneficiaries();
    },
    [fetchBeneficiaries],
  );

  useEffect(() => {
    fetchBeneficiaries();
  }, [fetchBeneficiaries]);

  return {
    beneficiaries,
    isLoading,
    error,
    fetchBeneficiaries,
    addBeneficiaryIban,
    removeBeneficiaryIban,
    removeBeneficiary,
    refreshBeneficiary,
  };
}
