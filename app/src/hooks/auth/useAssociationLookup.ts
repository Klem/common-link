'use client';

import { useMemo, useState } from 'react';

/** Base URL of the Recherche d'entreprises open API. Keyless and CORS-open, like the JOAFE dataset. */
const RECHERCHE_ENTREPRISES_BASE = 'https://recherche-entreprises.api.gouv.fr';

/** Head-office fields of a `results[]` entry. */
interface RegistrySiege {
  libelle_commune?: string;
  code_postal?: string;
  adresse?: string;
}

/** Subset of a `results[]` entry that this form consumes. */
interface RegistryResult {
  siren?: string;
  nom_raison_sociale?: string;
  nom_complet?: string;
  identifiant_association?: string;
  nature_juridique?: string;
  etat_administratif?: string;
  complements?: { est_association?: boolean };
  siege?: RegistrySiege;
}

interface RegistryResponse {
  results?: RegistryResult[];
}

/** Registry snapshot used to pre-fill the manual sign-up form. */
export interface AssociationLookupResult {
  /** SIREN (9 digits) that was looked up and matched exactly. */
  siren: string;
  /** Legal name of the entity as held by the registry. */
  name: string;
  /** City of the registered office, null when the registry does not expose it. */
  city: string | null;
  /** Postal code of the registered office, null when the registry does not expose it. */
  postalCode: string | null;
  /** Full street address of the registered office, null when absent. */
  address: string | null;
  /** RNA the registry associates with this SIREN, when one exists. Informational only. */
  rna: string | null;
  /** INSEE legal category code (e.g. "9220"), null when absent. */
  legalCategory: string | null;
  /** Whether the registry flags the entity as an association. Displayed, never blocking. */
  isAssociation: boolean;
  /** Whether the entity is administratively active. */
  active: boolean;
}

/** Return type of {@link useAssociationLookup}. */
export interface UseAssociationLookupReturn {
  /** Current SIREN input, separators removed, capped at 9 characters. */
  siren: string;
  /** Updates the SIREN input; display separators are stripped. */
  setSiren: (value: string) => void;
  /** Last successful registry match, or null. */
  result: AssociationLookupResult | null;
  /** True while the lookup is in flight. */
  isLoading: boolean;
  /** i18n key (under the `auth` namespace) for the last error, or null. */
  error: string | null;
  /** True when the input holds exactly 9 digits and the lookup can be triggered. */
  isComplete: boolean;
  /** Runs the lookup against the registry. No-op while the SIREN is incomplete. */
  lookup: () => Promise<void>;
  /** Clears the result and the error, leaving the input untouched. */
  clear: () => void;
}

/** Returns a trimmed value, or null when absent or blank. */
function textOrNull(value: string | undefined): string | null {
  const trimmed = value?.trim();
  return trimmed ? trimmed : null;
}

/**
 * Maps a registry entry to the pre-fill shape.
 *
 * `nom_raison_sociale` is the bare legal name; `nom_complet` appends the acronym
 * ("CROIX ROUGE FRANCAISE (CRF)"), which has no place on a Cerfa receipt. Some entries carry only
 * the latter, hence the fallback.
 */
function mapRegistryResult(siren: string, entry: RegistryResult): AssociationLookupResult {
  return {
    siren,
    name: textOrNull(entry.nom_raison_sociale) ?? textOrNull(entry.nom_complet) ?? '—',
    city: textOrNull(entry.siege?.libelle_commune),
    postalCode: textOrNull(entry.siege?.code_postal),
    address: textOrNull(entry.siege?.adresse),
    rna: textOrNull(entry.identifiant_association),
    legalCategory: textOrNull(entry.nature_juridique),
    isAssociation: entry.complements?.est_association ?? false,
    active: entry.etat_administratif === 'A',
  };
}

/**
 * Hook driving the SIREN lookup used by the manual association sign-up form.
 *
 * Queries the Recherche d'entreprises open registry straight from the browser, the same way
 * {@link AssoSearch} queries the JOAFE dataset for RNA numbers. No backend proxy: the API needs no
 * key and sends `access-control-allow-origin: *`. Going direct also means each visitor spends their
 * own IP quota against the registry — a server-side proxy would funnel every visitor through the
 * single server IP, which is the configuration that throttles first.
 *
 * The registry endpoint is a full-text search, so the SIREN is re-checked against every candidate:
 * a fuzzy top hit must never be presented to the user as their own association.
 *
 * Nothing here is a compliance control. The identity is confirmed by the curator registry pre-check
 * at KYC time, and the SIREN uniqueness guard lives in the backend sign-up path.
 */
export function useAssociationLookup(): UseAssociationLookupReturn {
  const [siren, setSirenRaw] = useState('');
  const [result, setResult] = useState<AssociationLookupResult | null>(null);
  const [isLoading, setIsLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const isComplete = useMemo(() => /^\d{9}$/.test(siren), [siren]);

  /**
   * Strips display separators (whitespace, dots, dashes) and caps the length at a SIREN.
   *
   * Letters are kept rather than dropped, so an RNA pasted here stays visibly invalid instead of
   * being silently truncated to its 9 digits and treated as an unrelated SIREN.
   */
  const setSiren = (value: string): void => {
    setSirenRaw(value.replace(/[\s.-]/g, '').slice(0, 9));
    setResult(null);
    setError(null);
  };

  const lookup = async (): Promise<void> => {
    if (!isComplete) return;
    setIsLoading(true);
    setError(null);
    setResult(null);
    try {
      const params = new URLSearchParams({ q: siren, per_page: '10' });
      const res = await fetch(`${RECHERCHE_ENTREPRISES_BASE}/search?${params}`);
      if (res.status === 429) {
        setError('assoManual.errors.rateLimited');
        return;
      }
      if (!res.ok) {
        setError('assoManual.errors.unavailable');
        return;
      }
      const data = (await res.json()) as RegistryResponse;
      const match = (data.results ?? []).find((entry) => entry.siren === siren);
      if (!match) {
        setError('assoManual.errors.notFound');
        return;
      }
      setResult(mapRegistryResult(siren, match));
    } catch {
      setError('assoManual.errors.unavailable');
    } finally {
      setIsLoading(false);
    }
  };

  /** Resets the registry match and error without clearing the typed SIREN. */
  const clear = (): void => {
    setResult(null);
    setError(null);
  };

  return { siren, setSiren, result, isLoading, error, isComplete, lookup, clear };
}
