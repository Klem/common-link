const API_BASE = 'https://recherche-entreprises.api.gouv.fr';
const NATURE_JURIDIQUE_ASSO = '9210,9220,9221,9222,9223,9224,9230,9240,9260,9300';

export interface AssociationSiege {
  siret?: string;
  libelle_commune?: string;
  code_postal?: string;
  departement?: string;
  region?: string;
  numero_voie?: string;
  type_voie?: string;
  libelle_voie?: string;
  adresse?: string;
  activite_principale?: string;
}

export interface AssociationDirigeant {
  nom?: string;
  prenom?: string;
  qualite?: string;
  fonction?: string;
}

export interface AssociationResult {
  siren: string;
  nom_complet?: string;
  nom_raison_sociale?: string;
  etat_administratif?: string;
  date_creation?: string;
  nature_juridique?: string;
  activite_principale?: string;
  section_activite_principale?: string;
  tranche_effectif_salarie?: string;
  nombre_etablissements?: number;
  siege?: AssociationSiege;
  dirigeants?: AssociationDirigeant[];
}

export interface SearchResponse {
  results: AssociationResult[];
  total_results: number;
}

export async function searchAssociations(
  query: string,
  perPage: number = 25,
  page: number = 1
): Promise<SearchResponse> {
  const params = new URLSearchParams({
    q: query,
    per_page: String(perPage),
    page: String(page),
    nature_juridique: NATURE_JURIDIQUE_ASSO,
    etat_administratif: 'A',
  });

  const res = await fetch(`${API_BASE}/search?${params}`);

  if (!res.ok) {
    throw new Error(`API error: ${res.status}`);
  }

  const data = await res.json();
  return {
    results: data.results || [],
    total_results: data.total_results || 0,
  };
}
