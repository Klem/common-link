/** Minimal shape the impact gallery and the campaign report page both need to share a card. */
export interface ShareableImpact {
  campaignId: string;
  campaignName: string;
  campaignEmoji: string;
  associationName: string;
  impactGoals?: string | null;
  /** Plain text — never the story's rich-text HTML, which this wording is never allowed to render. */
  storySummary: string | null;
}

/**
 * The one wording this gallery/share card is allowed to render — never a donor share or a
 * percentage (D6, option A). Prefers the association's published story summary over `impactGoals`.
 */
export function impactWording(impact: Pick<ShareableImpact, 'storySummary' | 'impactGoals'>): string | null {
  return impact.storySummary ?? impact.impactGoals ?? null;
}

/**
 * Ensures free text ends with terminal punctuation, without ever doubling it up.
 */
export function withTerminalPunctuation(text: string): string {
  const trimmed = text.trim();
  return /[.!?…]$/.test(trimmed) ? trimmed : `${trimmed}.`;
}
