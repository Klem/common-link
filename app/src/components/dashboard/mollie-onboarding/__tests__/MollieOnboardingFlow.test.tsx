import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import { render, screen, fireEvent, waitFor, act } from '@testing-library/react';
import MollieOnboardingFlow from '../MollieOnboardingFlow';
import type { UseMollieKycStatusReturn } from '@/hooks/mollie/useMollieKycStatus';
import type { AssociationProfileDto } from '@/types/association';
import { MollieOnboardingStatus, MollieStructureVariant } from '@/types/mollie-connect';
import type { MollieOnboardingContext } from '@/types/mollie-connect';
import { BeneficialOwnerType } from '@/types/admin';
import { VerificationDocType } from '@/types/verification';

// Translator mock: "key" or "key {json of non-function values}" so counts and names are assertable.
vi.mock('next-intl', () => {
  const format = (key: string, values?: Record<string, unknown>) => {
    const plain = Object.fromEntries(Object.entries(values ?? {}).filter(([, v]) => typeof v !== 'function'));
    return Object.keys(plain).length ? `${key} ${JSON.stringify(plain)}` : key;
  };
  const t = Object.assign((key: string, values?: Record<string, unknown>) => format(key, values), {
    rich: (key: string, values?: Record<string, unknown>) => format(key, values),
  });
  return { useTranslations: () => t, useLocale: () => 'fr' };
});

const { mockGetContext, mockDownload, mockAttest } = vi.hoisted(() => ({
  mockGetContext: vi.fn(),
  mockDownload: vi.fn(),
  mockAttest: vi.fn(),
}));
vi.mock('@/lib/api/mollie-connect', () => ({
  getMollieOnboardingContext: mockGetContext,
  attestMollieSelfCompletion: mockAttest,
}));
vi.mock('@/lib/api/association-documents', () => ({ downloadOwnAssociationDocument: mockDownload }));
vi.mock('@/hooks/ui/useCopyToClipboard', () => ({ useCopyToClipboard: () => ({ copied: false, copy: vi.fn() }) }));

const kyc = (overrides: Partial<UseMollieKycStatusReturn> = {}): UseMollieKycStatusReturn => ({
  connected: false,
  pending: false,
  broken: false,
  onboardingStatus: null,
  canReceivePayments: null,
  dashboardUrl: null,
  canForceComplete: false,
  complementRequested: false,
  lastUpdatedAt: null,
  isLoading: false,
  error: null,
  refresh: vi.fn().mockResolvedValue(undefined),
  forceRefresh: vi.fn().mockResolvedValue(undefined),
  ...overrides,
});

const needsData = (overrides: Partial<UseMollieKycStatusReturn> = {}) =>
  kyc({
    connected: true,
    onboardingStatus: MollieOnboardingStatus.NEEDS_DATA,
    canReceivePayments: false,
    dashboardUrl: 'https://my.mollie.com/dashboard/onboarding',
    ...overrides,
  });

const profile = { name: 'Les Amis du Jardin Partagé', identifier: 'W691012345' } as AssociationProfileDto;

const context = (overrides: Partial<MollieOnboardingContext> = {}): MollieOnboardingContext => ({
  structureVariant: MollieStructureVariant.DECLARED,
  verifiedAt: '2026-09-12T10:00:00Z',
  beneficialOwners: [
    { name: 'Claire Martin', role: 'Présidente', type: BeneficialOwnerType.REPRESENTATIVE },
    { name: 'Karim Benali', role: 'Trésorier', type: BeneficialOwnerType.BENEFICIAL_OWNER },
    { name: 'Sophie Leroy', role: 'Secrétaire', type: BeneficialOwnerType.BENEFICIAL_OWNER },
  ],
  providedDocuments: [
    { id: 'doc-statuts', type: VerificationDocType.VERIF_STATUTS },
    { id: 'doc-rna', type: VerificationDocType.VERIF_RNA_RECEIPT },
    { id: 'doc-id', type: VerificationDocType.VERIF_REPRESENTATIVE_ID },
  ],
  siren: '912345678',
  selfCompletionAttestedAt: null,
  ...overrides,
});

