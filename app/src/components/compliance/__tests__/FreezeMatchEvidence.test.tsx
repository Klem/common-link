import { describe, it, expect, vi } from 'vitest';
import { render, screen } from '@testing-library/react';
import { FreezeMatchEvidence } from '../FreezeMatchEvidence';
import type { FreezeScreeningMatchDto, SubjectRegistryDto } from '@/types/compliance';

vi.mock('next-intl', () => ({
  useTranslations: () => (key: string) => key,
}));

const technolab: FreezeScreeningMatchDto = {
  subjectType: 'ASSOCIATION',
  subjectId: '0e35c813-6f20-4b65-aa3f-c19bef6055c7',
  screenedNormalizedName: 'TECHNO',
  sanctionedIdRegistre: 1776,
  matchedName: 'TECHNOLAB',
  matchedNature: 'LEGAL_ENTITY',
  matchedLegalReference: '(UE) 2026/509 du 23/04/2026 (UE Ukraine intégrité territoriale)',
  matchedDateOfBirth: null,
  score: 0.9333333333333333,
  scoreThreshold: 0.85,
  algorithm: 'JARO_WINKLER',
  registryPublicationDate: '2026-08-12',
};

const secondMatch: FreezeScreeningMatchDto = {
  ...technolab,
  sanctionedIdRegistre: 3196,
  matchedName: 'Technology and Development Group limited',
  score: 0.87,
};

const registry: SubjectRegistryDto = {
  siren: '812345678',
  rna: 'W751234567',
  scopeVerdict: 'IN_SCOPE',
  associationExists: true,
  rnaActive: true,
  checkedAt: '2026-08-13T08:00:00Z',
};

function renderEvidence(overrides: Partial<React.ComponentProps<typeof FreezeMatchEvidence>> = {}) {
  return render(
    <FreezeMatchEvidence
      matches={[technolab]}
      subjectLabel="TECHNO +"
      subjectId="0e35c813-6f20-4b65-aa3f-c19bef6055c7"
      subjectRegistry={registry}
      locale="fr"
      {...overrides}
    />,
  );
}

describe('FreezeMatchEvidence', () => {
  it('names the register entry that was matched', () => {
    renderEvidence();
    expect(screen.getByText('TECHNOLAB')).toBeInTheDocument();
    expect(screen.getByText('1776')).toBeInTheDocument();
  });

  it('shows the sanctions programme, which is what a false-positive ruling rests on', () => {
    renderEvidence();
    expect(
      screen.getByText('(UE) 2026/509 du 23/04/2026 (UE Ukraine intégrité territoriale)'),
    ).toBeInTheDocument();
  });

  // Displaying "TECHNO +" alone would leave the 0.9333 unexplained: it is the normalized
  // "TECHNO" that was actually compared against "TECHNOLAB".
  it('shows the normalized value that produced the score, not just the raw name', () => {
    renderEvidence();
    expect(screen.getByText('TECHNO')).toBeInTheDocument();
  });

  it('rounds the score rather than showing raw Jaro-Winkler precision', () => {
    renderEvidence();
    expect(screen.getByText('0.9333')).toBeInTheDocument();
    expect(screen.queryByText('0.9333333333333333')).not.toBeInTheDocument();
  });

  it('shows the registry identity that discriminates the subject from the register entry', () => {
    renderEvidence();
    expect(screen.getByText('812345678')).toBeInTheDocument();
    expect(screen.getByText('W751234567')).toBeInTheDocument();
  });

  it('lists every correspondence, not only the top-scoring one', () => {
    renderEvidence({ matches: [technolab, secondMatch] });
    expect(screen.getByText('TECHNOLAB')).toBeInTheDocument();
    expect(screen.getByText('Technology and Development Group limited')).toBeInTheDocument();
  });

  // An onboarding alert is carried by the association but covers several screened parties: the
  // association, its registry officers, its legal representatives, its beneficial owners. Listed
  // flat under one "value compared" heading, a person-name match would read as if it had scored
  // against the association's name.
  describe('when one alert covers several screened parties', () => {
    const representativeMatch: FreezeScreeningMatchDto = {
      ...technolab,
      subjectType: 'REPRESENTATIVE',
      subjectId: '7b3e82fe-0000-0000-0000-000000000001',
      screenedNormalizedName: 'CLAMENT P',
      sanctionedIdRegistre: 5010,
      matchedName: 'ABDI Abbas',
      matchedNature: 'PHYSICAL_PERSON',
      matchedDateOfBirth: '1962',
      score: 0.86,
    };

    it('shows each screened value, not only the first', () => {
      renderEvidence({ matches: [technolab, representativeMatch] });
      expect(screen.getByText('TECHNO')).toBeInTheDocument();
      expect(screen.getByText('CLAMENT P')).toBeInTheDocument();
    });

    it('labels the party each screened value belongs to', () => {
      renderEvidence({ matches: [technolab, representativeMatch] });
      expect(screen.getByText('alerts.subjectType.ASSOCIATION')).toBeInTheDocument();
      expect(screen.getByText('alerts.subjectType.REPRESENTATIVE')).toBeInTheDocument();
    });

    it('keeps each correspondence under its own screened party', () => {
      renderEvidence({ matches: [technolab, representativeMatch] });
      // One table per screened party rather than a single flat list.
      expect(screen.getAllByRole('table')).toHaveLength(2);
    });

    it('groups several correspondences of the same party into one block', () => {
      renderEvidence({ matches: [technolab, secondMatch, representativeMatch] });
      expect(screen.getAllByRole('table')).toHaveLength(2);
      expect(screen.getAllByText('TECHNO')).toHaveLength(1);
    });
  });

  // A SCREENING_UNAVAILABLE alert has no comparison behind it.
  it('renders an explicit empty state when no correspondence was recorded', () => {
    renderEvidence({ matches: [] });
    expect(screen.getByText('evidence.empty')).toBeInTheDocument();
    expect(screen.queryByText('TECHNOLAB')).not.toBeInTheDocument();
  });

  it('degrades gracefully when the subject dossier no longer resolves', () => {
    renderEvidence({ subjectLabel: null, subjectRegistry: null });
    expect(screen.getAllByText('evidence.subjectUnresolved').length).toBeGreaterThan(0);
    expect(screen.getByText('0e35c813-6f20-4b65-aa3f-c19bef6055c7')).toBeInTheDocument();
  });
});
