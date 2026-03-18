'use client';

import { useState, useRef, useEffect, useCallback } from 'react';
import { useTranslations } from 'next-intl';

export interface AssoResult {
  siren: string;
  nom: string;
  ville: string;
  codePostal: string;
  etat: 'A' | 'F';
}

interface ApiOrganization {
  nom_complet?: string;
  nom_raison_sociale?: string;
  siren: string;
  siege?: { code_postal?: string; libelle_commune?: string };
  etat_administratif?: 'A' | 'F';
}

interface ApiSearchResponse {
  results: ApiOrganization[];
  total_results?: number;
}

interface AssoSearchProps {
  onSelect: (asso: AssoResult) => void;
}

type SearchState = 'idle' | 'loading' | 'results' | 'empty' | 'error';

const API_BASE = 'https://recherche-entreprises.api.gouv.fr';
const NATURE_JURIDIQUE_ASSO = '9210,9220,9221,9222,9223,9224,9230,9240,9260,9300';

function mapOrg(org: ApiOrganization): AssoResult {
  return {
    siren: org.siren,
    nom: org.nom_complet ?? org.nom_raison_sociale ?? '—',
    ville: org.siege?.libelle_commune ?? '',
    codePostal: org.siege?.code_postal ?? '',
    etat: org.etat_administratif ?? 'A',
  };
}

