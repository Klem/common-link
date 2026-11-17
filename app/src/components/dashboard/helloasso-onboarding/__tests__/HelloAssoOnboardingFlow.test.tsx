import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import { render, screen, fireEvent, waitFor, act } from '@testing-library/react';
import { AxiosError, AxiosHeaders } from 'axios';
import HelloAssoOnboardingFlow from '../HelloAssoOnboardingFlow';
import { useAccStatusStore } from '@/stores/accStatusStore';
import { useToastStore } from '@/stores/toastStore';
import { HelloAssoErrorCode, HelloAssoLink, HelloAssoWebhookState } from '@/types/helloasso';
import type { HelloAssoStatus } from '@/types/helloasso';
import type { AssociationProfileDto } from '@/types/association';
import {
  disconnect,
  getDisconnectPreview,
  ensureNotificationUrl,
  linkAccount,
  markOnboardingStarted,
  refreshEligibility,
  verifyCredentials,
} from '@/lib/api/helloasso';
import { listKybDocuments } from '@/lib/api/association-documents';
import { HELLOASSO_MANDATE_VERSION } from '../helloAssoFlow';

vi.mock('next-intl', () => {
  const t = Object.assign((key: string) => key, { rich: (key: string) => key });
  return { useTranslations: () => t, useLocale: () => 'fr' };
});

vi.mock('@/lib/api/helloasso', async (importOriginal) => {
  const actual = await importOriginal<typeof import('@/lib/api/helloasso')>();
  return {
    ...actual,
    ensureNotificationUrl: vi.fn(),
    verifyCredentials: vi.fn(),
    linkAccount: vi.fn(),
    disconnect: vi.fn(),
    getDisconnectPreview: vi.fn(),
    refreshEligibility: vi.fn(),
    markOnboardingStarted: vi.fn(),
  };
});

vi.mock('@/lib/api/association-documents', () => ({
  listKybDocuments: vi.fn(),
  downloadOwnAssociationDocument: vi.fn(),
}));

const URL_ = 'https://api.example/api/public/webhooks/helloasso/tok';
const refresh = vi.fn();
const profile = {
  id: 'a1',
  name: 'Les Amis',
  widgetToken: 'clk_123',
  widgetDestinationCampaignId: 'camp-1',
} as unknown as AssociationProfileDto;

const status = (overrides: Partial<HelloAssoStatus> = {}): HelloAssoStatus => ({
  link: HelloAssoLink.NONE,
  notificationUrl: URL_,
  organizationSlug: null,
  canCollect: false,
  everCompliant: false,
  lastEligibilityCheckAt: null,
  webhook: HelloAssoWebhookState.UNKNOWN,
  clientIdMasked: null,
  linkedAt: null,
  linkedBy: null,
  secretWipedAt: null,
  lastRefreshAt: null,
  ...overrides,
});
const linked = (overrides: Partial<HelloAssoStatus> = {}) =>
  status({
    link: HelloAssoLink.OK,
    organizationSlug: 'les-amis',
    lastEligibilityCheckAt: '2026-11-03T09:41:00Z',
    clientIdMasked: 'abcd…wxyz',
    linkedAt: '2026-11-03T09:38:00Z',
    linkedBy: 'asso@example.org',
    secretWipedAt: '2026-11-03T09:38:00Z',
    ...overrides,
  });

function renderFlow(helloasso: HelloAssoStatus | null, resolved = true) {
  useAccStatusStore.setState({ helloasso, bankResolved: resolved, refreshPaymentStatus: refresh });
  return render(<HelloAssoOnboardingFlow profile={profile} />);
}

const apiError = (status: number, code?: string) =>
  new AxiosError('failed', 'ERR_BAD_REQUEST', undefined, undefined, {
    status,
    statusText: '',
    data: code ? { code } : {},
    headers: {},
    config: { headers: new AxiosHeaders() },
  });

const typeKeys = (id: string, secret: string) => {
  fireEvent.change(screen.getByLabelText(/step2.clientIdLabel/), { target: { value: id } });
  fireEvent.change(screen.getByLabelText(/step2.secretLabel/), { target: { value: secret } });
};

const okVerify = { verifyId: 'v1', orgName: 'Les Amis', slug: 'les-amis', matchesKyb: true, canCollect: false };

