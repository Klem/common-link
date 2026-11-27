import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import { renderHook, act } from '@testing-library/react';
import { useAssociationLookup } from '../useAssociationLookup';

/** Builds a minimal fetch Response carrying a Recherche d'entreprises payload. */
const registryResponse = (results: unknown[]): Response =>
  ({ ok: true, status: 200, json: async () => ({ results }) }) as unknown as Response;

const SIREN = '402328694';

/** Runs a full lookup for {@link SIREN} against the mocked registry. */
const runLookup = async () => {
  const { result } = renderHook(() => useAssociationLookup());
  act(() => result.current.setSiren(SIREN));
  await act(async () => {
    await result.current.lookup();
  });
  return result;
};

describe('useAssociationLookup — RNA mapping', () => {
  const fetchMock = vi.fn();

  beforeEach(() => {
    fetchMock.mockReset();
    vi.stubGlobal('fetch', fetchMock);
  });

  afterEach(() => {
    vi.unstubAllGlobals();
  });

  it('reads the RNA from complements, where the live API exposes it', async () => {
    fetchMock.mockResolvedValue(
      registryResponse([
        {
          siren: SIREN,
          nom_raison_sociale: 'MY ASSO',
          etat_administratif: 'A',
          complements: { est_association: true, identifiant_association: 'W061006975' },
        },
      ]),
    );

    const result = await runLookup();

    expect(result.current.result?.rna).toBe('W061006975');
    expect(result.current.result?.isAssociation).toBe(true);
  });

  it('falls back to a root-level RNA when complements carries none', async () => {
    fetchMock.mockResolvedValue(
      registryResponse([
        {
          siren: SIREN,
          nom_raison_sociale: 'MY ASSO',
          identifiant_association: 'W751234567',
          complements: { est_association: true },
        },
      ]),
    );

    const result = await runLookup();

    expect(result.current.result?.rna).toBe('W751234567');
  });

  it('reports a null RNA when the registry exposes none', async () => {
    fetchMock.mockResolvedValue(
      registryResponse([{ siren: SIREN, nom_raison_sociale: 'MY COMPANY', complements: {} }]),
    );

    const result = await runLookup();

    expect(result.current.result?.rna).toBeNull();
    expect(result.current.error).toBeNull();
  });
});
