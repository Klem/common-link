'use client';

import { useState, useEffect, useCallback } from 'react';
import {
  getPayees,
  addIban,
  deleteIban,
  deletePayee,
} from '@/lib/api/payee';
import type { PayeeDto } from '@/types/payee';

/** Return type of {@link usePayees}. */
export interface UsePayeesReturn {
  /** Current list of payees for the association. */
  payees: PayeeDto[];
  /** True while any API request is in-flight. */
  isLoading: boolean;
  /** Error message key, or null. */
  error: string | null;
  /** Re-fetches the full payee list from the API. */
  fetchPayees: () => Promise<void>;
  /**
   * Adds an IBAN to a payee and refreshes the list.
   * @param payeeId - UUID of the payee.
   * @param iban - IBAN string to add.
   */
  addPayeeIban: (payeeId: string, iban: string) => Promise<void>;
  /**
   * Removes an IBAN from a payee and refreshes the list.
   * @param payeeId - UUID of the payee.
   * @param ibanId - UUID of the IBAN to remove.
   */
  removePayeeIban: (payeeId: string, ibanId: string) => Promise<void>;
  /**
   * Deletes a payee by ID and refreshes the list.
   * @param id - UUID of the payee.
   */
  removePayee: (id: string) => Promise<void>;
  /**
   * Re-fetches the full list (simpler than a partial update).
   * @param id - UUID of the payee to refresh (triggers a full list reload).
   */
  refreshPayee: (id: string) => Promise<void>;
}

/**
 * Hook managing payee CRUD operations for an association.
 *
 * Fetches the payee list on mount and exposes actions
 * that mutate the remote state and keep the local list in sync.
 */
export function usePayees(): UsePayeesReturn {
  const [payees, setPayees] = useState<PayeeDto[]>([]);
  const [isLoading, setIsLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  /** Fetches the full list from the API. */
  const fetchPayees = useCallback(async (): Promise<void> => {
    setIsLoading(true);
    setError(null);
    try {
      const data = await getPayees();
      setPayees(data);
    } catch {
      setError('common.errors.serverError');
    } finally {
      setIsLoading(false);
    }
  }, []);

  /** Adds an IBAN and refreshes the list. */
  const addPayeeIban = useCallback(
    async (payeeId: string, iban: string): Promise<void> => {
      await addIban(payeeId, { iban });
      await fetchPayees();
    },
    [fetchPayees],
  );

  /** Removes an IBAN and refreshes the list. */
  const removePayeeIban = useCallback(
    async (payeeId: string, ibanId: string): Promise<void> => {
      await deleteIban(payeeId, ibanId);
      await fetchPayees();
    },
    [fetchPayees],
  );

  /** Deletes a payee and refreshes the list. */
  const removePayee = useCallback(
    async (id: string): Promise<void> => {
      await deletePayee(id);
      await fetchPayees();
    },
    [fetchPayees],
  );

  /** Re-fetches the full list (full reload, no partial update). */
  const refreshPayee = useCallback(
    async (_id: string): Promise<void> => {
      await fetchPayees();
    },
    [fetchPayees],
  );

  useEffect(() => {
    fetchPayees();
  }, [fetchPayees]);

  return {
    payees,
    isLoading,
    error,
    fetchPayees,
    addPayeeIban,
    removePayeeIban,
    removePayee,
    refreshPayee,
  };
}