beforeEach(() => {
  vi.clearAllMocks();
  refresh.mockResolvedValue(undefined);
  vi.mocked(ensureNotificationUrl).mockResolvedValue(URL_);
  vi.mocked(markOnboardingStarted).mockResolvedValue(undefined);
  vi.mocked(listKybDocuments).mockResolvedValue([]);
  vi.mocked(getDisconnectPreview).mockResolvedValue({ notYetPaidOutCount: 0, notYetPaidOutAmount: 0 });
  useToastStore.setState({ toasts: [] });
  vi.stubEnv('NEXT_PUBLIC_HELLOASSO_VISIO_URL', '');
});

afterEach(() => {
  vi.unstubAllEnvs();
});

describe('views', () => {
  it('shows the skeleton until the status is read', () => {
    renderFlow(null, false);
    expect(screen.getByLabelText('aria')).toHaveAttribute('aria-busy', 'true');
  });

  it('replaces the flow when the access is broken, and re-pasting opens screen 4 in replacement mode', async () => {
    renderFlow(status({ link: HelloAssoLink.BROKEN, everCompliant: true }));
    expect(screen.getByText('title')).toBeInTheDocument();
    expect(screen.queryByRole('list', { name: 'label' })).not.toBeInTheDocument();

    fireEvent.click(screen.getByRole('button', { name: 'replace' }));

    expect(screen.getByText('replaceBanner')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'submitReplace' })).toBeDisabled();
    // Notification URL usually still in place: pre-acknowledged.
    expect(screen.getByRole('checkbox', { name: 'step3.ack' })).toBeChecked();
  });

  it('after re-pasting the key, re-reads the status before leaving screen 4: the broken view never flashes', async () => {
    vi.mocked(verifyCredentials).mockResolvedValue(okVerify);
    vi.mocked(linkAccount).mockResolvedValue(undefined);
    let finishRefresh: () => void = () => undefined;
    refresh.mockImplementation(
      () =>
        new Promise<void>((resolve) => {
          finishRefresh = () => {
            useAccStatusStore.setState({ helloasso: linked() });
            resolve();
          };
        }),
    );
    renderFlow(status({ link: HelloAssoLink.BROKEN, everCompliant: true }));
    fireEvent.click(screen.getByRole('button', { name: 'replace' }));
    typeKeys('id', 'secret');
    fireEvent.click(await screen.findByRole('checkbox', { name: 'step2.confirm' }, { timeout: 3000 }));
    fireEvent.click(screen.getByRole('checkbox', { name: 'mandate' }));
    fireEvent.click(screen.getByRole('button', { name: 'submitReplace' }));

    await waitFor(() => expect(refresh).toHaveBeenCalled());
    // Re-read in flight, old BROKEN status still in the store: screen 4 stays, no "accès à renouveler".
    expect(screen.queryByText('keep')).not.toBeInTheDocument();
    expect(screen.getByText('replaceBanner')).toBeInTheDocument();

    await act(async () => finishRefresh());

    expect(screen.queryByText('keep')).not.toBeInTheDocument();
    expect(screen.getByText('linkedBanner')).toBeInTheDocument();
  });

  it('shows the last eligibility check time, politely announced', () => {
    renderFlow(linked());
    expect(screen.getByText('checkedAt')).toHaveAttribute('aria-live', 'polite');
  });
});

describe('screens 0 and 3', () => {
  it('goes 0 → 3 → 4 on the "new" path and records the journey start', async () => {
    renderFlow(status());
    expect(screen.getByRole('heading', { name: 'title' })).toBeInTheDocument();

    fireEvent.click(screen.getByRole('button', { name: 'new.cta' }));
    expect(markOnboardingStarted).toHaveBeenCalledTimes(1);
    expect(screen.getByRole('link', { name: 'cta' })).toHaveAttribute('target', '_blank');

    fireEvent.click(screen.getByRole('button', { name: 'continue' }));
    expect(screen.getByLabelText(/step2.clientIdLabel/)).toBeInTheDocument();
    // Step 1 done, step 2 current.
    const steps = screen.getAllByRole('listitem').filter((li) => li.classList.contains('stepper-item'));
    expect(steps[0]).toHaveClass('done');
    expect(steps[1]).toHaveAttribute('aria-current', 'step');

    fireEvent.click(screen.getByRole('button', { name: 'back' }));
    expect(screen.getByRole('button', { name: 'continue' })).toBeInTheDocument();
  });

  it('goes 0 → 4 on the "existing" path with step 1 "Déjà fait", and back to 0', () => {
    renderFlow(status());
    fireEvent.click(screen.getByRole('button', { name: 'existing.cta' }));

    expect(markOnboardingStarted).not.toHaveBeenCalled();
    const steps = screen.getAllByRole('listitem').filter((li) => li.classList.contains('stepper-item'));
    expect(steps[0]).toHaveClass('skipped');
    expect(steps[0]).toHaveTextContent('skippedSub');

    fireEvent.click(screen.getByRole('button', { name: 'back' }));
    expect(screen.getByRole('button', { name: 'existing.cta' })).toBeInTheDocument();
  });

  it('shows the FAQ on screen 0 and hides it on screens 3 and 4', () => {
    renderFlow(status());
    expect(screen.getByText('titleStart')).toBeInTheDocument();
    fireEvent.click(screen.getByRole('button', { name: 'new.cta' }));
    expect(screen.queryByText('titleStart')).not.toBeInTheDocument();
    expect(screen.queryByText('title', { selector: 'h3' })).not.toBeInTheDocument();
  });
});

