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
  id?: string;
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

/** Subset of a `recherche-entreprises` result consumed by the RNA resolution step. */
interface RegistryResult {
  nom_complet?: string;
  nom_raison_sociale?: string;
  identifiant_association?: string;
  complements?: { est_association?: boolean; identifiant_association?: string };
}

/** A JOAFE row offered in the results list, before its RNA is known for certain. */
interface AssoCandidate {
  /** Stable React key — the JO announcement id, unique per row. */
  key: string;
  /**
   * RNA of the association, or null when the JOAFE row carries a legacy announcement number
   * instead (see [RNA_PATTERN]). A null here means the RNA must be resolved before sign-up.
   */
  rna: string | null;
  nom: string;
  ville: string;
  codePostal: string;
}

interface AssoSearchProps {
  onSelect: (asso: AssoResult) => void;
  /**
   * Switches to the manual SIREN form. When omitted the escape hatch is not rendered, so the
   * component keeps its original single-purpose behaviour.
   */
  onNoRna?: () => void;
}

type SearchState = 'idle' | 'loading' | 'results' | 'empty' | 'error';

const JOAFE_BASE = 'https://journal-officiel-datadila.opendatasoft.com/api/explore/v2.0/catalog/datasets/jo_associations/records';

/** Keyless, CORS-open registry — same source as the manual SIREN form. */
const RECHERCHE_ENTREPRISES_BASE = 'https://recherche-entreprises.api.gouv.fr';

/**
 * A real RNA: `W` followed by 9 alphanumerics (Corsican départements use `W2A`/`W2B`).
 *
 * The JOAFE dataset reuses the `numero_rna` column for announcements published before the RNA
 * existed (2009-2010), where it holds `ASS` + the announcement number *within its JO issue*.
 * That number restarts at every issue: `ASS01469` alone matches 806 announcements belonging to
 * hundreds of unrelated associations. It identifies nothing and must never be stored as an RNA.
 */
const RNA_PATTERN = /^W[0-9A-Z]{9}$/i;

/**
 * Escapes a user-typed term for an ODSQL double-quoted string literal.
 *
 * ODSQL does not accept SQL-style `''` doubling — a name such as "SAINT JULIEN L'ARS" sent that
 * way is rejected with HTTP 400. Double-quoted literals accept apostrophes as-is, so only the
 * backslash and the double quote need escaping.
 */
