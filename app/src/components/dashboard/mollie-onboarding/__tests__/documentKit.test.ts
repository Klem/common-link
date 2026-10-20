import { describe, it, expect } from 'vitest';
import { KitRowKind, buildKitRows, kitProgress, onboardingPeople } from '../documentKit';
import { BeneficialOwnerType } from '@/types/admin';
import { VerificationDocType } from '@/types/verification';
import { MollieStructureVariant } from '@/types/mollie-connect';
import type { MollieOnboardingContext } from '@/types/mollie-connect';

const context = (overrides: Partial<MollieOnboardingContext> = {}): MollieOnboardingContext => ({
  structureVariant: MollieStructureVariant.DECLARED,
  verifiedAt: '2026-09-12T10:00:00Z',
  beneficialOwners: [],
  providedDocuments: [],
  siren: null,
  selfCompletionAttestedAt: null,
  ...overrides,
});

describe('onboardingPeople', () => {
  it('counts a person recorded as both beneficial owner and representative once, as representative', () => {
    const people = onboardingPeople([
      { name: 'Claire Martin', role: 'Présidente', type: BeneficialOwnerType.BENEFICIAL_OWNER },
      { name: ' claire  martin ', role: null, type: BeneficialOwnerType.REPRESENTATIVE },
      { name: 'Karim Benali', role: 'Trésorier', type: BeneficialOwnerType.BENEFICIAL_OWNER },
    ]);

    expect(people).toEqual([
      { name: 'Claire Martin', role: 'Présidente', type: BeneficialOwnerType.REPRESENTATIVE },
      { name: 'Karim Benali', role: 'Trésorier', type: BeneficialOwnerType.BENEFICIAL_OWNER },
    ]);
  });
});

describe('buildKitRows', () => {
  it('lists one identity row per person, then statutes, proof, bank details and the optional SIREN', () => {
    const rows = buildKitRows(
      context({
        beneficialOwners: [
          { name: 'A', role: null, type: BeneficialOwnerType.BENEFICIAL_OWNER },
          { name: 'B', role: null, type: BeneficialOwnerType.BENEFICIAL_OWNER },
        ],
      }),
    );

    expect(rows.map((r) => r.kind)).toEqual([
      KitRowKind.IDENTITY,
      KitRowKind.IDENTITY,
      KitRowKind.STATUTES,
      KitRowKind.PROOF,
      KitRowKind.RIB,
      KitRowKind.SIREN,
    ]);
    expect(rows.filter((r) => !r.required).map((r) => r.kind)).toEqual([KitRowKind.SIREN]);
  });

  it('attaches the provided statutes and proof of existence to their rows', () => {
    const rows = buildKitRows(
      context({
        providedDocuments: [
          { id: 'doc-statuts', type: VerificationDocType.VERIF_STATUTS },
          { id: 'doc-rna', type: VerificationDocType.VERIF_RNA_RECEIPT },
        ],
      }),
    );

    expect(rows.find((r) => r.kind === KitRowKind.STATUTES)?.providedDocumentId).toBe('doc-statuts');
    expect(rows.find((r) => r.kind === KitRowKind.PROOF)?.providedDocumentId).toBe('doc-rna');
    expect(rows.find((r) => r.kind === KitRowKind.RIB)?.providedDocumentId).toBeNull();
  });

  it('attaches the representative ID to the single legal representative only', () => {
    const rows = buildKitRows(
      context({
        beneficialOwners: [
          { name: 'Karim', role: null, type: BeneficialOwnerType.BENEFICIAL_OWNER },
          { name: 'Claire', role: null, type: BeneficialOwnerType.REPRESENTATIVE },
        ],
        providedDocuments: [{ id: 'doc-id', type: VerificationDocType.VERIF_REPRESENTATIVE_ID }],
      }),
    );

    const identity = rows.filter((r) => r.kind === KitRowKind.IDENTITY);
    expect(identity.map((r) => [r.person?.name, r.providedDocumentId])).toEqual([
      ['Karim', null],
      ['Claire', 'doc-id'],
    ]);
  });

  it('claims the representative ID for nobody when several representatives are recorded', () => {
    const rows = buildKitRows(
      context({
        beneficialOwners: [
          { name: 'Claire', role: null, type: BeneficialOwnerType.REPRESENTATIVE },
          { name: 'Sophie', role: null, type: BeneficialOwnerType.REPRESENTATIVE },
        ],
        providedDocuments: [{ id: 'doc-id', type: VerificationDocType.VERIF_REPRESENTATIVE_ID }],
      }),
    );

    expect(rows.every((r) => r.providedDocumentId === null)).toBe(true);
  });

  it('still lists the association documents when the context is unavailable', () => {
    expect(buildKitRows(null).map((r) => r.kind)).toEqual([
      KitRowKind.STATUTES,
      KitRowKind.PROOF,
      KitRowKind.RIB,
      KitRowKind.SIREN,
    ]);
  });
});

describe('kitProgress', () => {
  it('counts ticked required rows over required rows, SIREN excluded, with any number of people', () => {
    const rows = buildKitRows(
      context({ beneficialOwners: [{ name: 'Solo', role: null, type: BeneficialOwnerType.REPRESENTATIVE }] }),
    );

    expect(kitProgress(rows, {})).toEqual({ ready: 0, total: 4 });
    expect(kitProgress(rows, { 'identity-0': true, siren: true })).toEqual({ ready: 1, total: 4 });
  });

  it('reaches 6 required rows with the mockup example (3 people)', () => {
    const rows = buildKitRows(
      context({
        beneficialOwners: ['Claire', 'Karim', 'Sophie'].map((name) => ({
          name,
          role: null,
          type: BeneficialOwnerType.BENEFICIAL_OWNER,
        })),
      }),
    );

    expect(kitProgress(rows, {}).total).toBe(6);
  });
});
