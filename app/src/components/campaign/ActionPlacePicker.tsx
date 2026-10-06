'use client';

import { useEffect, useMemo, useRef, useState } from 'react';
import { useLocale, useTranslations } from 'next-intl';
import { searchPlaces, type PlaceSuggestion } from '@/lib/api/geo';
import { ACTION_PLACE_COUNTRY_CODES } from '@/data/countryCodes';
import { ActionPlaceType, type ActionPlaceDto, type ActionPlaceRequest } from '@/types/campaign';

/** Picker mode: a French commune/department, the whole of France, or a foreign country. */
const PlaceMode = {
  LOCAL: 'LOCAL',
  FRANCE: 'FRANCE',
  PAYS: 'PAYS',
} as const;
type PlaceMode = typeof PlaceMode[keyof typeof PlaceMode];

const SEARCH_DEBOUNCE_MS = 300;

function modeOf(place: ActionPlaceDto | null): PlaceMode {
  if (place?.type === ActionPlaceType.FRANCE) return PlaceMode.FRANCE;
  if (place?.type === ActionPlaceType.PAYS) return PlaceMode.PAYS;
  return PlaceMode.LOCAL;
}

interface ActionPlacePickerProps {
  /** Place currently saved on the campaign (server-resolved), or null. */
  value: ActionPlaceDto | null;
  /** Called with the kind + code to save; the server validates and resolves the label. */
  onChange: (place: ActionPlaceRequest) => void;
}

/**
 * "Lieu de l'action" field of the campaign editor. The scope is never entered: it is derived
 * server-side from the place kind and displayed read-only.
 */
export function ActionPlacePicker({ value, onChange }: ActionPlacePickerProps) {
  const t = useTranslations('dashboard.campaigns.editor.info.actionPlace');
  const locale = useLocale();
  const [mode, setMode] = useState<PlaceMode>(modeOf(value));
  const [query, setQuery] = useState('');
  const [suggestions, setSuggestions] = useState<PlaceSuggestion[]>([]);
  const [searchState, setSearchState] = useState<'idle' | 'loading' | 'done' | 'error'>('idle');
  const timerRef = useRef<ReturnType<typeof setTimeout> | null>(null);

  useEffect(() => { setMode(modeOf(value)); }, [value]);
  useEffect(() => () => { if (timerRef.current) clearTimeout(timerRef.current); }, []);

  const countries = useMemo(() => {
    const names = new Intl.DisplayNames([locale], { type: 'region' });
    return ACTION_PLACE_COUNTRY_CODES
      .map((code) => ({ code, name: names.of(code) ?? code }))
      .sort((a, b) => a.name.localeCompare(b.name, locale));
  }, [locale]);

  const handleModeChange = (next: PlaceMode) => {
    setMode(next);
    if (next === PlaceMode.FRANCE) onChange({ type: ActionPlaceType.FRANCE });
  };

  const handleQueryChange = (q: string) => {
    setQuery(q);
    if (timerRef.current) clearTimeout(timerRef.current);
    if (q.trim().length < 2) {
      setSuggestions([]);
      setSearchState('idle');
      return;
    }
    timerRef.current = setTimeout(async () => {
      setSearchState('loading');
      try {
        setSuggestions(await searchPlaces(q));
        setSearchState('done');
      } catch {
        setSuggestions([]);
        setSearchState('error');
      }
    }, SEARCH_DEBOUNCE_MS);
  };

  const handlePick = (s: PlaceSuggestion) => {
    setQuery('');
    setSuggestions([]);
    setSearchState('idle');
    onChange({ type: s.type, code: s.code });
  };

  return (
    <div className="mb-14">
      <label className="cm-label">
        {t('label')}{' '}
        <span className="tip" data-tip={t('tip')}>?</span>
      </label>

      <div className="flex flex-wrap gap-4 mb-2" role="radiogroup" aria-label={t('label')}>
        {Object.values(PlaceMode).map((m) => (
          <label key={m} className="flex items-center gap-1.5 text-[13px] cursor-pointer">
            <input
              type="radio"
              name="action-place-mode"
              checked={mode === m}
              onChange={() => handleModeChange(m)}
            />
            {t(`type.${m}`)}
          </label>
        ))}
      </div>

      {mode === PlaceMode.LOCAL && (
        <div className="relative">
          <input
            className="cm-fi"
            type="text"
            value={query}
            onChange={(e) => handleQueryChange(e.target.value)}
            placeholder={t('searchPlaceholder')}
            aria-label={t('searchPlaceholder')}
          />
          {searchState === 'loading' && <div className="text-[12px] text-muted mt-1">{t('searching')}</div>}
          {searchState === 'error' && <div className="text-[12px] text-red mt-1">{t('searchError')}</div>}
          {searchState === 'done' && suggestions.length === 0 && (
            <div className="text-[12px] text-muted mt-1">{t('noResult')}</div>
          )}
          {suggestions.length > 0 && (
            <ul className="mt-1 border border-border rounded-[8px] bg-bg-3 max-h-[220px] overflow-y-auto" role="listbox">
              {suggestions.map((s) => (
                <li key={`${s.type}-${s.code}`} role="option" aria-selected={false}>
                  <button
                    type="button"
                    className="w-full text-left px-3 py-2 text-[13px] hover:bg-green/[.06] cursor-pointer"
                    onClick={() => handlePick(s)}
                  >
                    {s.label}
                    {s.type === ActionPlaceType.DEPARTEMENT && (
                      <span className="text-muted text-[11px] ml-2">{t('department')}</span>
                    )}
                  </button>
                </li>
              ))}
            </ul>
          )}
        </div>
      )}

      {mode === PlaceMode.PAYS && (
        <select
          className="cm-fi"
          value={value?.type === ActionPlaceType.PAYS ? value.code ?? '' : ''}
          onChange={(e) => e.target.value && onChange({ type: ActionPlaceType.PAYS, code: e.target.value })}
          aria-label={t('type.PAYS')}
        >
          <option value="">{t('countryPlaceholder')}</option>
          {countries.map((c) => (
            <option key={c.code} value={c.code}>{c.name}</option>
          ))}
        </select>
      )}

      <div className="text-[12px] text-text-2 mt-1.5">
        {value
          ? <>{t('current', { label: value.label })} · {t('scope.label')} : {t(`scope.${value.scope}`)}</>
          : t('none')}
      </div>
    </div>
  );
}
