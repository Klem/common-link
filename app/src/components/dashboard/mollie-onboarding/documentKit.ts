import { BeneficialOwnerType } from '@/types/admin';
import { VerificationDocType } from '@/types/verification';
import { MollieStructureVariant } from '@/types/mollie-connect';
import type { MollieOnboardingContext, OnboardingBeneficialOwner } from '@/types/mollie-connect';

/**
 * Per-variant content of the "Votre structure" card and of the kit's proof-of-existence row
 * (mockup `VARIANTS`). Single mapping point variant → documents: screens read nothing else.
 *
 * - `key`: i18n segment under `settings.mollie.onboarding.structure.types` and
 *   `settings.mollie.onboarding.kit.proof.variants`.
 * - `link`: official source quoted in the "where to find it" text, null when there is none.
 */
export const STRUCTURE_KIT = {
  [MollieStructureVariant.DECLARED]: { key: 'declared', link: 'https://www.journal-officiel.gouv.fr' },
  [MollieStructureVariant.ARUP]: { key: 'arup', link: 'https://www.legifrance.gouv.fr' },
  // TODO(sprint-20 open point): AMALIA portal URL not confirmed yet — the text names the portal without a link.
  [MollieStructureVariant.ALSACE_MOSELLE]: { key: 'alsaceMoselle', link: null },
  [MollieStructureVariant.UNKNOWN]: { key: 'unknown', link: null },
} as const;

/** Kinds of rows in the document kit (screen 2), in display order after the identity rows. */
export const KitRowKind = {
  IDENTITY: 'IDENTITY',
  STATUTES: 'STATUTES',
  PROOF: 'PROOF',
  RIB: 'RIB',
  SIREN: 'SIREN',
} as const;
export type KitRowKind = typeof KitRowKind[keyof typeof KitRowKind];

/** One row of the document kit. */
export interface KitRow {
  /** Stable id, used as the key of the local "Je l'ai" tick state. */
  id: string;
  kind: KitRowKind;
  /** Required rows count in the progress bar; only the SIREN row is optional. */
  required: boolean;
  /** Id of the matching document already provided to CommonLink, re-downloadable; null when none. */
  providedDocumentId: string | null;
  /** The person whose identity document this row asks for (identity rows only). */
  person?: OnboardingBeneficialOwner;
}

/** Collapses whitespace and case so a person recorded twice (e.g. as BE and as representative) counts once. */
function personKey(name: string): string {
  return name.trim().replace(/\s+/g, ' ').toLocaleLowerCase('fr');
}

/**
 * People whose identity document Mollie will ask for: the confirmed, non-discarded beneficial
 * owners AND legal representatives of the KYB dossier (for an association, every administrator or
 * officer is a beneficial owner, art. R. 561-3 CMF), deduplicated by name.
 *
 * When the same name appears with both types, the entry is kept once and typed `REPRESENTATIVE`.
 *
 * @param owners the onboarding context's `beneficialOwners`, in server order.
 * @return the deduplicated people, first-occurrence order.
 */
export function onboardingPeople(owners: OnboardingBeneficialOwner[]): OnboardingBeneficialOwner[] {
  const byKey = new Map<string, OnboardingBeneficialOwner>();
  for (const owner of owners) {
    const key = personKey(owner.name);
    if (!key) continue;
    const existing = byKey.get(key);
    if (!existing) {
      byKey.set(key, { ...owner, name: owner.name.trim() });
    } else {
      byKey.set(key, {
        ...existing,
        role: existing.role ?? owner.role,
        type: existing.type === BeneficialOwnerType.REPRESENTATIVE ? existing.type : owner.type,
      });
    }
  }
  return [...byKey.values()];
}

/**
 * Builds the document kit rows (screen 2): one identity row per person, then statutes, proof of
 * existence, bank details and the optional SIREN.
 *
 * Provided documents: `VERIF_STATUTS` → statutes row, `VERIF_RNA_RECEIPT` → proof row (all three
 * variants), `VERIF_REPRESENTATIVE_ID` → the identity row of the legal representative, but only
 * when exactly one representative is recorded — otherwise we cannot tell whose ID it is, and no
 * identity row claims it.
 *
 * @param context the onboarding context, or null when it could not be loaded.
 * @return the kit rows, in display order.
 */
export function buildKitRows(context: MollieOnboardingContext | null): KitRow[] {
  const people = onboardingPeople(context?.beneficialOwners ?? []);
  const documentId = (type: VerificationDocType): string | null =>
    context?.providedDocuments.find((doc) => doc.type === type)?.id ?? null;

  const representatives = people.filter((person) => person.type === BeneficialOwnerType.REPRESENTATIVE);
  const representativeIdDoc = representatives.length === 1 ? documentId(VerificationDocType.VERIF_REPRESENTATIVE_ID) : null;

  const identityRows: KitRow[] = people.map((person, index) => ({
    id: `identity-${index}`,
    kind: KitRowKind.IDENTITY,
    required: true,
    providedDocumentId: person === representatives[0] ? representativeIdDoc : null,
    person,
  }));

  return [
    ...identityRows,
    { id: 'statutes', kind: KitRowKind.STATUTES, required: true, providedDocumentId: documentId(VerificationDocType.VERIF_STATUTS) },
    { id: 'proof', kind: KitRowKind.PROOF, required: true, providedDocumentId: documentId(VerificationDocType.VERIF_RNA_RECEIPT) },
    { id: 'rib', kind: KitRowKind.RIB, required: true, providedDocumentId: null },
    { id: 'siren', kind: KitRowKind.SIREN, required: false, providedDocumentId: null },
  ];
}

/**
 * Kit preparation progress: ticked required rows over required rows (the optional SIREN excluded).
 *
 * The mockup shows "/ 6" because its example has 3 people + statutes + proof + bank details; the
 * total follows the actual number of identity rows.
 *
 * @param rows kit rows from [buildKitRows].
 * @param ticked local "Je l'ai" state, by row id.
 * @return ready and total counts.
 */
export function kitProgress(rows: KitRow[], ticked: Record<string, boolean>): { ready: number; total: number } {
  const required = rows.filter((row) => row.required);
  return { ready: required.filter((row) => ticked[row.id]).length, total: required.length };
}
