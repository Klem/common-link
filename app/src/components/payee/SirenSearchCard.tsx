'use client';

import { useEffect, KeyboardEvent } from 'react';
import { useTranslations } from 'next-intl';
import { useSireneSearch } from '@/hooks/payee/useSireneSearch';
import type { SireneSearchResultDto } from '@/types/payee';

interface SirenSearchCardProps {
  /** Called when a successful search result is returned from the API. */
  onResult: (result: SireneSearchResultDto) => void;
}

/**
 * Search card for SIREN/SIRET lookup via the INSEE Sirene proxy.
 *
 * Accepts only digit input, detects SIREN (9 digits) / SIRET (14 digits)
 * automatically, shows a detection pill, and triggers `onResult` on success.
 */
export function SirenSearchCard({ onResult }: SirenSearchCardProps) {
  const t = useTranslations('dashboard');
  const { query, setQuery, result, isLoading, error, detectedType, search, clear } =
    useSireneSearch();

  useEffect(() => {
    if (result) {
      onResult(result);
    }
  }, [result, onResult]);

  const handleChange = (e: React.ChangeEvent<HTMLInputElement>) => {
    const digits = e.target.value.replace(/\D/g, '');
    if (digits !== query) clear();
    setQuery(digits);
  };

  const handleKeyDown = (e: KeyboardEvent<HTMLInputElement>) => {
    if (e.key === 'Enter' && detectedType) {
      search();
    }
  };

  const remaining = query.length < 9 ? 9 - query.length : 14 - query.length;

  return (
    <div className="card card-no-hover">
      <div className="card-body">
        <h2 className="font-display font-bold text-lg text-text mb-1">
          {t('payees.search.title')}
        </h2>
        <p className="form-hint mb-4">{t('payees.search.subtitle')}</p>

        {/* Input row */}
        <div className="flex gap-3 items-center">
          <div className="relative flex-1">
            <span className="absolute left-3 top-1/2 -translate-y-1/2 text-text-2 text-sm pointer-events-none">
              🔍
            </span>
            <input
              id="siren-search-input"
              type="text"
              inputMode="numeric"
              maxLength={14}
              value={query}
              onChange={handleChange}
              onKeyDown={handleKeyDown}
              placeholder={t('payees.search.placeholder')}
              className={`form-input pl-9 font-mono text-sm w-full${detectedType ? ' success' : ''}`}
              aria-label={t('payees.search.title')}
              autoComplete="off"
            />
          </div>
          <button
            onClick={search}
            disabled={!detectedType || isLoading}
            className="btn btn-secondary btn-sm whitespace-nowrap flex items-center gap-2"
            aria-busy={isLoading}
          >
            {isLoading && (
              <span className="inline-block w-4 h-4 border-2 border-border border-t-text rounded-full animate-spin" />
            )}
            {isLoading ? t('payees.search.loading') : t('payees.search.button')}
          </button>
        </div>

        {/* Detection pill */}
        <div className="mt-2 min-h-6">
          {detectedType === 'siren' && (
            <span className="inline-flex items-center gap-1.5 px-2.5 py-1 rounded-full text-xs font-semibold bg-green/10 text-green border border-green/20">
              <span className="w-1.5 h-1.5 rounded-full bg-green" />
              {t('payees.search.detected.siren')}
            </span>
          )}
          {detectedType === 'siret' && (
            <span className="inline-flex items-center gap-1.5 px-2.5 py-1 rounded-full text-xs font-semibold bg-cyan/10 text-cyan border border-cyan/20">
              <span className="w-1.5 h-1.5 rounded-full bg-cyan" />
              {t('payees.search.detected.siret')}
            </span>
          )}
          {!detectedType && query.length > 0 && (
            <span className="inline-flex items-center gap-1.5 px-2.5 py-1 rounded-full text-xs font-semibold bg-border/20 text-text-2 border border-border">
              {t('payees.search.hint', { remaining })}
            </span>
          )}
        </div>

        {/* Error display */}
        {error && (
          <div className="mt-3 bg-red/8 border-l-[3px] border-red rounded-lg p-3 text-sm text-red">
            {t(error as Parameters<typeof t>[0])}
          </div>
        )}
      </div>
    </div>
  );
}
