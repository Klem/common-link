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
      <div style={{ display: 'flex', gap: 10, alignItems: 'flex-end', marginBottom: 8 }}>
        <div style={{ flex: 1 }}>
          <label className="cm-label">{t('payees.search.label')}</label>
          <input
            id="siren-search-input"
            type="text"
            inputMode="numeric"
            maxLength={14}
            value={query}
            onChange={handleChange}
            onKeyDown={handleKeyDown}
            placeholder={t('payees.search.placeholder')}
            className="cm-fi"
            style={{ fontFamily: 'monospace' }}
            aria-label={t('payees.search.title')}
            autoComplete="off"
          />
        </div>
        <button
          onClick={search}
          disabled={!detectedType || isLoading}
          className="cm-btn cm-btn-primary"
          aria-busy={isLoading}
          style={{ height: 44, flexShrink: 0 }}
        >
          {isLoading
            ? <><span className="rm-spinner" style={{ marginRight: 6 }} />{t('payees.search.loading')}</>
            : <>🔍 {t('payees.search.button')}</>
          }
        </button>
      </div>

      {/* Detection / hint pill */}
      <div style={{ marginBottom: 8, minHeight: 24 }}>
        {detectedType === 'siren' && (
          <span className="rm-dpill siren">
            <span className="rm-dpill-d" />
            {t('payees.search.detected.siren')}
          </span>
        )}
        {detectedType === 'siret' && (
          <span className="rm-dpill siret">
            <span className="rm-dpill-d" />
            {t('payees.search.detected.siret')}
          </span>
        )}
        {!detectedType && query.length > 0 && (
          <span className="rm-dpill hint">
            <span className="rm-dpill-d" />
            {t('payees.search.hint', { remaining })}
          </span>
        )}
      </div>

      {error && (
        <div className="rm-error-inline">
          {t(error as Parameters<typeof t>[0])}
        </div>
      )}
    </div>
  );
}