describe('screen 4 — key check and link', () => {
  const openLink = (s: HelloAssoStatus = status()) => {
    renderFlow(s);
    fireEvent.click(screen.getByRole('button', { name: 'existing.cta' }));
  };

  it('creates the notification URL when the status has none', async () => {
    openLink(status({ notificationUrl: null }));
    await waitFor(() => expect(ensureNotificationUrl).toHaveBeenCalledTimes(1));
    expect(await screen.findByText(URL_)).toBeInTheDocument();
  });

  it('checks the codes once both are filled, then gates the button on every condition', async () => {
    vi.mocked(verifyCredentials).mockResolvedValue(okVerify);
    vi.mocked(linkAccount).mockResolvedValue(undefined);
    openLink();
    const submit = screen.getByRole('button', { name: 'submit' });

    fireEvent.change(screen.getByLabelText(/step2.clientIdLabel/), { target: { value: ' id ' } });
    expect(verifyCredentials).not.toHaveBeenCalled();
    fireEvent.change(screen.getByLabelText(/step2.secretLabel/), { target: { value: 'secret' } });

    expect(await screen.findByText('step2.okMatch', {}, { timeout: 3000 })).toBeInTheDocument();
    expect(verifyCredentials).toHaveBeenCalledTimes(1);
    expect(vi.mocked(verifyCredentials).mock.calls[0][0]).toEqual({ clientId: 'id', clientSecret: 'secret' });
    expect(screen.getByText('step2.validBadge')).toBeInTheDocument();
    expect(submit).toBeDisabled();

    fireEvent.click(screen.getByRole('checkbox', { name: 'step2.confirm' }));
    fireEvent.click(screen.getByRole('checkbox', { name: 'step3.ack' }));
    expect(submit).toBeDisabled();
    fireEvent.click(screen.getByRole('checkbox', { name: 'mandate' }));
    expect(submit).toBeEnabled();

    fireEvent.click(submit);
    await waitFor(() =>
      expect(linkAccount).toHaveBeenCalledWith({ verifyId: 'v1', mandate: { accepted: true, version: HELLOASSO_MANDATE_VERSION } }),
    );
    await waitFor(() => expect(refresh).toHaveBeenCalled());
  });

  it('resets "C\'est bien nous" when a code changes', async () => {
    vi.mocked(verifyCredentials).mockResolvedValue(okVerify);
    openLink();
    typeKeys('id', 'secret');
    fireEvent.click(await screen.findByRole('checkbox', { name: 'step2.confirm' }, { timeout: 3000 }));

    fireEvent.change(screen.getByLabelText(/step2.secretLabel/), { target: { value: 'secret2' } });

    await waitFor(() => expect(verifyCredentials).toHaveBeenCalledTimes(2), { timeout: 3000 });
    expect(await screen.findByRole('checkbox', { name: 'step2.confirm' })).not.toBeChecked();
  });

  it('shows invalid codes as an alert with the inputs marked invalid', async () => {
    vi.mocked(verifyCredentials).mockRejectedValue(apiError(422, HelloAssoErrorCode.INVALID_KEYS));
    openLink();
    typeKeys('id', 'bad');

    expect(await screen.findByText('step2.invalidTitle', {}, { timeout: 3000 })).toBeInTheDocument();
    // Re-queried until stable: the stepper warns once the key-check outcome has reached the parent flow.
    await waitFor(() => {
      expect(screen.getByLabelText(/step2.clientIdLabel/)).toHaveAttribute('aria-invalid', 'true');
      const steps = screen.getAllByRole('listitem').filter((li) => li.classList.contains('stepper-item'));
      expect(steps[1]).toHaveClass('warn');
    });
  });

  it('refuses another association: nothing is ever sent to /connection', async () => {
    vi.mocked(verifyCredentials).mockResolvedValue({ ...okVerify, verifyId: null, matchesKyb: false, orgName: 'Autre' });
    openLink();
    typeKeys('id', 'secret');

    expect(await screen.findByText('step2.mismatchTitle', {}, { timeout: 3000 })).toBeInTheDocument();
    expect(screen.queryByRole('checkbox', { name: 'step2.confirm' })).not.toBeInTheDocument();
    fireEvent.click(screen.getByRole('checkbox', { name: 'step3.ack' }));
    fireEvent.click(screen.getByRole('checkbox', { name: 'mandate' }));
    const submit = screen.getByRole('button', { name: 'submit' });
    expect(submit).toBeDisabled();
    fireEvent.click(submit);
    expect(linkAccount).not.toHaveBeenCalled();
  });

  it('tells an outage apart from invalid codes', async () => {
    vi.mocked(verifyCredentials).mockRejectedValue(apiError(502));
    openLink();
    typeKeys('id', 'secret');
    expect(await screen.findByText('step2.unavailable', {}, { timeout: 3000 })).toBeInTheDocument();
    expect(screen.queryByText('step2.invalidTitle')).not.toBeInTheDocument();
  });

  it('asks for the codes again when the verification expired', async () => {
    vi.mocked(verifyCredentials).mockResolvedValue(okVerify);
    vi.mocked(linkAccount).mockRejectedValue(apiError(422, HelloAssoErrorCode.VERIFICATION_EXPIRED));
    openLink();
    typeKeys('id', 'secret');
    fireEvent.click(await screen.findByRole('checkbox', { name: 'step2.confirm' }, { timeout: 3000 }));
    fireEvent.click(screen.getByRole('checkbox', { name: 'step3.ack' }));
    fireEvent.click(screen.getByRole('checkbox', { name: 'mandate' }));
    fireEvent.click(screen.getByRole('button', { name: 'submit' }));

    expect(await screen.findByText('errors.expired')).toBeInTheDocument();
    expect(screen.getByLabelText(/step2.secretLabel/)).toHaveValue('');
    expect(refresh).not.toHaveBeenCalled();
  });

  it('toggles the secret visibility with aria-pressed', () => {
    openLink();
    const secret = screen.getByLabelText(/step2.secretLabel/);
    const toggle = screen.getByRole('button', { name: 'step2.show' });
    expect(secret).toHaveAttribute('type', 'password');
    expect(toggle).toHaveAttribute('aria-pressed', 'false');
    fireEvent.click(toggle);
    expect(secret).toHaveAttribute('type', 'text');
    expect(screen.getByRole('button', { name: 'step2.hide' })).toHaveAttribute('aria-pressed', 'true');
  });
});

