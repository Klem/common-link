import { useCallback } from 'react';
import { useTranslations } from 'next-intl';
import { CampaignCause } from '@/types/campaign';

/**
 * Display order of causes (Asana discovery-filters table). `AUTRE` comes last.
 */
export const CAMPAIGN_CAUSES: readonly CampaignCause[] = [
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
  CampaignCause.AUTRE,
];

/**
 * Emoji of each cause, aligned on the CommonLink UI mockup. Kept in code because the i18n JSON
 * files carry no emojis. `AUTRE` has none.
 */
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

/**
 * Returns a formatter turning a cause into its translated label, prefixed with its emoji
 * (e.g. `🍎 Alimentation`). Unknown values (stale cache, older API) are rendered as-is.
 */
export function useCauseLabel(): (cause: CampaignCause) => string {
  const t = useTranslations('common.campaignCause');
  return useCallback(
    (cause: CampaignCause) => {
      if (!(cause in CAUSE_EMOJI)) return cause;
      const emoji = CAUSE_EMOJI[cause];
      return emoji ? `${emoji} ${t(cause)}` : t(cause);
    },
    [t],
  );
}
