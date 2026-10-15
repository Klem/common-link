import { EmbedType } from '@/lib/gtm';
import type { AssociationProfileDto } from '@/types/association';

/** Where a diffusion method's page lives: on CommonLink, or on the association's own site. */
export const MethodGroup = {
  COMMONLINK: 'commonlink',
  SITE: 'site',
} as const;
export type MethodGroup = (typeof MethodGroup)[keyof typeof MethodGroup];

/**
 * Static facts shown on a method card, each one an i18n key under
 * `settings.diffusion.methods.factValues`.
 */
export interface MethodFacts {
  returnTo: string;
  gtm: string;
  update: string;
  consent: string;
}

export interface DiffusionMethod {
  id: EmbedType;
  group: MethodGroup;
  /** Whether the method carries a highlight tag (`methods.<id>.tag`). */
  hasTag: boolean;
  facts: MethodFacts;
}

const SITE_FACTS: MethodFacts = {
  returnTo: 'returnSite',
  gtm: 'gtmSite',
  update: 'updateAuto',
  consent: 'consentYours',
};

/**
 * The diffusion methods offered in the settings tab, in display order. The ids are the `embed_type`
 * values, which the backend also stores as the association's pick (`AssociationProfile.embedType`).
 */
export const DIFFUSION_METHODS: readonly DiffusionMethod[] = [
  {
    id: EmbedType.DIRECT,
    group: MethodGroup.COMMONLINK,
    hasTag: true,
    facts: { returnTo: 'returnCommonLink', gtm: 'gtmCommonLink', update: 'updateAuto', consent: 'consentCommonLink' },
  },
  { id: EmbedType.SCRIPT, group: MethodGroup.SITE, hasTag: true, facts: SITE_FACTS },
  { id: EmbedType.IFRAME, group: MethodGroup.SITE, hasTag: false, facts: SITE_FACTS },
  {
    id: EmbedType.HTML,
    group: MethodGroup.SITE,
    hasTag: false,
    facts: { returnTo: 'returnSite', gtm: 'gtmFile', update: 'updateManual', consent: 'consentFile' },
  },
  { id: EmbedType.WIDGET, group: MethodGroup.SITE, hasTag: false, facts: SITE_FACTS },
];

/**
 * Method shown when the association never picked one: the recommended script embed, or the direct
 * link while no campaign page URL is set (every embedded method needs one to bring donors back).
 */
export function initialMethod(profile: AssociationProfileDto | null): EmbedType {
  if (profile?.embedType) return profile.embedType;
  return profile?.widgetAllowedOrigin ? EmbedType.SCRIPT : EmbedType.DIRECT;
}

/**
 * Whether CommonLink itself loads the association's GTM container for this method (direct link and
 * standalone export) — as opposed to relaying the events to a host page that must load it.
 */
export function isGtmLoadedByCommonLink(method: EmbedType): boolean {
  return method === EmbedType.DIRECT || method === EmbedType.HTML;
}