function renderFlow(props: Partial<React.ComponentProps<typeof MollieOnboardingFlow>> = {}) {
  const handlers = {
    onConnect: vi.fn(),
    onReconnect: vi.fn(),
    onRetryConnect: vi.fn(),
    onLearnMore: vi.fn(),
    onForceComplete: vi.fn(),
    onOpenTab: vi.fn(),
  };
  const view = render(
    <MollieOnboardingFlow kyc={kyc()} profile={profile} bankDone={false} interrupted={false} {...handlers} {...props} />,
  );
  return { ...handlers, unmount: view.unmount };
}

/** Walks from screen 0 to the given screen through the flow's own buttons. */
async function goToScreen(target: 1 | 2) {
  fireEvent.click(screen.getByRole('button', { name: 'start' }));
  await screen.findByRole('button', { name: 'continue' });
  if (target === 2) fireEvent.click(screen.getByRole('button', { name: 'continue' }));
}

describe('MollieOnboardingFlow — last visited screen', () => {
  const withId = { ...profile, id: 'asso-1' } as AssociationProfileDto;

  beforeEach(() => {
    window.localStorage.clear();
    mockGetContext.mockReset().mockResolvedValue(context());
  });
  afterEach(() => window.localStorage.clear());

  it('reopens on the last visited screen after leaving and coming back to the tab', async () => {
    const first = renderFlow({ profile: withId });
    await goToScreen(2);
    first.unmount();

    renderFlow({ profile: withId });
    expect(screen.queryByRole('button', { name: 'start' })).not.toBeInTheDocument();
    expect(screen.getByRole('listitem', { current: 'step' })).toBeInTheDocument();
    expect(await screen.findByRole('button', { name: 'back' })).toBeInTheDocument();
  });

  it('holds the user on screen 3 without a Mollie connection, with an explanation', () => {
    window.localStorage.setItem('cl.mollieOnboarding.screen.asso-1', '4');
    renderFlow({ profile: withId });

    expect(screen.queryByText('testPayment.title')).not.toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'next' })).toBeDisabled();
    expect(screen.getByText('nextBlocked')).toBeInTheDocument();
  });

  it('starts on screen 0 for an association with no stored screen, or an invalid one', () => {
    window.localStorage.setItem('cl.mollieOnboarding.screen.asso-1', '99');
    renderFlow({ profile: withId });
    expect(screen.getByRole('button', { name: 'start' })).toBeInTheDocument();
  });
});

