'use client';

import { useEffect, KeyboardEvent } from 'react';
import { useTranslations } from 'next-intl';
import { useSireneSearch } from '@/hooks/payee/useSireneSearch';
import type { SireneSearchResultDto } from '@/types/payee';

interface SirenSearchCardProps {
  onResult: (result: SireneSearchResultDto) => void;
}

export function SirenSearchCard({ onResult }: SirenSearchCardProps) {
  const t = useTranslations('dashboard');
  const { query, setQuery, result, isLoading, error, detectedType, search, clear } =
    useSireneSearch();

  useEffect(() => {
    if (result) onResult(result);
  }, [result, onResult]);

  const handleChange = (e: React.ChangeEvent<HTMLInputElement>) => {
    const digits = e.target.value.replace(/\D/g, '');
    if (digits !== query) clear();
    setQuery(digits);
  };

  const handleKeyDown = (e: KeyboardEvent<HTMLInputElement>) => {
    if (e.key === 'Enter' && detectedType) search();
  };

  const remaining = query.length < 9 ? 9 - query.length : 14 - query.length;

  return (
    <div>
      <div className="siren-search-row">
        <div className="flex-1">
          <label className="fl">{t('payees.search.label')}</label>
          <input
            id="siren-search-input"
            type="text"
            inputMode="numeric"
            maxLength={14}
            value={query}
            onChange={handleChange}
            onKeyDown={handleKeyDown}
            placeholder={t('payees.search.placeholder')}
            className="fi payee-fi-mono"
            aria-label={t('payees.search.title')}
            autoComplete="off"
          />
        </div>
        <button
          onClick={search}
          disabled={!detectedType || isLoading}
          className="btn btn-primary siren-search-btn"
          aria-busy={isLoading}
        >
          {isLoading
            ? <><span className="spinner spinner-inline" />{t('payees.search.loading')}</>
            : <>🔍 {t('payees.search.button')}</>
          }
        </button>
      </div>

      {/* Detection / hint pill */}
      <div className="siren-hint-row">
        {detectedType === 'siren' && (
          <span className="payee-dpill siren">
            <span className="payee-dpill-d" />
            {t('payees.search.detected.siren')}
          </span>
        )}
        {detectedType === 'siret' && (
          <span className="payee-dpill siret">
            <span className="payee-dpill-d" />
            {t('payees.search.detected.siret')}
          </span>
        )}
        {!detectedType && query.length > 0 && (
          <span className="payee-dpill hint">
            <span className="payee-dpill-d" />
            {t('payees.search.hint', { remaining })}
          </span>
        )}
      </div>

      {error && (
        <div className="alert alert-error">
          {t(error as Parameters<typeof t>[0])}
        </div>
      )}
    </div>
  );
}
