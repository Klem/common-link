import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, fireEvent, cleanup, waitFor } from '@testing-library/react';
import { NextIntlClientProvider } from 'next-intl';
import MollieOnboardingFlow from '../MollieOnboardingFlow';
import type { UseMollieKycStatusReturn } from '@/hooks/mollie/useMollieKycStatus';
import type { AssociationProfileDto } from '@/types/association';
import { MollieOnboardingStatus, MollieStructureVariant } from '@/types/mollie-connect';
import { BeneficialOwnerType } from '@/types/admin';
import { VerificationDocType } from '@/types/verification';
import fr from '../../../../../messages/fr.json';
import en from '../../../../../messages/en.json';

// Real messages, no next-intl mock: catches missing keys, dynamic-key typos and bad ICU / rich-text syntax.

const { mockGetContext } = vi.hoisted(() => ({ mockGetContext: vi.fn() }));
vi.mock('@/lib/api/mollie-connect', () => ({ getMollieOnboardingContext: mockGetContext, attestMollieSelfCompletion: vi.fn() }));
vi.mock('@/lib/api/association-documents', () => ({ downloadOwnAssociationDocument: vi.fn() }));

const kyc: UseMollieKycStatusReturn = {
  connected: true,
  pending: false,
  broken: false,
  onboardingStatus: MollieOnboardingStatus.NEEDS_DATA,
  canReceivePayments: false,
  dashboardUrl: 'https://my.mollie.com/dashboard/onboarding',
  canForceComplete: false,
  complementRequested: false,
  lastUpdatedAt: new Date(2026, 9, 12, 9, 41),
  isLoading: false,
  error: null,
  refresh: vi.fn().mockResolvedValue(undefined),
  forceRefresh: vi.fn().mockResolvedValue(undefined),
};

const profile = { name: 'Les Amis du Jardin Partagé', identifier: 'W691012345' } as AssociationProfileDto;

/** A raw i18n key leaking into the page (missing message or dynamic-key typo). */
const RAW_KEY = /settings\.mollie|onboarding\.(welcome|structure|kit|stepper|fill|testPayment|review|active|notices|broken|help|loading)|guide\.(steps|values)/;

describe.each([
  ['fr', fr],
  ['en', en],
])('MollieOnboardingFlow with the real %s messages', (locale, messages) => {
  beforeEach(() => mockGetContext.mockReset());

  it.each(Object.values(MollieStructureVariant))('renders screens 0 to 4 for %s without any missing message', async (variant) => {
    mockGetContext.mockResolvedValue({
      structureVariant: variant,
      verifiedAt: '2026-09-12T10:00:00Z',
      beneficialOwners: [
        { name: 'Claire Martin', role: 'Présidente', type: BeneficialOwnerType.REPRESENTATIVE },
        { name: 'Karim Benali', role: null, type: BeneficialOwnerType.BENEFICIAL_OWNER },
      ],
      providedDocuments: [{ id: 'doc-statuts', type: VerificationDocType.VERIF_STATUTS }],
      siren: '912345678',
      selfCompletionAttestedAt: null,
    });
    const onError = vi.fn();
    const assertNoRawKey = () => expect(document.body.textContent).not.toMatch(RAW_KEY);

    render(
      <NextIntlClientProvider locale={locale} messages={messages} onError={onError}>
        <MollieOnboardingFlow
          kyc={kyc}
          profile={profile}
          bankDone={false}
          interrupted={false}
          onConnect={vi.fn()}
          onReconnect={vi.fn()}
          onRetryConnect={vi.fn()}
          onLearnMore={vi.fn()}
          onForceComplete={vi.fn()}
        />
      </NextIntlClientProvider>,
    );

    // Screen 0: open every FAQ answer.
    screen.getAllByRole('button', { expanded: false }).forEach((button) => fireEvent.click(button));
    assertNoRawKey();
    fireEvent.click(screen.getByRole('button', { name: messages.settings.mollie.onboarding.welcome.start }));

    // Screen 1.
    await screen.findByText('Claire Martin');
    assertNoRawKey();
    fireEvent.click(screen.getByRole('button', { name: messages.settings.mollie.onboarding.structure.continue }));

    // Screen 2: tick one box, then trigger the soft warning (plural).
    fireEvent.click(screen.getAllByRole('checkbox')[0]);
    fireEvent.click(screen.getByRole('button', { name: messages.settings.mollie.onboarding.kit.openMollie }));
    expect(screen.getByRole('alert')).toBeInTheDocument();
    assertNoRawKey();

    // Screen 3: open every translator step (plurals, rich text, copy rows).
    const open = vi.spyOn(window, 'open').mockReturnValue(null);
    fireEvent.click(screen.getByRole('button', { name: messages.settings.mollie.onboarding.kit.softWarning.openAnyway }));
    screen.getAllByRole('button', { expanded: false }).forEach((button) => fireEvent.click(button));
    assertNoRawKey();

    // Screen 4, then the local acknowledgement.
    fireEvent.click(screen.getByRole('button', { name: messages.settings.mollie.onboarding.fill.next }));
    fireEvent.click(screen.getByRole('button', { name: messages.settings.mollie.onboarding.testPayment.ack }));
    expect(screen.getByText(messages.settings.mollie.onboarding.testPayment.acked)).toBeInTheDocument();
    assertNoRawKey();

    expect(onError).not.toHaveBeenCalled();
    open.mockRestore();
    cleanup();
    // Walks five screens with the real messages: generous timeout for a loaded CI/dev machine.
  }, 15_000);

  it.each([
    ['in review', { onboardingStatus: MollieOnboardingStatus.IN_REVIEW }, false, false],
    ['complement requested', { complementRequested: true }, false, false],
    ['active', { onboardingStatus: MollieOnboardingStatus.COMPLETED, canReceivePayments: true }, true, false],
    ['active without payments', { onboardingStatus: MollieOnboardingStatus.COMPLETED }, false, false],
    ['broken', { broken: true }, false, false],
    ['loading', { isLoading: true }, false, false],
    ['interrupted', { connected: false, onboardingStatus: null }, false, true],
    ['pending', { connected: false, onboardingStatus: null, pending: true }, false, false],
  ] as const)('renders the %s state without any missing message', async (_, overrides, bankDone, interrupted) => {
    mockGetContext.mockResolvedValue({
      structureVariant: MollieStructureVariant.DECLARED,
      verifiedAt: null,
      beneficialOwners: [],
      providedDocuments: [],
      siren: null,
      selfCompletionAttestedAt: null,
    });
    const onError = vi.fn();
    render(
      <NextIntlClientProvider locale={locale} messages={messages} onError={onError}>
        <MollieOnboardingFlow
          kyc={{ ...kyc, ...overrides }}
          profile={profile}
          bankDone={bankDone}
          interrupted={interrupted}
          onConnect={vi.fn()}
          onReconnect={vi.fn()}
          onRetryConnect={vi.fn()}
          onForceComplete={vi.fn()}
        />
      </NextIntlClientProvider>,
    );
    await waitFor(() => expect(document.body.textContent).not.toBe(''));

    expect(document.body.textContent).not.toMatch(RAW_KEY);
    expect(onError).not.toHaveBeenCalled();
    cleanup();
  });

  it('never calls the test payment free (D11)', () => {
    const testPayment = JSON.stringify([messages.settings.mollie.onboarding.testPayment, messages.settings.mollie.onboarding.review]);
    expect(testPayment).not.toMatch(/gratuit|free of charge|\bfree\b/i);
  });
});
