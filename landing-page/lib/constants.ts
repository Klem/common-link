export const APP_URL = 'https://app.common-link.org';

/**
 * Base URL de l'API CommonLink.
 *
 * Même défaut que `app/` (`src/lib/api.ts`) pour que le développement local marche sans .env.
 * Les appels sont faits côté serveur (composants serveur Next), donc aucune configuration CORS
 * n'est nécessaire — mais la variable doit être définie en production, sinon la page projets
 * interroge `localhost` et affiche une grille vide.
 */
export const API_URL = process.env.NEXT_PUBLIC_API_URL || 'http://localhost:8080';