describe('MollieOnboardingFlow — preparation screens (S3)', () => {
  beforeEach(() => {
    mockGetContext.mockReset().mockResolvedValue(context());
    mockDownload.mockReset().mockResolvedValue(undefined);
  });

  it('starts on screen 0 with the stepper on "Préparer" and the six FAQ questions', () => {
    renderFlow();

    const current = screen.getByRole('listitem', { current: 'step' });
    expect(current).toHaveAttribute('aria-label', 'state.current {"label":"steps.prepare"}');
    ['whyMollie', 'documents', 'cost', 'genuine', 'resume', 'help'].forEach((id) =>
      expect(screen.getByRole('button', { name: `faq.items.${id}.question` })).toBeInTheDocument(),
    );
    expect(screen.getByText('figures.estimate')).toBeInTheDocument();
  });

  it('opens a FAQ answer on click, with the imposed D11 wording key', () => {
    renderFlow();

    const trigger = screen.getByRole('button', { name: 'faq.items.documents.question' });
    expect(trigger).toHaveAttribute('aria-expanded', 'false');
    fireEvent.click(trigger);
    expect(trigger).toHaveAttribute('aria-expanded', 'true');
    expect(screen.getByText('faq.items.documents.p1')).toBeVisible();
  });

  it('offers "Reprendre mon dossier" only when the Mollie onboarding needs data', () => {
    renderFlow({ kyc: needsData() });
    expect(screen.getByRole('link', { name: 'resume' })).toHaveAttribute('href', 'https://my.mollie.com/dashboard/onboarding');
  });

  it('hides "Reprendre mon dossier" before any Mollie connection', () => {
    renderFlow();
    expect(screen.queryByRole('link', { name: 'resume' })).not.toBeInTheDocument();
  });

  it.each([
    [MollieStructureVariant.DECLARED, 'declared'],
    [MollieStructureVariant.ARUP, 'arup'],
    [MollieStructureVariant.ALSACE_MOSELLE, 'alsaceMoselle'],
  ])('shows the %s variant on screen 1 and its proof of existence on screen 2', async (variant, key) => {
    mockGetContext.mockResolvedValue(context({ structureVariant: variant }));
    renderFlow();

    await goToScreen(1);
    expect(await screen.findByText(`types.${key}`)).toBeInTheDocument();
    expect(screen.queryByText('unknownHint')).not.toBeInTheDocument();

    fireEvent.click(screen.getByRole('button', { name: 'continue' }));
    expect(screen.getByText(`proof.variants.${key}.what`)).toBeInTheDocument();
  });

  it('quotes the RNA in the declared-association proof row', async () => {
    renderFlow();
    await goToScreen(2);
    expect(await screen.findByText('proof.variants.declared.whereWithRna {"rna":"W691012345"}')).toBeInTheDocument();
  });

  it('shows a sober state, without any selector, when the variant is unknown', async () => {
    mockGetContext.mockResolvedValue(context({ structureVariant: MollieStructureVariant.UNKNOWN }));
    renderFlow();

    await goToScreen(1);
    expect(await screen.findByText('types.unknown')).toBeInTheDocument();
    expect(screen.getByText('unknownHint')).toBeInTheDocument();
    expect(screen.queryByRole('combobox')).not.toBeInTheDocument();
    expect(screen.queryByRole('radio')).not.toBeInTheDocument();
  });

  it('lists the people and announces one identity document each', async () => {
    renderFlow();
    await goToScreen(1);

    expect(await screen.findByText('Claire Martin')).toBeInTheDocument();
    expect(screen.getByText('owners.count {"count":3}')).toBeInTheDocument();
    expect(screen.getByText('owners.idAlert {"count":3}')).toBeInTheDocument();
  });

  it('re-downloads a document already provided to CommonLink', async () => {
    renderFlow();
    await goToScreen(2);

    const buttons = await screen.findAllByRole('button', { name: 'download' });
    // Representative ID, statutes, proof of existence.
    expect(buttons).toHaveLength(3);
    fireEvent.click(buttons[1]);
    await waitFor(() => expect(mockDownload).toHaveBeenCalledWith('doc-statuts', 'commonlink-statutes'));
  });

  it('tracks the preparation progress over required rows only (SIREN excluded)', async () => {
    renderFlow();
    await goToScreen(2);

    expect(await screen.findByText('progress.label {"ready":0,"total":6}')).toBeInTheDocument();
    const boxes = screen.getAllByRole('checkbox');
    fireEvent.click(boxes[0]);
    fireEvent.click(boxes[boxes.length - 1]); // SIREN, optional
    expect(screen.getByText('progress.label {"ready":1,"total":6}')).toBeInTheDocument();
    expect(screen.getByRole('progressbar')).toHaveAttribute('aria-valuenow', '1');
  });

  it('warns without blocking when documents are missing, then opens Mollie on demand', async () => {
    const { onConnect } = renderFlow();
    await goToScreen(2);
    await screen.findAllByRole('checkbox');

    fireEvent.click(screen.getByRole('button', { name: 'openMollie' }));
    expect(onConnect).not.toHaveBeenCalled();
    expect(screen.getByRole('alert')).toHaveTextContent('softWarning.missing {"count":6}');

    fireEvent.click(screen.getByRole('button', { name: 'softWarning.openAnyway' }));
    expect(onConnect).toHaveBeenCalledTimes(1);
  });

  it('opens Mollie directly once every required document is ticked', async () => {
    const { onConnect } = renderFlow();
    await goToScreen(2);

    const boxes = await screen.findAllByRole('checkbox');
    boxes.slice(0, -1).forEach((box) => fireEvent.click(box));
    fireEvent.click(screen.getByRole('button', { name: 'openMollie' }));

    expect(onConnect).toHaveBeenCalledTimes(1);
    expect(screen.queryByRole('alert')).not.toBeInTheDocument();
  });

  it('opens the hosted Mollie onboarding when already connected, never a new connection', async () => {
    const open = vi.spyOn(window, 'open').mockReturnValue(null);
    const { onConnect } = renderFlow({ kyc: needsData() });
    await goToScreen(2);

    const boxes = await screen.findAllByRole('checkbox');
    boxes.forEach((box) => fireEvent.click(box));
    fireEvent.click(screen.getByRole('button', { name: 'openMollie' }));

    expect(open).toHaveBeenCalledWith('https://my.mollie.com/dashboard/onboarding', '_blank', 'noopener,noreferrer');
    expect(onConnect).not.toHaveBeenCalled();
    open.mockRestore();
  });

  it('keeps the screens usable when the onboarding context cannot be loaded', async () => {
    mockGetContext.mockRejectedValue(new Error('boom'));
    renderFlow();
    await goToScreen(1);

    expect(await screen.findByText('contextError')).toBeInTheDocument();
    fireEvent.click(screen.getByRole('button', { name: 'continue' }));
    expect(screen.getByText('statutes.title')).toBeInTheDocument();
  });
});

