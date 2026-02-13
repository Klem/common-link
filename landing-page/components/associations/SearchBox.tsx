'use client';

import { useState, useRef, useEffect, useCallback } from 'react';
import { useTranslations } from 'next-intl';
import { searchAssociations, AssociationResult } from '@/lib/api';

interface SearchBoxProps {
  onSearch: (query: string) => void;
  onLoading?: (loading: boolean) => void;
}

export function SearchBox({ onSearch }: SearchBoxProps) {
  const t = useTranslations('associations.search');
  const [query, setQuery] = useState('');
  const [acItems, setAcItems] = useState<AssociationResult[]>([]);
  const [showAc, setShowAc] = useState(false);
  const timeoutRef = useRef<NodeJS.Timeout | null>(null);
  const boxRef = useRef<HTMLDivElement>(null);

  const doFullSearch = useCallback(
    (q: string) => {
      if (!q.trim()) return;
      setShowAc(false);
      onSearch(q.trim());
    },
    [onSearch]
  );

  const fetchAutocomplete = useCallback(async (q: string) => {
    try {
      const data = await searchAssociations(q, 5, 1);
      setAcItems(data.results);
      setShowAc(data.results.length > 0);
    } catch {
      setShowAc(false);
    }
  }, []);

  const handleInput = (value: string) => {
    setQuery(value);
    if (timeoutRef.current) clearTimeout(timeoutRef.current);
    if (value.trim().length >= 2) {
      timeoutRef.current = setTimeout(() => fetchAutocomplete(value.trim()), 300);
    } else {
      setShowAc(false);
    }
  };

  const handleAcClick = (item: AssociationResult) => {
    const name = item.nom_complet || item.nom_raison_sociale || '';
    setQuery(name);
    setShowAc(false);
    doFullSearch(name);
  };

  const handleKeyDown = (e: React.KeyboardEvent) => {
    if (e.key === 'Enter') {
      e.preventDefault();
      setShowAc(false);
      doFullSearch(query);
    }
    if (e.key === 'Escape') setShowAc(false);
  };

  // Close autocomplete on outside click
  useEffect(() => {
    const handler = (e: MouseEvent) => {
      if (boxRef.current && !boxRef.current.contains(e.target as Node)) {
        setShowAc(false);
      }
    };
    document.addEventListener('click', handler);
    return () => document.removeEventListener('click', handler);
  }, []);

  return (
    <div className="relative" ref={boxRef}>
      <div className="flex items-center bg-white border-2 border-border rounded-lg overflow-hidden transition-all duration-200 focus-within:border-secondary focus-within:shadow-[0_0_0_4px_var(--color-secondary-pale),0_8px_32px_rgba(26,74,90,0.1)]">
        <span className="px-6 text-foreground-muted text-xl flex-shrink-0">
          🔍
        </span>
        <input
          type="text"
          value={query}
          onChange={(e) => handleInput(e.target.value)}
          onKeyDown={handleKeyDown}
          className="flex-1 border-none bg-transparent py-4 font-ui text-base text-foreground-dark outline-none placeholder:text-foreground-muted"
          placeholder={t('placeholder')}
          autoComplete="off"
        />
        <button
          onClick={() => {
            setShowAc(false);
            doFullSearch(query);
          }}
          className="bg-primary border-none px-7 py-4 text-white font-ui font-semibold text-[0.9rem] cursor-pointer transition-colors duration-200 hover:bg-primary-light"
        >
          {t('button')}
        </button>
      </div>

      {/* Autocomplete dropdown */}
      {showAc && acItems.length > 0 && (
        <div className="absolute top-[calc(100%+6px)] left-0 right-0 bg-white border border-border rounded-md shadow-xl z-50 max-h-80 overflow-y-auto animate-in">
          {acItems.map((item) => {
            const siege = item.siege || {};
            const name = item.nom_complet || item.nom_raison_sociale || '—';
            return (
              <div
                key={item.siren}
                onClick={() => handleAcClick(item)}
                className="flex items-center gap-4 px-6 py-4 cursor-pointer border-b border-border-light last:border-b-0 transition-colors duration-150 hover:bg-secondary-pale"
              >
                <div className="w-9 h-9 flex-shrink-0 bg-secondary-pale rounded-sm flex items-center justify-center text-primary font-ui font-bold text-[0.85rem]">
                  {name[0].toUpperCase()}
                </div>
                <div>
                  <div className="font-ui font-semibold text-[0.9rem] text-foreground-dark">
                    {name}
                  </div>
                  <div className="text-[0.8rem] text-foreground-muted">
                    {siege.code_postal || ''} {siege.libelle_commune || ''} · {t('siren')}{' '}
                    {item.siren || '—'}
                  </div>
                </div>
              </div>
            );
          })}
        </div>
      )}
    </div>
  );
}