describe('screen 5 — verification', () => {
  it('links to the verification page and checks the file on demand', async () => {
    vi.mocked(refreshEligibility).mockResolvedValue(undefined);
    renderFlow(linked());

    expect(screen.getByRole('link', { name: 'openVerification' })).toHaveAttribute(
      'href',
      'https://admin.helloasso.com/les-amis/verification',
    );
    fireEvent.click(screen.getByRole('button', { name: 'refresh' }));
    await waitFor(() => expect(refreshEligibility).toHaveBeenCalledTimes(1));
    await waitFor(() => expect(refresh).toHaveBeenCalled());
  });

  it('explains a 429 instead of failing silently', async () => {
    vi.mocked(refreshEligibility).mockRejectedValue(apiError(429));
    renderFlow(linked());
    fireEvent.click(screen.getByRole('button', { name: 'refresh' }));
    expect(await screen.findByText('tooSoon')).toBeInTheDocument();
  });

  it('lists the KYB documents on "Retrouver mes fichiers"', async () => {
    vi.mocked(listKybDocuments).mockResolvedValue([{ id: 'd1', type: 'VERIF_STATUTS' } as never]);
    renderFlow(linked());
    fireEvent.click(screen.getByRole('button', { name: 'files' }));
    expect(await screen.findByText('types.VERIF_STATUTS')).toBeInTheDocument();
  });

  it('re-reads the status silently when the user comes back, while linked and not collecting', () => {
    renderFlow(linked());
    act(() => {
      window.dispatchEvent(new Event('focus'));
    });
    expect(refresh).toHaveBeenCalledWith({ silent: true });
    expect(refreshEligibility).not.toHaveBeenCalled();
  });

  it('does not re-read on focus once collecting', () => {
    renderFlow(linked({ canCollect: true, everCompliant: true }));
    act(() => {
      window.dispatchEvent(new Event('focus'));
    });
    expect(refresh).not.toHaveBeenCalled();
  });
});

