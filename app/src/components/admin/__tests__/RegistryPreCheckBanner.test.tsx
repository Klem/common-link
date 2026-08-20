import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen } from '@testing-library/react';
import { RegistryPreCheckBanner } from '../RegistryPreCheckBanner';
import type { RegistryPreCheckDto } from '@/types/admin';
import { ScopeVerdict } from '@/types/admin';
import { getRegistryPreCheck } from '@/lib/api/admin';

vi.mock('next-intl', () => ({
  useTranslations: () => (key: string) => key,
  useLocale: () => 'fr',
}));

vi.mock('@/lib/api/admin', () => ({
  getRegistryPreCheck: vi.fn(),
  scanRegistryPreCheck: vi.fn(),
}));

const mockedGet = vi.mocked(getRegistryPreCheck);

/** An association declared with an RNA only — no SIREN was ever attributed to it. */
const rnaOnlyScan: RegistryPreCheckDto = {
  id: '2b9e5a01-6f4d-4c33-9a7e-4d4b3a1c7f10',
  associationExists: null,
  siren: null,
  rna: 'W751121684',
  legalCategory: null,
  scopeVerdict: ScopeVerdict.UNDETERMINED,
  etatAdministratif: null,
  joafeDeclarationFound: true,
  dissolutionDetected: false,
  bodaccProcedureFound: null,
  checkedAt: '2026-08-13T14:00:00Z',
  warnings: [],
  officers: [],
  rnaActive: true,
};

const renderBanner = async (scan: RegistryPreCheckDto) => {
  mockedGet.mockResolvedValue(scan);
  render(<RegistryPreCheckBanner associationId="a1" />);
  // Wait for the initial load to settle.
  expect(await screen.findByText('registryCheck.title')).toBeInTheDocument();
};

describe('RegistryPreCheckBanner', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  describe('cause of an unknown "association exists" answer', () => {
    it('reports an association absent from the business register as not listed, not as an outage', async () => {
      await renderBanner(rnaOnlyScan);

      // Recherche d'entreprises only lists SIREN-bearing entities: absence proves nothing,
      // and no source actually failed — no warning was recorded.
      expect(screen.getByText('registryCheck.status.notListed')).toBeInTheDocument();
      expect(screen.queryByText('registryCheck.error')).not.toBeInTheDocument();
    });

    it('explains the skipped SIREN-keyed checks by the missing SIREN, not by an outage', async () => {
      await renderBanner(rnaOnlyScan);

      expect(screen.getByText(/registryCheck\.skipped\.noSiren/)).toBeInTheDocument();
      expect(screen.queryByText(/registryCheck\.skipped\.unavailable/)).not.toBeInTheDocument();
    });

    it('reports a genuine source failure as unavailable', async () => {
      await renderBanner({
        ...rnaOnlyScan,
        warnings: ['recherche-entreprises: connect timeout'],
      });

      expect(screen.getByText('registryCheck.error')).toBeInTheDocument();
      expect(screen.queryByText('registryCheck.status.notListed')).not.toBeInTheDocument();
      expect(screen.getByText(/registryCheck\.skipped\.unavailable/)).toBeInTheDocument();
    });
  });

  describe('legal category behind the perimeter verdict', () => {
    it('shows the INSEE code and its label', async () => {
      await renderBanner({
        ...rnaOnlyScan,
        siren: '339863417',
        associationExists: true,
        legalCategory: '9230',
        scopeVerdict: ScopeVerdict.IN_SCOPE,
      });

      expect(screen.getByText('9230')).toBeInTheDocument();
      expect(screen.getByText(/registry\.legalCategory\.codes\.9230/)).toBeInTheDocument();
    });

    it('shows an unlabelled code raw rather than mislabelling it', async () => {
      await renderBanner({
        ...rnaOnlyScan,
        siren: '123456789',
        associationExists: false,
        legalCategory: '5710',
        scopeVerdict: ScopeVerdict.OUT_OF_SCOPE,
      });

      expect(screen.getByText('5710')).toBeInTheDocument();
      expect(screen.queryByText(/registry\.legalCategory\.codes\./)).not.toBeInTheDocument();
    });

    it('states the accepted forms when the association is not in scope', async () => {
      await renderBanner(rnaOnlyScan);

      expect(screen.getByText('registry.scopeVerdict.accepted')).toBeInTheDocument();
      expect(screen.getByText('registry.scopeVerdict.undeterminedHint')).toBeInTheDocument();
    });

    it('reports a missing category explicitly instead of leaving the verdict unexplained', async () => {
      await renderBanner(rnaOnlyScan);

      expect(screen.getByText('registry.legalCategory.unknown')).toBeInTheDocument();
    });
  });
});