const VISIO = 'https://calendar.proton.me/bookings/guest#HwvqIxtLuDJOaPIrg1-JrTXq9Cj-N2Y79_u2UXr_MiQ=';

/** From screen 0 to screen 3 through the flow's own buttons (every kit row ticked). */
async function goToFill() {
  await goToScreen(2);
  const boxes = await screen.findAllByRole('checkbox');
  boxes.forEach((box) => fireEvent.click(box));
  fireEvent.click(screen.getByRole('button', { name: 'openMollie' }));
}

const currentStep = () => screen.getByRole('listitem', { current: 'step' });

describe('MollieOnboardingFlow — screen 3 "Remplir chez Mollie" (S4)', () => {
  let open: ReturnType<typeof vi.spyOn>;
  beforeEach(() => {
    mockGetContext.mockReset().mockResolvedValue(context());
    open = vi.spyOn(window, 'open').mockReturnValue(null);
  });
  afterEach(() => open.mockRestore());

  it('opens the hosted onboarding and shows the 10-step translator next to the Mollie window', async () => {
    renderFlow({ kyc: needsData() });
    await goToFill();

    expect(open).toHaveBeenCalledWith('https://my.mollie.com/dashboard/onboarding', '_blank', 'noopener,noreferrer');
    expect(currentStep()).toHaveAttribute('aria-label', 'state.current {"label":"steps.fill"}');
    expect(screen.getAllByText(/^steps\..*\.title$/)).toHaveLength(10);
    expect(screen.getAllByText('prefilled')).toHaveLength(3);
    expect(screen.getByText('toConfirm')).toBeInTheDocument();
    expect(screen.getByText('window.openedTitle')).toBeInTheDocument();
    // Beneficial owners from the onboarding context, ready to copy.
    expect(screen.getAllByText('Karim Benali').length).toBeGreaterThan(0);
  });

  it('reopens the hosted onboarding and forces a status refresh on demand', async () => {
    const status = needsData();
    renderFlow({ kyc: status });
    await goToFill();

    fireEvent.click(screen.getByRole('button', { name: 'window.reopen' }));
    expect(open).toHaveBeenCalledTimes(2);
    fireEvent.click(screen.getByRole('button', { name: 'window.refresh' }));
    await waitFor(() => expect(status.forceRefresh).toHaveBeenCalledTimes(1));
    // Explicit click: not silent, the error toast stays (R8).
    expect(status.forceRefresh).toHaveBeenCalledWith();
  });

  it('goes through the connection modal when not connected yet, never the hosted link', async () => {
    const { onConnect } = renderFlow();
    await goToFill();

    expect(onConnect).toHaveBeenCalledTimes(1);
    expect(open).not.toHaveBeenCalled();
    expect(screen.getByText('window.notOpenedTitle')).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'window.refresh' })).not.toBeInTheDocument();
    fireEvent.click(screen.getByRole('button', { name: 'window.open' }));
    expect(onConnect).toHaveBeenCalledTimes(2);
  });

  it('never starts a new connection when connected without a hosted link', async () => {
    const { onConnect } = renderFlow({ kyc: kyc({ connected: true, onboardingStatus: null }) });
    await goToScreen(2);
    await screen.findAllByRole('checkbox');

    expect(screen.getByRole('button', { name: 'openMollie' })).toBeDisabled();
    expect(onConnect).not.toHaveBeenCalled();
  });
});

