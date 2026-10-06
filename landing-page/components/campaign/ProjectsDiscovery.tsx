'use client';

import { useEffect, useMemo, useRef, useState } from 'react';
import { useTranslations } from 'next-intl';
import { CampaignCard } from '@/components/campaign/CampaignCard';
import type { PublicCampaign } from '@/lib/api/campaigns';
import {
  ActionPlaceType,
  CAUSE_EMOJI,
  CampaignScope,
  DISPLAYED_CAUSES,
  SEE_ALSO,
  type CampaignCause,
} from '@/lib/causes';
import { distanceKm, searchCommunes, type GeoPoint } from '@/lib/geo';

/** Cause chips shown before the « Plus » button. */
const VISIBLE_CAUSE_CHIPS = 6;
/** Radius choices of the proximity filter; the first activation uses {@link DEFAULT_RADIUS_KM}. */
const RADIUS_OPTIONS_KM = [10, 20, 50, 100] as const;
const DEFAULT_RADIUS_KM = 20;
const SEARCH_DEBOUNCE_MS = 300;

const SearchState = {
  IDLE: 'IDLE',
  LOADING: 'LOADING',
  DONE: 'DONE',
  ERROR: 'ERROR',
} as const;
type SearchState = typeof SearchState[keyof typeof SearchState];

/** Distance from the proximity centre, or null when the campaign does not match it locally. */
function localDistance(campaign: PublicCampaign, center: GeoPoint, radiusKm: number): number | null {
  const place = campaign.actionPlace;
  if (!place) return null;
  if (place.type === ActionPlaceType.DEPARTEMENT) {
    // A department has no point: it matches when the visitor's commune is in it.
    return place.code === center.departmentCode ? 0 : null;
  }
  if (place.type === ActionPlaceType.COMMUNE && place.latitude !== null && place.longitude !== null) {
    const d = distanceKm(center.latitude, center.longitude, place.latitude, place.longitude);
    return d <= radiusKm ? d : null;
  }
  return null;
}

interface ProjectsDiscoveryProps {
  /** Live campaigns, fetched server-side (≤ 60, see `PublicCampaignDirectoryService`). */
  campaigns: PublicCampaign[];
}

/**
 * Discovery filters of the `/projets` page — cause chips, scope, optional proximity — applied
 * client-side on the campaigns already fetched by the server component (the API endpoint takes
 * no parameter and is cached).
 *
 * Display rules (Asana discovery-filters spec):
 * - a cause chip appears only when at least one campaign has it; `AUTRE` never;
 * - beyond {@link VISIBLE_CAUSE_CHIPS} chips, a « Plus » button reveals the rest;
 * - « Voir aussi » cross-links Alimentation ↔ Solidarité and Alimentation ↔ Environnement;
 * - proximity is off by default; once a town or postal code is picked (no browser geolocation),
 *   local campaigns within the radius come first, national ones in a « Partout en France » block,
 *   international ones are excluded.
 */