export function AssoSearch({ onSelect }: AssoSearchProps) {
  const t = useTranslations('auth');
  const [query, setQuery] = useState('');
  const [results, setResults] = useState<AssoResult[]>([]);
  const [searchState, setSearchState] = useState<SearchState>('idle');
  const [apiUnavailable, setApiUnavailable] = useState(false);
  const [showManual, setShowManual] = useState(false);
  const [manualData, setManualData] = useState({ siren: '', nom: '', ville: '', codePostal: '' });
  const [dropdownOpen, setDropdownOpen] = useState(false);
  const timerRef = useRef<ReturnType<typeof setTimeout> | null>(null);
  const containerRef = useRef<HTMLDivElement>(null);

  const search = useCallback(async (q: string) => {
    if (q.length < 2) {
      setSearchState('idle');
      setResults([]);
      return;
    }
    setSearchState('loading');
    setDropdownOpen(false);
    try {
      const params = new URLSearchParams({
        q,
        per_page: '10',
        nature_juridique: NATURE_JURIDIQUE_ASSO,
        etat_administratif: 'A',
      });
      const url = `${API_BASE}/search?${params}`;
      const res = await fetch(url);
      if (!res.ok) throw new Error('API error');
      const data = (await res.json()) as ApiSearchResponse;
      const mapped = (data.results ?? []).map(mapOrg);
      setResults(mapped);
      setSearchState(mapped.length > 0 ? 'results' : 'empty');
      if (mapped.length > 0) setDropdownOpen(true);
      setApiUnavailable(false);
    } catch {
      setSearchState('error');
      setApiUnavailable(true);
    }
  }, []);

  const handleInput = (value: string) => {
    setQuery(value);
    setDropdownOpen(false);
    if (timerRef.current) clearTimeout(timerRef.current);
    timerRef.current = setTimeout(() => search(value), 320);
  };

  useEffect(() => {
    const handleClickOutside = (e: MouseEvent) => {
      if (containerRef.current && !containerRef.current.contains(e.target as Node)) {
        setDropdownOpen(false);
      }
    };
    document.addEventListener('mousedown', handleClickOutside);
    return () => document.removeEventListener('mousedown', handleClickOutside);
  }, []);

  useEffect(() => () => { if (timerRef.current) clearTimeout(timerRef.current); }, []);

  const handleSelect = (asso: AssoResult) => {
    setDropdownOpen(false);
    setQuery(asso.nom);
    onSelect(asso);
  };

  const handleManualConfirm = () => {
    if (!manualData.nom.trim() || !manualData.siren.trim()) return;
    onSelect({ ...manualData, etat: 'A' });
  };

  const fieldClass =
    'w-full bg-bg-3 border border-border text-text px-3 py-[10px] rounded-[8px] font-body text-[13.5px] outline-none transition-[border-color] duration-200 placeholder:text-muted focus:border-green/40';
  const labelClass = 'text-[11px] font-semibold text-text-2 uppercase tracking-[0.06em] block mb-[5px]';

  return (
    <div className="flex flex-col gap-3">
      <p className="text-[12.5px] text-text-2 leading-[1.65]">{t('assoSearch.searchHint')}</p>

      <div ref={containerRef} className="relative">
        <span className="absolute left-[13px] top-1/2 -translate-y-1/2 text-muted text-[15px] pointer-events-none">
          🔍
        </span>
        <input
          type="text"
          value={query}
          onChange={(e) => handleInput(e.target.value)}
          placeholder={t('signup.association.search.placeholder')}
          autoComplete="off"
          className="w-full bg-bg-3 border-[1.5px] border-border text-text pl-[40px] pr-[44px] py-3 rounded-[10px] font-body text-[13.5px] outline-none transition-all duration-[250ms] placeholder:text-muted focus:border-green/40 focus:shadow-[0_0_0_3px_rgba(0,184,154,.07)]"
        />
        {searchState === 'loading' && (
          <div
            className="absolute right-[13px] top-1/2 -translate-y-1/2 w-4 h-4 rounded-full border-2 border-green-dim border-t-green animate-spin-around-slow"
          />
        )}

        {/* Autocomplete dropdown */}
        {dropdownOpen && results.length > 0 && (
          <div className="absolute top-[calc(100%+5px)] left-0 right-0 bg-bg-2 border border-border rounded-[10px] shadow-[0_12px_40px_rgba(0,0,0,.55)] z-50 overflow-hidden">
            {results.slice(0, 5).map((asso) => (
              <button
                key={asso.siren}
                type="button"
                onClick={() => handleSelect(asso)}
                className="flex items-center gap-[11px] w-full px-[14px] py-[11px] text-left cursor-pointer transition-colors duration-150 border-b border-border/[.18] last:border-b-0 hover:bg-bg-3"
              >
                <div
                  className="w-[30px] h-[30px] rounded-[7px] flex items-center justify-center font-display font-bold text-[13px] text-green flex-shrink-0 bg-green/10 border border-green/20"
                >
                  {asso.nom[0]?.toUpperCase()}
                </div>
                <div>
                  <div className="text-[13px] font-semibold text-text">{asso.nom}</div>
                  <div className="text-[11px] text-muted">
                    {asso.codePostal} {asso.ville} · SIREN {asso.siren}
                  </div>
                </div>
              </button>
            ))}
          </div>
        )}
      </div>

      {/* Results list */}
      {searchState === 'results' && !dropdownOpen && results.length > 0 && (
        <div className="flex flex-col gap-[7px] max-h-[260px] overflow-y-auto pr-[2px]">
          {results.map((asso) => {
            const isActive = asso.etat === 'A';
            return (
              <div
                key={asso.siren}
                className={`bg-bg-3 border border-border rounded-[10px] px-[14px] py-[13px] flex items-center gap-[11px] transition-all duration-200 ${
                  isActive ? 'cursor-pointer hover:border-green/30 hover:bg-green/[.04]' : 'opacity-45 cursor-not-allowed'
                }`}
                onClick={() => isActive && handleSelect(asso)}
              >
                <div
                  className="w-9 h-9 rounded-[9px] flex items-center justify-center font-display font-extrabold text-[15px] text-green flex-shrink-0 bg-green/10 border border-green/20"
                >
                  {asso.nom[0]?.toUpperCase()}
                </div>
                <div className="flex-1 min-w-0">
                  <div className="text-[12.5px] font-semibold text-text truncate">{asso.nom}</div>
                  <div className="text-[11px] text-muted flex flex-wrap gap-2">
                    <span>📍 {asso.ville} {asso.codePostal}</span>
                    <span>SIREN {asso.siren}</span>
                    <span
                      className={`text-[10px] font-bold px-[6px] py-[2px] rounded-[4px] ${
                        isActive ? 'text-green bg-green/10' : 'text-red bg-red/10'
                      }`}
                    >
                      {isActive
                        ? t('signup.association.search.status.active')
                        : t('signup.association.search.status.ceased')}
                    </span>
                  </div>
                </div>
                {isActive && (
                  <button
                    type="button"
                    onClick={(e) => { e.stopPropagation(); handleSelect(asso); }}
                    className="px-[11px] py-[5px] rounded-[6px] font-body text-[12px] font-semibold text-green flex-shrink-0 cursor-pointer transition-all duration-200 hover:opacity-80 bg-green/10 border border-green/25"
                  >
                    {t('signup.association.search.select')} →
                  </button>
                )}
              </div>
            );
          })}
        </div>
      )}

      {searchState === 'empty' && (
        <div className="text-center py-5 text-muted text-[13px]">
          🔭 {t('assoSearch.noResults')}
          <span className="block text-[11px] mt-1">{t('assoSearch.noResultsHint')}</span>
        </div>
      )}

      {/* API unavailable — manual entry fallback */}
      {apiUnavailable && (
        <div>
          <p className="text-[12px] text-red mb-3">{t('assoSearch.apiUnavailable')}</p>
          {!showManual ? (
            <button
              type="button"
              onClick={() => setShowManual(true)}
              className="text-[12px] text-cyan bg-transparent border-none cursor-pointer p-0 underline-offset-2 hover:underline"
            >
              {t('assoSearch.manualEntry')} →
            </button>
          ) : (
            <div className="flex flex-col gap-[9px]">
              <div>
                <label className={labelClass}>{t('assoSearch.manualSiren')} *</label>
                <input
                  type="text"
                  value={manualData.siren}
                  onChange={(e) => setManualData((d) => ({ ...d, siren: e.target.value }))}
                  className={fieldClass}
                />
              </div>
              <div>
                <label className={labelClass}>{t('assoSearch.manualNom')} *</label>
                <input
                  type="text"
                  value={manualData.nom}
                  onChange={(e) => setManualData((d) => ({ ...d, nom: e.target.value }))}
                  className={fieldClass}
                />
              </div>
              <div className="grid grid-cols-2 gap-[9px]">
                <div>
                  <label className={labelClass}>{t('assoSearch.manualVille')}</label>
                  <input
                    type="text"
                    value={manualData.ville}
                    onChange={(e) => setManualData((d) => ({ ...d, ville: e.target.value }))}
                    className={fieldClass}
                  />
                </div>
                <div>
                  <label className={labelClass}>{t('assoSearch.manualCodePostal')}</label>
                  <input
                    type="text"
                    value={manualData.codePostal}
                    onChange={(e) => setManualData((d) => ({ ...d, codePostal: e.target.value }))}
                    className={fieldClass}
                  />
                </div>
              </div>
              <button
                type="button"
                onClick={handleManualConfirm}
                disabled={!manualData.nom.trim() || !manualData.siren.trim()}
                className="w-full py-[13px] bg-green text-black border-none rounded-md font-display text-[14px] font-bold cursor-pointer transition-all duration-200 hover:bg-[#00d4b0] disabled:opacity-[.38] disabled:cursor-not-allowed"
              >
                {t('assoSearch.confirm')}
              </button>
            </div>
          )}
        </div>
      )}
    </div>
  );
}
