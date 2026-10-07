/**
 * Campaign causes, mirror of the API `CampaignCause` enum (`api/.../entity/Enums.kt`).
 */
export const CampaignCause = {
  SOLIDARITE: 'SOLIDARITE',
  ALIMENTATION: 'ALIMENTATION',
  SANTE: 'SANTE',
  ENFANCE_EDUCATION: 'ENFANCE_EDUCATION',
  ANIMAUX: 'ANIMAUX',
  HANDICAP: 'HANDICAP',
  ENVIRONNEMENT: 'ENVIRONNEMENT',
  CULTURE: 'CULTURE',
  SPORT: 'SPORT',
  DROITS_CITOYENNETE: 'DROITS_CITOYENNETE',
  AUTRE: 'AUTRE',
} as const;
export type CampaignCause = typeof CampaignCause[keyof typeof CampaignCause];

/** Reach of a campaign, derived server-side from its place of action. */
export const CampaignScope = {
  LOCALE: 'LOCALE',
  NATIONALE: 'NATIONALE',
  INTERNATIONALE: 'INTERNATIONALE',
} as const;
export type CampaignScope = typeof CampaignScope[keyof typeof CampaignScope];

/** Kind of place of action, mirror of the API `ActionPlaceType` enum. */
export const ActionPlaceType = {
  COMMUNE: 'COMMUNE',
  DEPARTEMENT: 'DEPARTEMENT',
  FRANCE: 'FRANCE',
  PAYS: 'PAYS',
} as const;
export type ActionPlaceType = typeof ActionPlaceType[keyof typeof ActionPlaceType];

/** Chip order on the discovery page. `AUTRE` is deliberately absent: it is never displayed. */
export const DISPLAYED_CAUSES: readonly CampaignCause[] = [
  CampaignCause.SOLIDARITE,
  CampaignCause.ALIMENTATION,
  CampaignCause.SANTE,
  CampaignCause.ENFANCE_EDUCATION,
  CampaignCause.ANIMAUX,
  CampaignCause.HANDICAP,
  CampaignCause.ENVIRONNEMENT,
  CampaignCause.CULTURE,
  CampaignCause.SPORT,
  CampaignCause.DROITS_CITOYENNETE,
];

/** Emoji of each displayed cause (site mockup), kept in code rather than in `fr.json`. */
export const CAUSE_EMOJI: Record<CampaignCause, string> = {
  SOLIDARITE: '🌱',
  ALIMENTATION: '🍎',
  SANTE: '🏥',
  ENFANCE_EDUCATION: '📚',
  ANIMAUX: '🐾',
  HANDICAP: '♿',
  ENVIRONNEMENT: '🌍',
  CULTURE: '🎨',
  SPORT: '⚽',
  DROITS_CITOYENNETE: '⚖️',
  AUTRE: '',
};

/** Cross "Voir aussi" links between neighbouring causes (Asana discovery-filters spec). */
export const SEE_ALSO: Partial<Record<CampaignCause, readonly CampaignCause[]>> = {
  ALIMENTATION: [CampaignCause.SOLIDARITE, CampaignCause.ENVIRONNEMENT],
  SOLIDARITE: [CampaignCause.ALIMENTATION],
  ENVIRONNEMENT: [CampaignCause.ALIMENTATION],
};

/**
 * Whether a cause value can be shown publicly: a known cause other than `AUTRE`. Guards against
 * values this build does not know (API deployed ahead of the landing page).
 */
export function isDisplayedCause(value: string | null | undefined): value is CampaignCause {
  return value != null && (DISPLAYED_CAUSES as readonly string[]).includes(value);
}