describe('MollieOnboardingFlow — screen 4 "Paiement test" (S4)', () => {
  beforeEach(() => {
    mockGetContext.mockReset().mockResolvedValue(context());
    mockAttest.mockReset();
    vi.spyOn(window, 'open').mockReturnValue(null);
  });
  afterEach(() => vi.restoreAllMocks());

  it('explains the 0.01 € test payment and records the acknowledgement locally only', async () => {
    const status = needsData();
    renderFlow({ kyc: status });
    await goToFill();
    fireEvent.click(screen.getByRole('button', { name: 'next' }));

    expect(currentStep()).toHaveAttribute('aria-label', 'state.current {"label":"steps.testPayment"}');
    expect(screen.getByText('how.steps.s2')).toBeInTheDocument();
    expect(screen.getByRole('link', { name: 'openMollie' })).toHaveAttribute('href', 'https://my.mollie.com/dashboard/onboarding');

    fireEvent.click(screen.getByRole('button', { name: 'ack' }));
    expect(screen.getByText('acked')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'ack' })).toBeDisabled();
    // Not a server state: no call, and the flow stays on the test-payment step.
    expect(status.forceRefresh).not.toHaveBeenCalled();
    expect(mockAttest).not.toHaveBeenCalled();
    expect(currentStep()).toHaveAttribute('aria-label', 'state.current {"label":"steps.testPayment"}');
  });
});

describe('MollieOnboardingFlow — screen 5 "Vérification" (S4)', () => {
  beforeEach(() => mockGetContext.mockReset().mockResolvedValue(context()));
  afterEach(() => vi.restoreAllMocks());

  it('shows the review timeline while in review, without loading the context', () => {
    renderFlow({ kyc: kyc({ connected: true, onboardingStatus: MollieOnboardingStatus.IN_REVIEW, canReceivePayments: false }) });

    expect(screen.getByText('badgeInReview')).toBeInTheDocument();
    expect(screen.getByText('timeline.review.text')).toBeInTheDocument();
    expect(screen.getByText('timeline.test.todo')).toBeInTheDocument();
    expect(screen.queryByRole('alert')).not.toBeInTheDocument();
    expect(currentStep()).toHaveAttribute('aria-label', 'state.current {"label":"steps.review"}');
    expect(currentStep()).not.toHaveClass('warn');
    expect(mockGetContext).not.toHaveBeenCalled();
  });

  it('alerts on a requested complement and reopens the hosted onboarding', () => {
    const open = vi.spyOn(window, 'open').mockReturnValue(null);
    renderFlow({ kyc: needsData({ complementRequested: true }) });

    expect(screen.getByRole('alert')).toHaveTextContent('complement.text');
    expect(screen.getByText('badgeComplement')).toBeInTheDocument();
    expect(screen.getByText('timeline.review.complement')).toBeInTheDocument();
    expect(currentStep()).toHaveClass('warn');

    fireEvent.click(screen.getByRole('button', { name: 'complement.action' }));
    expect(open).toHaveBeenCalledWith('https://my.mollie.com/dashboard/onboarding', '_blank', 'noopener,noreferrer');
  });

  it('shows the red dev banner with its force button only when canForceComplete', () => {
    const { onForceComplete } = renderFlow({
      kyc: kyc({ connected: true, onboardingStatus: MollieOnboardingStatus.IN_REVIEW, canForceComplete: true }),
    });

    fireEvent.click(screen.getByRole('button', { name: 'devBanner.force' }));
    expect(onForceComplete).toHaveBeenCalled();
  });

  it('hides the dev banner without canForceComplete, and disables "force" until Mollie is connected', () => {
    const first = renderFlow();
    expect(screen.queryByText('devBanner.title')).not.toBeInTheDocument();
    first.unmount();

    renderFlow({ kyc: kyc({ canForceComplete: true }) });
    expect(screen.getByText('devBanner.needsConnection')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'devBanner.force' })).toBeDisabled();
  });
});

