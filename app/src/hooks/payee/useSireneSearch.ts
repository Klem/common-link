'use client';

import { useState, useMemo } from 'react';
import { searchSirene } from '@/lib/api/payee';
import type { SireneSearchResultDto } from '@/types/payee';

/**
 * Detects whether a numeric string is a SIREN (9 digits) or SIRET (14 digits).
 * @param value - The raw numeric string.
 * @returns `'siren'`, `'siret'`, or `null` if neither.
 */
function detectType(value: string): 'siren' | 'siret' | null {
  if (/^\d{9}$/.test(value)) return 'siren';
  if (/^\d{14}$/.test(value)) return 'siret';
  return null;
}

/** Return type of {@link useSireneSearch}. */
export interface UseSireneSearchReturn {
  /** Current input value (digits only). */
  query: string;
  /** Updates the search query. */
  setQuery: (value: string) => void;
  /** Last successful search result, or null. */
  result: SireneSearchResultDto | null;
  /** True while the API call is in-flight. */
  isLoading: boolean;
  /** i18n error key for the last error, or null. */
  error: string | null;
  /** Detected identifier type based on query length. */
  detectedType: 'siren' | 'siret' | null;
  /** Triggers the INSEE Sirene search using the current query. */
  search: () => Promise<void>;
  /** Resets result and error state. */
  clear: () => void;
}

/**
 * Hook managing the SIREN/SIRET search flow against the INSEE Sirene proxy.
 *
 * Provides digit-only query management, automatic type detection,
 * error mapping to i18n keys, and a `clear` reset function.
 */
export function useSireneSearch(): UseSireneSearchReturn {
  const [query, setQuery] = useState('');
  const [result, setResult] = useState<SireneSearchResultDto | null>(null);
  const [isLoading, setIsLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const detectedType = useMemo(() => detectType(query), [query]);

  /**
   * Calls the backend Sirene proxy with the current query.
   * Maps HTTP error status codes to i18n keys under `dashboard.payees.search.errors`.
   */
  const search = async (): Promise<void> => {
    if (!detectedType) return;
    setIsLoading(true);
    setError(null);
    setResult(null);
    try {
      const data = await searchSirene(query);
      setResult(data);
    } catch (err: unknown) {
      const status = (err as { response?: { status?: number } }).response?.status;
      const key = status === 400
        ? 'payees.search.errors.400'
        : status === 404
          ? 'payees.search.errors.404'
          : status === 429
            ? 'payees.search.errors.429'
            : status === 502
              ? 'payees.search.errors.502'
              : 'payees.search.errors.network';
      setError(key);
    } finally {
      setIsLoading(false);
    }
  };

  /** Resets search result and error without changing the query. */
  const clear = (): void => {
    setResult(null);
    setError(null);
  };

  return { query, setQuery, result, isLoading, error, detectedType, search, clear };
}
