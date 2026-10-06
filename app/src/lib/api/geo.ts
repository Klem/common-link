import { ActionPlaceType } from '@/types/campaign';

/** Public, key-less French administrative boundaries API (also used server-side for validation). */
export const GEO_API_BASE = 'https://geo.api.gouv.fr';

/** A commune or department suggestion for the place-of-action picker. */
export interface PlaceSuggestion {
  type: typeof ActionPlaceType.COMMUNE | typeof ActionPlaceType.DEPARTEMENT;
  /** INSEE commune code or department code — what is sent to the API. */
  code: string;
  /** Display label, same format as the server-side one (`Vallauris (06)`). */
  label: string;
}

interface GeoCommune { nom: string; code: string; codeDepartement?: string }
interface GeoDepartment { nom: string; code: string }

const DEPARTMENT_CODE = /^(\d{2}|2[AB]|97[1-6])$/i;
const POSTAL_CODE = /^\d{5}$/;

async function getJson<T>(path: string): Promise<T> {
  const res = await fetch(`${GEO_API_BASE}${path}`);
  if (!res.ok) throw new Error(`geo.api.gouv.fr ${res.status}`);
  return (await res.json()) as T;
}

const communeSuggestion = (c: GeoCommune): PlaceSuggestion => ({
  type: ActionPlaceType.COMMUNE,
  code: c.code,
  label: c.codeDepartement ? `${c.nom} (${c.codeDepartement})` : c.nom,
});

const departmentSuggestion = (d: GeoDepartment): PlaceSuggestion => ({
  type: ActionPlaceType.DEPARTEMENT,
  code: d.code,
  label: `${d.nom} (${d.code})`,
});

/**
 * Searches communes and departments matching a free-text query: a postal code lists its
 * communes, a department code its department, anything else is matched by name (communes ranked
 * by population). Queries shorter than 2 characters return nothing.
 *
 * @throws Error when geo.api.gouv.fr is unreachable or answers an error.
 */
export async function searchPlaces(query: string): Promise<PlaceSuggestion[]> {
  const q = query.trim();
  if (q.length < 2) return [];
  const fields = 'fields=nom,code,codeDepartement';

  if (POSTAL_CODE.test(q)) {
    const communes = await getJson<GeoCommune[]>(`/communes?codePostal=${q}&${fields}`);
    return communes.map(communeSuggestion);
  }
  if (DEPARTMENT_CODE.test(q)) {
    const departments = await getJson<GeoDepartment[]>(`/departements?code=${q.toUpperCase()}`);
    return departments.map(departmentSuggestion);
  }
  const name = encodeURIComponent(q);
  const [departments, communes] = await Promise.all([
    // Department name matches are a bonus: their failure must not hide the commune results.
    getJson<GeoDepartment[]>(`/departements?nom=${name}`).catch(() => [] as GeoDepartment[]),
    getJson<GeoCommune[]>(`/communes?nom=${name}&${fields}&boost=population&limit=6`),
  ]);
  return [...departments.slice(0, 2).map(departmentSuggestion), ...communes.map(communeSuggestion)];
}