describe('MollieOnboardingFlow — screen 6 "Actif" (S4)', () => {
  const collecting = () =>
    kyc({ connected: true, onboardingStatus: MollieOnboardingStatus.COMPLETED, canReceivePayments: true });
  const noPayments = () =>
    kyc({ connected: true, onboardingStatus: MollieOnboardingStatus.COMPLETED, canReceivePayments: false });

  beforeEach(() => {
    mockGetContext.mockReset().mockResolvedValue(context());
    mockAttest.mockReset().mockResolvedValue({ attestedAt: '2026-10-12T09:41:00Z' });
  });

  it('celebrates an active account, with every step done, and links to the Widget and Landing tabs', async () => {
    const { onOpenTab } = renderFlow({ kyc: collecting(), bankDone: true });

    expect(screen.getByRole('heading', { name: 'title' })).toBeInTheDocument();
    expect(screen.queryByRole('listitem', { current: 'step' })).not.toBeInTheDocument();
    fireEvent.click(screen.getByRole('button', { name: /tabs\.widget/ }));
    fireEvent.click(screen.getByRole('button', { name: /tabs\.landing/ }));
    expect(onOpenTab).toHaveBeenNthCalledWith(1, 'widget');
    expect(onOpenTab).toHaveBeenNthCalledWith(2, 'landing');
    await waitFor(() => expect(mockGetContext).toHaveBeenCalled());
  });

  it('records the self-completion attestation, then keeps it checked', async () => {
    renderFlow({ kyc: collecting(), bankDone: true });
    const box = await screen.findByRole('checkbox', { name: 'attest.label' });
    await waitFor(() => expect(box).not.toBeDisabled());

    expect(box).not.toBeChecked();
    fireEvent.click(box);
    await waitFor(() => expect(box).toBeChecked());
    expect(mockAttest).toHaveBeenCalledTimes(1);
    expect(box).toBeDisabled();
  });

  it('reads an existing attestation from the onboarding context', async () => {
    mockGetContext.mockResolvedValue(context({ selfCompletionAttestedAt: '2026-10-01T08:00:00Z' }));
    renderFlow({ kyc: collecting(), bankDone: true });

    const box = await screen.findByRole('checkbox', { name: 'attest.label' });
    await waitFor(() => expect(box).toBeChecked());
    expect(box).toBeDisabled();
  });

  it('never offers "Connecter Mollie" to a COMPLETED account without payments, and the attestation unlocks nothing', async () => {
    const { onConnect, onOpenTab } = renderFlow({ kyc: noPayments(), bankDone: false });

    expect(screen.getByRole('status')).toHaveTextContent('noPayments');
    expect(screen.queryByRole('button', { name: 'mollie.connect' })).not.toBeInTheDocument();
    expect(currentStep()).toHaveClass('warn');
    const box = await screen.findByRole('checkbox', { name: 'attest.label' });
    await waitFor(() => expect(box).not.toBeDisabled());
    fireEvent.click(box);
    await waitFor(() => expect(box).toBeChecked());

    const widget = screen.getByRole('button', { name: /tabs\.widget/ });
    expect(widget).toBeDisabled();
    expect(widget).toHaveClass('tab-locked');
    fireEvent.click(widget);
    expect(onOpenTab).not.toHaveBeenCalled();
    expect(onConnect).not.toHaveBeenCalled();
    expect(screen.getByText('tabs.hint')).toBeInTheDocument();
  });
});

