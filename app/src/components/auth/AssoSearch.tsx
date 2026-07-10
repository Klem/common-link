'use client';

import { useState, useRef, useEffect, useCallback } from 'react';
import { useTranslations } from 'next-intl';

export interface AssoResult {
  identifier: string;
  nom: string;
  ville: string;
  codePostal: string;
}

interface JoafeFields {
  numero_rna?: string;
  titre?: string;
  typeavis?: string;
  commune_actuelle?: string;
  codepostal_actuel?: string;
}

interface JoafeRecord {
  record: { fields: JoafeFields };
}

interface JoafeResponse {
  records: JoafeRecord[];
}

interface AssoSearchProps {
  onSelect: (asso: AssoResult) => void;
}

type SearchState = 'idle' | 'loading' | 'results' | 'empty' | 'error';

const JOAFE_BASE = 'https://journal-officiel-datadila.opendatasoft.com/api/explore/v2.0/catalog/datasets/jo_associations/records';

function mapJoafeRecords(records: JoafeRecord[]): AssoResult[] {
  const byRna = new Map<string, { fields: JoafeFields; dissolved: boolean }>();

  for (const { record: { fields } } of records) {
    const rna = fields.numero_rna;
    if (!rna) continue;
    const dissolved = fields.typeavis?.toLowerCase().includes('dissolution') ?? false;
    const existing = byRna.get(rna);
    if (existing) {
      if (dissolved) existing.dissolved = true;
    } else {
      byRna.set(rna, { fields, dissolved });
    }
  }

  return Array.from(byRna.values())
    .filter(({ dissolved }) => !dissolved)
    .map(({ fields }) => ({
      identifier: fields.numero_rna!,
      nom: fields.titre ?? '—',
      ville: fields.commune_actuelle ?? '',
      codePostal: fields.codepostal_actuel ?? '',
    }));
}

export function AssoSearch({ onSelect }: AssoSearchProps) {
  const t = useTranslations('auth');
  const [query, setQuery] = useState('');
  const [results, setResults] = useState<AssoResult[]>([]);
  const [searchState, setSearchState] = useState<SearchState>('idle');
  const [apiUnavailable, setApiUnavailable] = useState(false);
  const timerRef = useRef<ReturnType<typeof setTimeout> | null>(null);

  const search = useCallback(async (q: string) => {
    if (q.length < 2) {
      setSearchState('idle');
      setResults([]);
      return;
    }
    setSearchState('loading');
    try {
      const safeQ = q.replace(/'/g, "''");
      const isRna = /^W\d{6,}$/i.test(q.trim());
      const whereClause = isRna ? `numero_rna='${safeQ}'` : `titre like '%${safeQ}%'`;
      const params = new URLSearchParams({ where: whereClause, limit: '10' });
      const res = await fetch(`${JOAFE_BASE}?${params}`);
      if (!res.ok) throw new Error('API error');
      const data = (await res.json()) as JoafeResponse;
      const mapped = mapJoafeRecords(data.records ?? []);
      setResults(mapped);
      setSearchState(mapped.length > 0 ? 'results' : 'empty');
      setApiUnavailable(false);
    } catch {
      setSearchState('error');
      setApiUnavailable(true);
    }
  }, []);

  const handleInput = (value: string) => {
    setQuery(value);
    if (timerRef.current) clearTimeout(timerRef.current);
    timerRef.current = setTimeout(() => search(value), 320);
  };

  useEffect(() => () => { if (timerRef.current) clearTimeout(timerRef.current); }, []);

  const handleSelect = (asso: AssoResult) => {
    setQuery(asso.nom);
    onSelect(asso);
  };

  return (
    <div className="flex flex-col gap-3">
      <p className="text-[12.5px] text-text-2 leading-[1.65]">{t('assoSearch.searchHint')}</p>

      <div className="relative">
        <span className="absolute left-[13px] top-1/2 -translate-y-1/2 text-muted text-[15px] pointer-events-none">
          🔍
        </span>
        <input
          type="text"
          value={query}
          onChange={(e) => handleInput(e.target.value)}
          placeholder={t('signup.association.search.placeholder')}
          autoComplete="off"
          className="form-input pl-[40px] pr-[44px]"
        />
        {searchState === 'loading' && (
          <div
            className="absolute right-[13px] top-1/2 -translate-y-1/2 w-4 h-4 rounded-full border-2 border-green-dim border-t-green animate-spin-around-slow"
          />
        )}
      </div>

      {/* Results list */}
      {searchState === 'results' && results.length > 0 && (
        <div className="flex flex-col gap-[7px] max-h-[260px] overflow-y-auto pr-[2px]">
          {results.map((asso) => (
            <div
              key={asso.identifier}
              className="bg-bg-3 border border-border rounded-[10px] px-[14px] py-[13px] flex items-center gap-[11px] transition-all duration-200 cursor-pointer hover:border-green/30 hover:bg-green/[.04]"
              onClick={() => handleSelect(asso)}
            >
              <div className="w-9 h-9 rounded-[9px] flex items-center justify-center font-display font-extrabold text-[15px] text-green flex-shrink-0 bg-green/10 border border-green/20">
                {asso.nom[0]?.toUpperCase()}
              </div>
              <div className="flex-1 min-w-0">
                <div className="text-[12.5px] font-semibold text-text truncate">{asso.nom}</div>
                <div className="text-[11px] text-muted flex flex-wrap gap-2">
                  <span>📍 {asso.ville} {asso.codePostal}</span>
                  <span>RNA {asso.identifier}</span>
                </div>
              </div>
              <button
                type="button"
                onClick={(e) => { e.stopPropagation(); handleSelect(asso); }}
                className="px-[11px] py-[5px] rounded-[6px] font-body text-[12px] font-semibold text-green flex-shrink-0 cursor-pointer transition-all duration-200 hover:opacity-80 bg-green/10 border border-green/25"
              >
                {t('signup.association.search.select')} →
              </button>
            </div>
          ))}
        </div>
      )}

      {searchState === 'empty' && (
        <div className="text-center py-5 text-muted text-[13px]">
          🔭 {t('assoSearch.noResults')}
          <span className="block text-[11px] mt-1">{t('assoSearch.noResultsHint')}</span>
        </div>
      )}

      {/* API unavailable error */}
      {apiUnavailable && (
        <p className="text-[12px] text-red">{t('assoSearch.apiUnavailable')}</p>
      )}

    </div>
  );
}