describe('screen 6 — active, suspended, test donation', () => {
  it('shows the active card, every step done and the test donation on the fundraising page', () => {
    renderFlow(linked({ canCollect: true, everCompliant: true }));
    expect(screen.getByRole('heading', { level: 4, name: 'title' })).toBeInTheDocument();
    const steps = screen.getAllByRole('listitem').filter((li) => li.classList.contains('stepper-item'));
    steps.forEach((step) => expect(step).toHaveClass('done'));
    expect(screen.getByRole('link', { name: 'test.cta' })).toHaveAttribute('href', '/fr/lp/clk_123');
    expect(screen.getByText('test.badge.UNKNOWN')).toHaveClass('badge-info');
  });

  it.each([
    [HelloAssoWebhookState.RECEIVED, 'badge-success'],
    [HelloAssoWebhookState.MISSING, 'badge-error'],
  ])('shows the %s notification badge', (webhook, cls) => {
    renderFlow(linked({ canCollect: true, everCompliant: true, webhook }));
    expect(screen.getByText(`test.badge.${webhook}`)).toHaveClass(cls);
  });

  it('alerts when the notification is missing and gives the URL again', () => {
    renderFlow(linked({ canCollect: true, everCompliant: true, webhook: HelloAssoWebhookState.MISSING }));
    expect(screen.getByRole('alert')).toHaveTextContent('test.missing');
    expect(screen.getByText(URL_)).toBeInTheDocument();
  });

  it('shows the suspended alert, warns on the current step and keeps disconnection possible', () => {
    renderFlow(linked({ canCollect: false, everCompliant: true }));
    expect(screen.getByText('suspended.text')).toBeInTheDocument();
    expect(screen.queryByText('test.title')).not.toBeInTheDocument();
    const steps = screen.getAllByRole('listitem').filter((li) => li.classList.contains('stepper-item'));
    expect(steps[3]).toHaveClass('warn');
    expect(screen.getByRole('button', { name: 'disconnect' })).toBeInTheDocument();
  });
});

describe('disconnect modal', () => {
  const openModal = () => {
    renderFlow(linked({ canCollect: true, everCompliant: true }));
    const trigger = screen.getByRole('button', { name: 'disconnect' });
    expect(trigger).toHaveAttribute('aria-haspopup', 'dialog');
    fireEvent.click(trigger);
    return trigger;
  };

  it('is an alertdialog that focuses "Annuler" and gives focus back on Escape', async () => {
    const trigger = openModal();
    const dialog = screen.getByRole('alertdialog');
    expect(dialog).toHaveAttribute('aria-modal', 'true');
    expect(dialog).toHaveAttribute('aria-labelledby', 'hao-dm-title');
    expect(dialog).toHaveAttribute('aria-describedby', 'hao-dm-body');
    expect(screen.getByRole('button', { name: 'cancel' })).toHaveFocus();

    fireEvent.keyDown(document, { key: 'Escape' });
    expect(screen.queryByRole('alertdialog')).not.toBeInTheDocument();
    await waitFor(() => expect(trigger).toHaveFocus());
  });

  it('closes on a backdrop click but not on a click inside', () => {
    openModal();
    fireEvent.click(screen.getByRole('alertdialog'));
    expect(screen.getByRole('alertdialog')).toBeInTheDocument();
    fireEvent.click(screen.getByTestId('hao-disconnect-backdrop'));
    expect(screen.queryByRole('alertdialog')).not.toBeInTheDocument();
  });

  it('warns about the donations HelloAsso has not paid out yet, and still allows disconnecting', async () => {
    vi.mocked(getDisconnectPreview).mockResolvedValue({ notYetPaidOutCount: 3, notYetPaidOutAmount: 42.5 });
    vi.mocked(disconnect).mockResolvedValue(undefined);
    openModal();

    expect(await screen.findByTestId('hao-disconnect-not-paid-out')).toHaveTextContent('notYetPaidOutRelease');
    fireEvent.click(screen.getByRole('button', { name: 'confirm' }));
    await waitFor(() => expect(disconnect).toHaveBeenCalledTimes(1));
  });

  it('shows no warning without donations pending, nor when the preview fails', async () => {
    openModal();
    await waitFor(() => expect(getDisconnectPreview).toHaveBeenCalled());
    expect(screen.queryByTestId('hao-disconnect-not-paid-out')).not.toBeInTheDocument();

    fireEvent.keyDown(document, { key: 'Escape' });
    vi.mocked(getDisconnectPreview).mockRejectedValue(apiError(502));
    fireEvent.click(screen.getByRole('button', { name: 'disconnect' }));
    await waitFor(() => expect(getDisconnectPreview).toHaveBeenCalledTimes(2));
    expect(screen.queryByTestId('hao-disconnect-not-paid-out')).not.toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'confirm' })).toBeEnabled();
  });

  it('disconnects, toasts and re-reads the status', async () => {
    vi.mocked(disconnect).mockResolvedValue(undefined);
    openModal();
    fireEvent.click(screen.getByRole('button', { name: 'confirm' }));

    await waitFor(() => expect(disconnect).toHaveBeenCalledTimes(1));
    await waitFor(() => expect(refresh).toHaveBeenCalled());
    expect(useToastStore.getState().toasts.map((t) => t.messageKey)).toContain('helloasso.disconnected');
    expect(screen.queryByRole('alertdialog')).not.toBeInTheDocument();
  });
});