describe('MollieOnboardingFlow — states (S4)', () => {
  beforeEach(() => mockGetContext.mockReset().mockResolvedValue(context()));

  it('shows a skeleton while the status loads', () => {
    renderFlow({ kyc: kyc({ isLoading: true }) });

    expect(document.querySelector('[aria-busy="true"]')).toBeInTheDocument();
    expect(screen.queryByRole('list', { name: 'label' })).not.toBeInTheDocument();
  });

  it('asks to reconnect when the connection is broken, with the visio help link', () => {
    const { onReconnect } = renderFlow({ kyc: kyc({ connected: true, broken: true }) });

    fireEvent.click(screen.getByRole('button', { name: 'reconnect' }));
    expect(onReconnect).toHaveBeenCalled();
    expect(screen.getByRole('link', { name: 'help' })).toHaveAttribute('href', VISIO);
    expect(screen.queryByRole('list', { name: 'label' })).not.toBeInTheDocument();
  });

  it('offers to retry after an interrupted connection', () => {
    const { onRetryConnect } = renderFlow({ interrupted: true });

    expect(screen.getByRole('status')).toHaveTextContent('notices.interrupted');
    fireEvent.click(screen.getByRole('button', { name: 'notices.retry' }));
    expect(onRetryConnect).toHaveBeenCalled();
  });

  it('does not start a second authorization while one is pending', async () => {
    const { onConnect } = renderFlow({ kyc: kyc({ pending: true }) });

    expect(screen.getByRole('status')).toHaveTextContent('notices.pending');
    await goToScreen(2);
    await screen.findAllByRole('checkbox');
    expect(screen.getByRole('button', { name: 'openMollie' })).toBeDisabled();
    expect(onConnect).not.toHaveBeenCalled();
  });

  it('opens the "Why Mollie?" modal from the link under the FAQ of screen 0 (R10)', () => {
    const { onLearnMore } = renderFlow();

    fireEvent.click(screen.getByRole('button', { name: 'learnMore' }));
    expect(onLearnMore).toHaveBeenCalledTimes(1);
  });

  it('shows the last refresh time in the flow header (Q4)', () => {
    renderFlow({ kyc: kyc({ lastUpdatedAt: new Date(2026, 9, 12, 9, 41) }) });

    expect(screen.getByText('refreshedAt {"time":"09:41"}')).toBeInTheDocument();
  });

  it('links the FAQ and the help card to the visio booking page (R4), in a new tab', () => {
    renderFlow();
    fireEvent.click(screen.getByRole('button', { name: 'faq.items.help.question' }));

    for (const name of ['faq.items.help.book', 'book']) {
      const link = screen.getByRole('link', { name });
      expect(link).toHaveAttribute('href', VISIO);
      expect(link).toHaveAttribute('target', '_blank');
      expect(link).toHaveAttribute('rel', 'noopener noreferrer');
    }
  });
});

describe('MollieOnboardingFlow — status refresh while the tab is mounted (S4)', () => {
  let visibility: DocumentVisibilityState;
  beforeEach(() => {
    mockGetContext.mockReset().mockResolvedValue(context());
    visibility = 'visible';
    Object.defineProperty(document, 'visibilityState', { configurable: true, get: () => visibility });
  });
  afterEach(() => {
    vi.useRealTimers();
    // Restore jsdom's own getter.
    delete (document as unknown as Record<string, unknown>).visibilityState;
  });

  it('forces a refresh when the association comes back to the tab (visibilitychange, focus)', () => {
    const status = needsData();
    renderFlow({ kyc: status });

    act(() => {
      document.dispatchEvent(new Event('visibilitychange'));
    });
    expect(status.forceRefresh).toHaveBeenCalledTimes(1);
    // Automatic trigger: silent, no error toast (R8).
    expect(status.forceRefresh).toHaveBeenCalledWith({ silent: true });

    visibility = 'hidden';
    act(() => {
      document.dispatchEvent(new Event('visibilitychange'));
    });
    expect(status.forceRefresh).toHaveBeenCalledTimes(1);

    act(() => {
      window.dispatchEvent(new Event('focus'));
    });
    expect(status.forceRefresh).toHaveBeenCalledTimes(2);
    expect(status.forceRefresh).toHaveBeenLastCalledWith({ silent: true });
  });

  it('re-reads the status silently every 60 s, and stops everything on unmount', () => {
    vi.useFakeTimers();
    const status = needsData();
    const { unmount } = renderFlow({ kyc: status });

    act(() => {
      vi.advanceTimersByTime(60_000);
    });
    expect(status.refresh).toHaveBeenCalledTimes(1);
    expect(status.refresh).toHaveBeenCalledWith({ silent: true });

    unmount();
    act(() => {
      vi.advanceTimersByTime(180_000);
      document.dispatchEvent(new Event('visibilitychange'));
      window.dispatchEvent(new Event('focus'));
    });
    expect(status.refresh).toHaveBeenCalledTimes(1);
    expect(status.forceRefresh).not.toHaveBeenCalled();
    expect(vi.getTimerCount()).toBe(0);
  });
});
