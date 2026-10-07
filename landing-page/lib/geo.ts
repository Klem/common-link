/** Public, key-less French administrative boundaries API, called from the browser. */
const GEO_API_BASE = 'https://geo.api.gouv.fr';

/** A commune picked as the proximity centre. */
export interface GeoPoint {
  /** Display label, e.g. `Vallauris (06)`. */
  label: string;
  latitude: number;
  longitude: number;
  /** Department of the commune — matches campaigns whose place of action is that department. */
  departmentCode: string;
}

interface GeoCommune {
  nom: string;
  codeDepartement: string;
  centre?: { coordinates: [number, number] };
}

const POSTAL_CODE = /^\d{5}$/;

/**
 * Finds communes matching a town name or a postal code, with their centre. No browser
 * geolocation is ever used: the visitor types the place.
 *
 * @throws Error when geo.api.gouv.fr is unreachable or answers an error.
 */
export async function searchCommunes(query: string): Promise<GeoPoint[]> {
  const q = query.trim();
  if (q.length < 2) return [];
  const filter = POSTAL_CODE.test(q) ? `codePostal=${q}` : `nom=${encodeURIComponent(q)}&boost=population`;
  const res = await fetch(`${GEO_API_BASE}/communes?${filter}&fields=nom,centre,codeDepartement&limit=6`);
  if (!res.ok) throw new Error(`geo.api.gouv.fr ${res.status}`);
  const communes = (await res.json()) as GeoCommune[];
  return communes
    .filter((c) => c.centre)
    .map((c) => ({
      label: `${c.nom} (${c.codeDepartement})`,
      // GeoJSON order: [longitude, latitude].
      latitude: c.centre!.coordinates[1],
      longitude: c.centre!.coordinates[0],
      departmentCode: c.codeDepartement,
    }));
}

const EARTH_RADIUS_KM = 6371;
const toRad = (deg: number) => (deg * Math.PI) / 180;

/** Great-circle distance in kilometres between two WGS84 points (haversine). */
export function distanceKm(lat1: number, lon1: number, lat2: number, lon2: number): number {
  const dLat = toRad(lat2 - lat1);
  const dLon = toRad(lon2 - lon1);
  const a = Math.sin(dLat / 2) ** 2 + Math.cos(toRad(lat1)) * Math.cos(toRad(lat2)) * Math.sin(dLon / 2) ** 2;
  return 2 * EARTH_RADIUS_KM * Math.asin(Math.sqrt(a));
}
