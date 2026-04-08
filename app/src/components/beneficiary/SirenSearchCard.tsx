'use client';

import { useEffect, KeyboardEvent } from 'react';
import { useTranslations } from 'next-intl';
import { useSireneSearch } from '@/hooks/beneficiary/useSireneSearch';
import type { SireneSearchResultDto } from '@/types/beneficiary';

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

  // Forward the result to the parent when it arrives
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

  const inputClass = [
    'flex-1 bg-bg-3 border text-text rounded-[10px] px-4 py-3 font-mono text-[14px] outline-none transition-all duration-150',
    detectedType
      ? 'border-green/40'
      : 'border-border focus:border-cyan/40 focus:shadow-[0_0_0_3px_rgba(79,126,134,.12)]',
  ].join(' ');

  const remaining = query.length < 9 ? 9 - query.length : 14 - query.length;

  return (
    <div className="bg-bg-2 border border-border rounded-xl p-[28px]">
      <h2 className="font-display font-bold text-[18px] text-text mb-[4px]">
        {t('beneficiaries.search.title')}
      </h2>
      <p className="text-[13px] text-text-2 mb-[18px]">
        {t('beneficiaries.search.subtitle')}
      </p>

      {/* Input row */}
      <div className="flex gap-[10px] items-center">
        <input
          type="text"
          inputMode="numeric"
          maxLength={14}
          value={query}
          onChange={handleChange}
          onKeyDown={handleKeyDown}
          placeholder={t('beneficiaries.search.placeholder')}
          className={inputClass}
          aria-label={t('beneficiaries.search.title')}
          autoComplete="off"
        />
        <button
          onClick={search}
          disabled={!detectedType || isLoading}
          className={[
            'bg-green text-black font-display font-bold rounded-[10px] px-5 py-3 transition-opacity duration-150 whitespace-nowrap flex items-center gap-2',
            !detectedType || isLoading ? 'opacity-38 cursor-not-allowed' : 'hover:opacity-90',
          ].join(' ')}
          aria-busy={isLoading}
        >
          {isLoading ? (
            <>
              <span className="inline-block w-4 h-4 border-2 border-black/30 border-t-black rounded-full animate-spin" />
              {t('beneficiaries.search.loading')}
            </>
          ) : (
            t('beneficiaries.search.button')
          )}
        </button>
      </div>

      {/* Detection pill */}
      <div className="mt-[10px] min-h-[24px]">
        {detectedType === 'siren' && (
          <span className="inline-flex items-center gap-[6px] px-[10px] py-[4px] rounded-full text-[11px] font-semibold bg-green/10 text-green border border-green/20">
            <span className="w-[6px] h-[6px] rounded-full bg-green" />
            {t('beneficiaries.search.detected.siren')}
          </span>
        )}
        {detectedType === 'siret' && (
          <span className="inline-flex items-center gap-[6px] px-[10px] py-[4px] rounded-full text-[11px] font-semibold bg-cyan/10 text-cyan border border-cyan/20">
            <span className="w-[6px] h-[6px] rounded-full bg-cyan" />
            {t('beneficiaries.search.detected.siret')}
          </span>
        )}
        {!detectedType && query.length > 0 && (
          <span className="inline-flex items-center gap-[6px] px-[10px] py-[4px] rounded-full text-[11px] font-semibold bg-border/20 text-text-2 border border-border">
            {t('beneficiaries.search.hint', { remaining })}
          </span>
        )}
      </div>

      {/* Loading text */}
      {isLoading && (
        <p className="mt-[12px] text-text-2 text-[13px]">
          {t('beneficiaries.search.loading')}
        </p>
      )}

      {/* Error display */}
      {error && (
        <div className="mt-[12px] bg-red/8 border-l-[3px] border-red rounded-[8px] p-[12px] text-[13px] text-red">
          {t(error as Parameters<typeof t>[0])}
        </div>
      )}
    </div>
  );
}
