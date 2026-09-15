import { API_URL } from '@/lib/constants';

/**
 * Campagne publiée, telle que renvoyée par `GET /api/public/campaigns`.
 *
 * Miroir exact de `PublicCampaignListItemDto` côté API. Aucun statut, aucun identifiant interne :
 * l'API ne renvoie que ce que la carte affiche.
 */
export interface PublicCampaign {
  campaignId: string;
  campaignName: string;
  campaignEmoji: string;
  /** Catégorie libre saisie par l'association, ou `null` si elle n'en a pas choisi. */
  campaignCategory: string | null;
  /**
   * Chemin de service de l'image de couverture, ou `null`.
   * Ne jamais construire d'`<img>` quand la valeur est nulle : l'URL répond 404, pas un placeholder.
   */
  coverImage: string | null;
  /**
   * Horodatage ISO-8601 de la dernière modification de la campagne. `coverImage` est servie sur
   * une URL stable (ne varie pas au remplacement) avec un cache public de 5 minutes côté API :
   * ce champ sert de jeton de cache-busting, voir {@link coverImageUrl}.
   */
  campaignUpdatedAt: string;
  goal: number;
  raised: number;
  milestoneCount: number;
  associationName: string;
  /** Chemin de service du logo de l'association, ou `null`. Même mise en garde que `coverImage`. */
  associationLogo: string | null;
  /** URL absolue de la page de don publique, construite par l'API sur `app.frontend-url`. */
  donationUrl: string;
}

/**
 * Résout `coverImage` en URL absolue avec un paramètre de cache-busting.
 *
 * L'endpoint de service est public et stable (ne porte que l'id de campagne), servi avec
 * `Cache-Control: max-age=300, public` côté API — sans version, un navigateur ou un CDN peut
 * continuer à afficher l'ancienne image jusqu'à 5 minutes après un remplacement.
 *
 * @param coverImage - Chemin renvoyé par l'API (`PublicCampaign.coverImage`), jamais `null` ici :
 *   vérifier `coverImage !== null` avant d'appeler cette fonction.
 * @param version - Jeton de cache-busting, `PublicCampaign.campaignUpdatedAt`.
 */
export function coverImageUrl(coverImage: string, version: string): string {
  return `${API_URL}${coverImage}?v=${encodeURIComponent(version)}`;
}

/**
 * Récupère les campagnes actuellement ouvertes aux dons.
 *
 * Appel serveur avec revalidation à 5 minutes, alignée sur le `Cache-Control` de l'API.
 *
 * Ne propage jamais d'erreur : une API injoignable doit se traduire par un état vide sur la page
 * publique, pas par une 500. L'échec est tracé côté serveur.
 */
export async function fetchLiveCampaigns(): Promise<PublicCampaign[]> {
  try {
    const res = await fetch(`${API_URL}/api/public/campaigns`, {
      next: { revalidate: 300 },
    });

    if (!res.ok) {
      console.error(`[campaigns] GET /api/public/campaigns a répondu ${res.status}`);
      return [];
    }

    return (await res.json()) as PublicCampaign[];
  } catch (error) {
    console.error('[campaigns] API injoignable :', error);
    return [];
  }
}