export function ProjectsDiscovery({ campaigns }: ProjectsDiscoveryProps) {
  const t = useTranslations('landing.projects.filters');
  const tCause = useTranslations('landing.causes');

  const [cause, setCause] = useState<CampaignCause | null>(null);
  const [scope, setScope] = useState<CampaignScope | null>(null);
  const [showAllCauses, setShowAllCauses] = useState(false);

  const [proximityOpen, setProximityOpen] = useState(false);
  const [center, setCenter] = useState<GeoPoint | null>(null);
  const [radiusKm, setRadiusKm] = useState<number>(DEFAULT_RADIUS_KM);
  const [query, setQuery] = useState('');
  const [suggestions, setSuggestions] = useState<GeoPoint[]>([]);
  const [searchState, setSearchState] = useState<SearchState>(SearchState.IDLE);
  const timerRef = useRef<ReturnType<typeof setTimeout> | null>(null);

  useEffect(() => () => { if (timerRef.current) clearTimeout(timerRef.current); }, []);

  const availableCauses = useMemo(
    () => DISPLAYED_CAUSES.filter((c) => campaigns.some((x) => x.campaignCategory === c)),
    [campaigns],
  );
  const availableScopes = useMemo(
    () => Object.values(CampaignScope).filter((s) => campaigns.some((x) => x.actionPlace?.scope === s)),
    [campaigns],
  );

  const causeLabel = (c: CampaignCause) => `${CAUSE_EMOJI[c]} ${tCause(c)}`;

  // The active cause stays visible even when it sits beyond the first chips.
  const hiddenCount = Math.max(availableCauses.length - VISIBLE_CAUSE_CHIPS, 0);
  const visibleCauses = showAllCauses || hiddenCount === 0
    ? availableCauses
    : availableCauses.filter((c, i) => i < VISIBLE_CAUSE_CHIPS || c === cause);

  const seeAlso = (cause && SEE_ALSO[cause]?.filter((c) => availableCauses.includes(c))) ?? [];

  const byCause = useMemo(
    () => (cause === null ? campaigns : campaigns.filter((c) => c.campaignCategory === cause)),
    [campaigns, cause],
  );

  const { local, national } = useMemo(() => {
    if (!center) return { local: [], national: [] };
    const near = byCause
      .map((c) => ({ c, d: localDistance(c, center, radiusKm) }))
      .filter((x): x is { c: PublicCampaign; d: number } => x.d !== null)
      .sort((a, b) => a.d - b.d)
      .map((x) => x.c);
    return {
      local: near,
      national: byCause.filter((c) => c.actionPlace?.scope === CampaignScope.NATIONALE),
    };
  }, [byCause, center, radiusKm]);

  const byScope = useMemo(
    () => (scope === null ? byCause : byCause.filter((c) => c.actionPlace?.scope === scope)),
    [byCause, scope],
  );

  const handleQueryChange = (q: string) => {
    setQuery(q);
    if (timerRef.current) clearTimeout(timerRef.current);
    if (q.trim().length < 2) {
      setSuggestions([]);
      setSearchState(SearchState.IDLE);
      return;
    }
    timerRef.current = setTimeout(async () => {
      setSearchState(SearchState.LOADING);
      try {
        setSuggestions(await searchCommunes(q));
        setSearchState(SearchState.DONE);
      } catch {
        setSuggestions([]);
        setSearchState(SearchState.ERROR);
      }
    }, SEARCH_DEBOUNCE_MS);
  };

  const pickCenter = (p: GeoPoint) => {
    setCenter(p);
    setRadiusKm(DEFAULT_RADIUS_KM);
    setQuery('');
    setSuggestions([]);
    setSearchState(SearchState.IDLE);
  };

  const closeProximity = () => {
    setProximityOpen(false);
    setCenter(null);
    setQuery('');
    setSuggestions([]);
    setSearchState(SearchState.IDLE);
  };

  const resetFilters = () => {
    setCause(null);
    setScope(null);
    closeProximity();
  };

  const grid = (list: PublicCampaign[]) => (
    <div className="campaigns-grid">
      {list.map((campaign) => (
        <CampaignCard key={campaign.campaignId} campaign={campaign} />
      ))}
    </div>
  );

  const noMatch = (
    <div className="empty-state discovery-block">
      <p>{t('noMatch')}</p>
      <button type="button" className="btn btn-sm btn-secondary discovery-reset" onClick={resetFilters}>
        {t('reset')}
      </button>
    </div>
  );

  return (
    <>
      {availableCauses.length > 0 && (
        <div className="filter-bar discovery-filter-bar" role="group" aria-label={t('causesLabel')}>
          <button
            type="button"
            className={`filter-pill${cause === null ? ' active' : ''}`}
            aria-pressed={cause === null}
            onClick={() => setCause(null)}
          >
            {t('all')}
          </button>
          {visibleCauses.map((c) => (
            <button
              key={c}
              type="button"
              className={`filter-pill${cause === c ? ' active' : ''}`}
              aria-pressed={cause === c}
              onClick={() => setCause(c)}
            >
              {causeLabel(c)}
            </button>
          ))}
          {hiddenCount > 0 && (
            <button
              type="button"
              className="filter-pill"
              aria-expanded={showAllCauses}
              onClick={() => setShowAllCauses((v) => !v)}
            >
              {showAllCauses ? t('less') : t('more')}
            </button>
          )}
        </div>
      )}

      {seeAlso.length > 0 && (
        <p className="discovery-see-also">
          {t('seeAlso')}{' '}
          {seeAlso.map((c) => (
            <button key={c} type="button" className="discovery-link" onClick={() => setCause(c)}>
              {causeLabel(c)}
            </button>
          ))}
        </p>
      )}

      <div className="filter-bar discovery-filter-bar">
        {!center && availableScopes.length > 0 && (
          <div className="discovery-inline" role="group" aria-label={t('scopeLabel')}>
            <button
              type="button"
              className={`filter-pill${scope === null ? ' active' : ''}`}
              aria-pressed={scope === null}
              onClick={() => setScope(null)}
            >
              {t('scope.all')}
            </button>
            {availableScopes.map((s) => (
              <button
                key={s}
                type="button"
                className={`filter-pill${scope === s ? ' active' : ''}`}
                aria-pressed={scope === s}
                onClick={() => setScope(s)}
              >
                {t(`scope.${s}`)}
              </button>
            ))}
          </div>
        )}

        {!proximityOpen ? (
          <button type="button" className="filter-pill" onClick={() => setProximityOpen(true)}>
            📍 {t('proximity.toggle')}
          </button>
        ) : center ? (
          <div className="discovery-inline">
            <span className="filter-pill active">📍 {t('proximity.around', { label: center.label })}</span>
            <select
              className="form-input discovery-radius"
              aria-label={t('proximity.radiusLabel')}
              value={radiusKm}
              onChange={(e) => setRadiusKm(Number(e.target.value))}
            >
              {RADIUS_OPTIONS_KM.map((km) => (
                <option key={km} value={km}>{t('proximity.radiusOption', { km })}</option>
              ))}
            </select>
            <button type="button" className="discovery-link" onClick={() => setCenter(null)}>
              {t('proximity.change')}
            </button>
            <button type="button" className="discovery-link" onClick={closeProximity}>
              {t('proximity.off')}
            </button>
          </div>
        ) : (
          <div className="discovery-search">
            <input
              className="form-input"
              type="text"
              value={query}
              onChange={(e) => handleQueryChange(e.target.value)}
              placeholder={t('proximity.placeholder')}
              aria-label={t('proximity.placeholder')}
              autoFocus
            />
            {searchState === SearchState.LOADING && <p className="discovery-hint">{t('proximity.searching')}</p>}
            {searchState === SearchState.ERROR && <p className="discovery-hint">{t('proximity.searchError')}</p>}
            {searchState === SearchState.DONE && suggestions.length === 0 && (
              <p className="discovery-hint">{t('proximity.noResult')}</p>
            )}
            {suggestions.length > 0 && (
              <ul className="discovery-suggestions">
                {suggestions.map((s) => (
                  <li key={`${s.label}-${s.latitude}`}>
                    <button type="button" onClick={() => pickCenter(s)}>{s.label}</button>
                  </li>
                ))}
              </ul>
            )}
            <button type="button" className="discovery-link" onClick={closeProximity}>
              {t('proximity.off')}
            </button>
          </div>
        )}
      </div>

      {center ? (
        <>
          {local.length > 0 ? grid(local) : <p className="discovery-hint">{t('proximity.localEmpty')}</p>}
          {national.length > 0 && (
            <div className="discovery-block">
              <h2 className="discovery-block-title">{t('proximity.nationalBlock')}</h2>
              {grid(national)}
            </div>
          )}
          {local.length === 0 && national.length === 0 && noMatch}
        </>
      ) : byScope.length > 0 ? (
        grid(byScope)
      ) : (
        noMatch
      )}
    </>
  );
}