function escapeOdsql(term: string): string {
  return term.replace(/\\/g, '\\\\').replace(/"/g, '\\"');
}

/** Uppercases and strips accents and punctuation, so registry and JOAFE spellings can be compared. */
function normalizeName(name: string): string {
  return name
    .normalize('NFD')
    .replace(/[̀-ͯ]/g, '')
    .toUpperCase()
    .replace(/[^A-Z0-9]+/g, ' ')
    .trim();
}

function mapJoafeRecords(records: JoafeRecord[]): AssoCandidate[] {
  const byKey = new Map<string, { fields: JoafeFields; dissolved: boolean }>();

  for (const { record: { fields } } of records) {
    const rna = fields.numero_rna;
    if (!rna) continue;
    // Announcements of one association are collapsed on its RNA. Legacy rows cannot be collapsed
    // that way — their number is shared by unrelated associations, so merging on it would hide
    // real results. They are keyed by announcement id and each stands on its own.
    const key = RNA_PATTERN.test(rna) ? rna : fields.id ?? rna;
    const dissolved = fields.typeavis?.toLowerCase().includes('dissolution') ?? false;
    const existing = byKey.get(key);
    if (existing) {
      if (dissolved) existing.dissolved = true;
    } else {
      byKey.set(key, { fields, dissolved });
    }
  }

  return Array.from(byKey.entries())
    .filter(([, { dissolved }]) => !dissolved)
    .map(([key, { fields }]) => ({
      key,
      rna: fields.numero_rna && RNA_PATTERN.test(fields.numero_rna) ? fields.numero_rna : null,
      nom: fields.titre ?? '—',
      ville: fields.commune_actuelle ?? '',
      codePostal: fields.codepostal_actuel ?? '',
    }));
}

/**
 * Looks the association up in the national registry to recover the RNA a legacy JOAFE row omits.
 *
 * Accepts a result only when exactly one RNA answers to the name at that postal code, so an
 * approximate hit is never persisted as the association's legal identifier. Returns null when
 * the registry is silent, ambiguous, or unreachable — the caller then falls back to the manual
 * path rather than guessing.
 */
async function resolveRna(nom: string, codePostal: string): Promise<string | null> {
  const params = new URLSearchParams({ q: nom, per_page: '10' });
  if (codePostal) params.set('code_postal', codePostal);

  const res = await fetch(`${RECHERCHE_ENTREPRISES_BASE}/search?${params}`);
  if (!res.ok) return null;
  const data = (await res.json()) as { results?: RegistryResult[] };

  const target = normalizeName(nom);
  const rnas = new Set(
    (data.results ?? [])
      .filter((r) => r.complements?.est_association === true)
      .filter(
        (r) =>
          normalizeName(r.nom_complet ?? '') === target ||
          normalizeName(r.nom_raison_sociale ?? '') === target,
      )
      .map((r) => r.complements?.identifiant_association ?? r.identifiant_association)
      .filter((rna): rna is string => !!rna && RNA_PATTERN.test(rna)),
  );

  return rnas.size === 1 ? [...rnas][0] : null;
}

export function AssoSearch({ onSelect, onNoRna }: AssoSearchProps) {
  const t = useTranslations('auth');
  const [query, setQuery] = useState('');
  const [results, setResults] = useState<AssoCandidate[]>([]);
  const [searchState, setSearchState] = useState<SearchState>('idle');
  const [apiUnavailable, setApiUnavailable] = useState(false);
  const [resolvingKey, setResolvingKey] = useState<string | null>(null);
  const [rnaUnresolved, setRnaUnresolved] = useState(false);
  const timerRef = useRef<ReturnType<typeof setTimeout> | null>(null);

  const search = useCallback(async (q: string) => {
    if (q.length < 2) {
      setSearchState('idle');
      setResults([]);
      return;
    }
    setSearchState('loading');
    try {
      const safeQ = escapeOdsql(q);
      const isRna = /^W\d{6,}$/i.test(q.trim());
      const whereClause = isRna ? `numero_rna="${safeQ}"` : `titre like "%${safeQ}%"`;
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
    setRnaUnresolved(false);
    if (timerRef.current) clearTimeout(timerRef.current);
    timerRef.current = setTimeout(() => search(value), 320);
  };

  useEffect(() => () => { if (timerRef.current) clearTimeout(timerRef.current); }, []);

  const handleSelect = async (asso: AssoCandidate) => {
    setRnaUnresolved(false);

    let rna = asso.rna;
    if (!rna) {
      setResolvingKey(asso.key);
      try {
        rna = await resolveRna(asso.nom, asso.codePostal);
      } catch {
        rna = null;
      } finally {
        setResolvingKey(null);
      }
      if (!rna) {
        // No confirmed RNA: sign-up would otherwise store the legacy announcement number as the
        // association's legal identifier. Stop and point at the SIREN path instead.
        setRnaUnresolved(true);
        return;
      }
    }

    setQuery(asso.nom);
    onSelect({ identifier: rna, nom: asso.nom, ville: asso.ville, codePostal: asso.codePostal });
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
              key={asso.key}
              className="bg-bg-3 border border-border rounded-[10px] px-[14px] py-[13px] flex items-center gap-[11px] transition-all duration-200 cursor-pointer hover:border-green/30 hover:bg-green/[.04]"
              onClick={() => void handleSelect(asso)}
            >
              <div className="w-9 h-9 rounded-[9px] flex items-center justify-center font-display font-extrabold text-[15px] text-green flex-shrink-0 bg-green/10 border border-green/20">
                {asso.nom[0]?.toUpperCase()}
              </div>
              <div className="flex-1 min-w-0">
                <div className="text-[12.5px] font-semibold text-text truncate">{asso.nom}</div>
                <div className="text-[11px] text-muted flex flex-wrap gap-2">
                  <span>📍 {asso.ville} {asso.codePostal}</span>
                  {asso.rna && <span>RNA {asso.rna}</span>}
                </div>
              </div>
              <button
                type="button"
                onClick={(e) => { e.stopPropagation(); void handleSelect(asso); }}
                disabled={resolvingKey !== null}
                className="px-[11px] py-[5px] rounded-[6px] font-body text-[12px] font-semibold text-green flex-shrink-0 cursor-pointer transition-all duration-200 hover:opacity-80 bg-green/10 border border-green/25 disabled:opacity-50"
              >
                {resolvingKey === asso.key
                  ? t('assoSearch.resolvingRna')
                  : `${t('signup.association.search.select')} →`}
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

      {/* The registry could not confirm an RNA for the selected row. */}
      {rnaUnresolved && (
        <p className="text-[12px] text-red">{t('assoSearch.rnaUnresolved')}</p>
      )}

      {/* API unavailable error */}
      {apiUnavailable && (
        <p className="text-[12px] text-red">{t('assoSearch.apiUnavailable')}</p>
      )}

      {/* Escape hatch for associations registered with a SIREN but no RNA: they are absent from
          JOAFE, so no query here can ever find them. */}
      {onNoRna && (
        <button
          type="button"
          onClick={onNoRna}
          className="self-start text-[11.5px] text-cyan bg-transparent border-none cursor-pointer p-0 underline-offset-2 hover:underline"
        >
          {t('assoSearch.noRna')}
        </button>
      )}
    </div>
  );
}