describe('"on request" payout recommendation (CEO Q2)', () => {
  it('is shown on the active screen', () => {
    renderFlow(linked({ canCollect: true, everCompliant: true }));
    expect(screen.getByTestId('hao-payout-mode-tip')).toHaveTextContent('payoutMode');
  });

  it('has its FAQ entry listing both payout schedules', () => {
    renderFlow(linked({ canCollect: true, everCompliant: true }));
    fireEvent.click(screen.getByRole('button', { name: 'payoutMode.q' }));
    expect(screen.getByText('payoutMode.intro')).toBeInTheDocument();
    expect(screen.getByText('payoutMode.onDemand')).toBeInTheDocument();
    expect(screen.getByText('payoutMode.automatic')).toBeInTheDocument();
    expect(screen.getByText('payoutMode.why')).toBeInTheDocument();
  });

  it('"En savoir plus" opens that FAQ entry and moves focus to it', async () => {
    renderFlow(linked({ canCollect: true, everCompliant: true }));
    const question = screen.getByRole('button', { name: 'payoutMode.q' });
    expect(question).toHaveAttribute('aria-expanded', 'false');

    fireEvent.click(screen.getByTestId('hao-payout-mode-learn-more'));

    expect(question).toHaveAttribute('aria-expanded', 'true');
    expect(screen.getByText('payoutMode.onDemand')).toBeInTheDocument();
    await waitFor(() => expect(question).toHaveFocus());
  });
});

describe('FAQ and help card', () => {
  it('shows "Votre accès aujourd\'hui" only when linked', () => {
    renderFlow(linked());
    fireEvent.click(screen.getByRole('button', { name: 'keepKey.q' }));
    expect(screen.getByTestId('hao-access-live')).toHaveTextContent('abcd…wxyz');
  });

  it('hides the access block before linking', () => {
    renderFlow(status());
    fireEvent.click(screen.getByRole('button', { name: 'keepKey.q' }));
    expect(screen.queryByTestId('hao-access-live')).not.toBeInTheDocument();
  });

  it('opens a panel as a labelled region', () => {
    renderFlow(status());
    const trigger = screen.getByRole('button', { name: 'why.q' });
    expect(trigger).toHaveAttribute('aria-expanded', 'false');
    fireEvent.click(trigger);
    expect(trigger).toHaveAttribute('aria-expanded', 'true');
    expect(screen.getByRole('region', { name: 'why.q' })).toBeInTheDocument();
  });

  it('shows the CommonLink fees text, with no pending-decision placeholder', () => {
    renderFlow(status());
    fireEvent.click(screen.getByRole('button', { name: 'fees.q' }));
    expect(screen.getByTestId('hao-fees-platform')).toHaveTextContent('fees.platform');
    expect(screen.queryByText('fees.pending')).not.toBeInTheDocument();
  });

  it('hides the visio card when no booking URL is configured, shows it otherwise', () => {
    const { unmount } = renderFlow(status());
    expect(screen.queryByRole('link', { name: 'book' })).not.toBeInTheDocument();
    unmount();

    vi.stubEnv('NEXT_PUBLIC_HELLOASSO_VISIO_URL', 'https://visio.example/book');
    renderFlow(status());
    expect(screen.getByRole('link', { name: 'book' })).toHaveAttribute('href', 'https://visio.example/book');
  });
});
