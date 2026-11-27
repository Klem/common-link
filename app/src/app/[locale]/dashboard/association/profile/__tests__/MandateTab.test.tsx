import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, fireEvent, waitFor, act } from '@testing-library/react';
import { MandateTab } from '@/components/settings/MandateTab';
import type { MandateStateDto } from '@/types/mandate';
import type { UseSignatoryReturn } from '@/hooks/dashboard/useSignatory';
import type { SignatoryDto } from '@/types/signatory';

// ── Mocks ────────────────────────────────────────────────────────────────────

vi.mock('next-intl', () => ({
  useTranslations: () => (key: string) => key,
}));

/** Signer saved with a signature by default, so the mandate flow tests keep their meaning. */
const savedSignatory: SignatoryDto = {
  signerName: 'Marie Dupont',
  signerRole: 'Présidente',
  mode: 'DRAWN',
  hasSignature: true,
  attestedAt: '2026-11-20T10:00:00Z',
  updatedAt: '2026-11-20T10:00:00Z',
};

const signatoryHook = vi.hoisted(() => ({ current: null as unknown as UseSignatoryReturn }));

vi.mock('@/hooks/dashboard/useSignatory', () => ({
  useSignatory: () => signatoryHook.current,
}));

// The card has its own test file (SignatoryCard.test.tsx); only its presence matters here.
vi.mock('@/components/settings/SignatoryCard', () => ({
  SIGNATORY_CARD_ID: 'signatory-card',
  SignatoryCard: () => <div data-testid="signatory-card" />,
}));

function mockSignatory(signatory: SignatoryDto | null, isLoading = false) {
  signatoryHook.current = {
    signatory,
    isLoading,
    savedImage: null,
    savedImageUrl: null,
    save: vi.fn().mockResolvedValue(true),
  };
}

beforeEach(() => {
  mockSignatory(savedSignatory);
});

// ── Fixtures ─────────────────────────────────────────────────────────────────

const emptyDocs: MandateStateDto['mandateDocs'] = [
  { docType: 'MANDATE_STATUTS', uploaded: false },
  { docType: 'MANDATE_RESCRIT', uploaded: false },
];

const fullDocs: MandateStateDto['mandateDocs'] = [
  { docType: 'MANDATE_STATUTS', uploaded: true, id: 'id1', fileName: 'statuts.pdf', sizeBytes: 102400, uploadedAt: '2026-07-01T10:00:00Z' },
  { docType: 'MANDATE_RESCRIT', uploaded: true, id: 'id2', fileName: 'rescrit.pdf', sizeBytes: 51200, uploadedAt: '2026-07-01T10:01:00Z' },
];

const blockedState: MandateStateDto = {
  signed: false, blocked: true, mandateDocs: emptyDocs, receiptPrerequisitesMissing: [],
};

const unSignedState: MandateStateDto = {
  signed: false, blocked: false, mandateDocs: emptyDocs, receiptPrerequisitesMissing: [],
};

const unSignedWithDocs: MandateStateDto = {
  signed: false, blocked: false, mandateDocs: fullDocs, receiptPrerequisitesMissing: [],
};

const signedState: MandateStateDto = {
  signed: true,
  blocked: false,
  reference: 'MND-2026-0001',
  signedAt: '2026-07-08T10:00:00Z',
  eligibility: 'OIG_66',
  revokedAt: null,
  mandateDocs: fullDocs,
  receiptPrerequisitesMissing: [],
};

const ALL_PREREQUISITES: MandateStateDto['receiptPrerequisitesMissing'] = [
  'SIGNATURE',
  'LEGAL_OBJECT',
  'HEAD_OFFICE_ADDRESS',
];

const P = 'association.profile.mandate.prerequisites';

function makeProps(state: MandateStateDto, overrides = {}) {
  return {
    state,
    isLoading: false,
    onGoToVerif: vi.fn(),
    onUploadDoc: vi.fn().mockResolvedValue(undefined),
    onDeleteDoc: vi.fn().mockResolvedValue(undefined),
    onSign: vi.fn().mockResolvedValue(undefined),
    onRevoke: vi.fn().mockResolvedValue(undefined),
    onDownloadPdf: vi.fn().mockResolvedValue(undefined),
    onSignatorySaved: vi.fn(),
    onGoToInfos: vi.fn(),
    ...overrides,
  };
}

// ── Loading state ─────────────────────────────────────────────────────────────

describe('MandateTab — loading', () => {
  it('affiche le loader si isLoading=true', () => {
    const props = makeProps(blockedState, { isLoading: true });
    render(<MandateTab {...props} />);
    expect(screen.getByText('association.profile.loading')).toBeInTheDocument();
  });
});

// ── Blocked state ─────────────────────────────────────────────────────────────

describe('MandateTab — bloqué', () => {
  it('affiche la bannière de blocage', () => {
    render(<MandateTab {...makeProps(blockedState)} />);
    expect(screen.getByText('association.profile.mandate.blocked.message')).toBeInTheDocument();
  });

  it('bouton CTA appelle onGoToVerif', async () => {
    const props = makeProps(blockedState);
    render(<MandateTab {...props} />);
    const btn = screen.getByRole('button', { name: /association\.profile\.mandate\.blocked\.cta/i });
    await act(async () => { fireEvent.click(btn); });
    expect(props.onGoToVerif).toHaveBeenCalledOnce();
  });

  it('n\'affiche pas les étapes du flux', () => {
    render(<MandateTab {...makeProps(blockedState)} />);
    expect(screen.queryByText('association.profile.mandate.step1.title')).not.toBeInTheDocument();
  });

  it('hides the signatory card', () => {
    render(<MandateTab {...makeProps(blockedState)} />);
    expect(screen.queryByTestId('signatory-card')).not.toBeInTheDocument();
  });
});

// ── Not signed: progressive activation ───────────────────────────────────────

describe('MandateTab — non signé', () => {
  it('affiche les étapes visibles, la carte docs étant masquée (SHOW_MANDATE_DOCS=false)', () => {
    render(<MandateTab {...makeProps(unSignedState)} />);
    expect(screen.getByText('association.profile.mandate.step1.title')).toBeInTheDocument();
    expect(screen.queryByText('association.profile.mandate.step2.title')).not.toBeInTheDocument();
    expect(screen.getByText('association.profile.mandate.step3.title')).toBeInTheDocument();
  });

  it('offers only the two general-interest eligibility categories (no public utility option)', () => {
    render(<MandateTab {...makeProps(unSignedState)} />);
    expect(screen.getAllByRole('radio').map((r) => (r as HTMLInputElement).value)).toEqual([
      'OIG_66',
      'OIG_75_COLUCHE',
    ]);
  });

  it('bouton signer désactivé sans radio et sans checkbox', () => {
    render(<MandateTab {...makeProps(unSignedState)} />);
    const signBtn = screen.getByRole('button', { name: /association\.profile\.mandate\.step3\.signBtn/i });
    expect(signBtn).toBeDisabled();
  });

  it('bouton signer actif avec radio + checkbox, sans docs (docs non requis)', () => {
    render(<MandateTab {...makeProps(unSignedState)} />);
    const radio = screen.getAllByRole('radio')[0];
    fireEvent.click(radio);
    const checkbox = screen.getByRole('checkbox');
    fireEvent.click(checkbox);
    const signBtn = screen.getByRole('button', { name: /association\.profile\.mandate\.step3\.signBtn/i });
    expect(signBtn).not.toBeDisabled();
  });

  it('bouton signer actif si radio + 2 docs + checkbox', () => {
    render(<MandateTab {...makeProps(unSignedWithDocs)} />);
    const radio = screen.getAllByRole('radio')[0];
    fireEvent.click(radio);
    const checkbox = screen.getByRole('checkbox');
    fireEvent.click(checkbox);
    const signBtn = screen.getByRole('button', { name: /association\.profile\.mandate\.step3\.signBtn/i });
    expect(signBtn).not.toBeDisabled();
  });

  it('bouton signer désactivé si checkbox décochée même avec radio + 2 docs', () => {
    render(<MandateTab {...makeProps(unSignedWithDocs)} />);
    const radio = screen.getAllByRole('radio')[0];
    fireEvent.click(radio);
    const signBtn = screen.getByRole('button', { name: /association\.profile\.mandate\.step3\.signBtn/i });
    expect(signBtn).toBeDisabled();
  });

  it('appelle onSign avec l\'éligibilité et accepted=true', async () => {
    const props = makeProps(unSignedWithDocs);
    render(<MandateTab {...props} />);
    const radio = screen.getAllByRole('radio')[0];
    fireEvent.click(radio);
    const checkbox = screen.getByRole('checkbox');
    fireEvent.click(checkbox);
    const signBtn = screen.getByRole('button', { name: /association\.profile\.mandate\.step3\.signBtn/i });
    await act(async () => { fireEvent.click(signBtn); });
    await waitFor(() =>
      expect(props.onSign).toHaveBeenCalledWith({ eligibility: 'OIG_66', accepted: true })
    );
  });

  // ── Receipt prerequisites gate (mirror of MandateService.signMandate) ──────

  /** Fills a valid signing (eligibility + acceptance) and returns the sign button. */
  function fillSigning() {
    fireEvent.click(screen.getAllByRole('radio')[0]);
    fireEvent.click(screen.getByRole('checkbox'));
    return screen.getByRole('button', { name: /association\.profile\.mandate\.step3\.signBtn/i });
  }

  it('renders the signatory card', () => {
    render(<MandateTab {...makeProps(unSignedState)} />);
    expect(screen.getByTestId('signatory-card')).toBeInTheDocument();
  });

  it('blocks signing and lists every missing prerequisite', async () => {
    const props = makeProps({ ...unSignedWithDocs, receiptPrerequisitesMissing: ALL_PREREQUISITES });
    render(<MandateTab {...props} />);
    const signBtn = fillSigning();
    expect(signBtn).toBeDisabled();
    expect(screen.getByText(`${P}.signGate`)).toBeInTheDocument();
    expect(screen.getByText(`${P}.items.SIGNATURE`)).toBeInTheDocument();
    expect(screen.getByText(`${P}.items.LEGAL_OBJECT`)).toBeInTheDocument();
    expect(screen.getByText(`${P}.items.HEAD_OFFICE_ADDRESS`)).toBeInTheDocument();
    await act(async () => { fireEvent.click(signBtn); });
    expect(props.onSign).not.toHaveBeenCalled();
  });

  it('blocks signing when only the head-office address is missing, and lists only it', () => {
    render(<MandateTab {...makeProps({ ...unSignedWithDocs, receiptPrerequisitesMissing: ['HEAD_OFFICE_ADDRESS'] })} />);
    expect(fillSigning()).toBeDisabled();
    expect(screen.getByText(`${P}.items.HEAD_OFFICE_ADDRESS`)).toBeInTheDocument();
    expect(screen.queryByText(`${P}.items.SIGNATURE`)).not.toBeInTheDocument();
    expect(screen.queryByText(`${P}.items.LEGAL_OBJECT`)).not.toBeInTheDocument();
  });

  it('links the legal object and the address to the Informations tab', () => {
    const props = makeProps({ ...unSignedWithDocs, receiptPrerequisitesMissing: ['LEGAL_OBJECT', 'HEAD_OFFICE_ADDRESS'] });
    render(<MandateTab {...props} />);
    const links = screen.getAllByRole('button', { name: `${P}.links.infos` });
    expect(links).toHaveLength(2);
    fireEvent.click(links[1]);
    expect(props.onGoToInfos).toHaveBeenCalledOnce();
  });

  it('links the missing signature to the signatory card', () => {
    const scrollIntoView = vi.fn();
    const anchor = document.createElement('div');
    anchor.id = 'signatory-card';
    anchor.scrollIntoView = scrollIntoView;
    document.body.appendChild(anchor);
    const props = makeProps({ ...unSignedWithDocs, receiptPrerequisitesMissing: ['SIGNATURE'] });
    render(<MandateTab {...props} />);
    fireEvent.click(screen.getByRole('button', { name: `${P}.links.signatory` }));
    expect(scrollIntoView).toHaveBeenCalledOnce();
    expect(props.onGoToInfos).not.toHaveBeenCalled();
    anchor.remove();
  });

  it('shows no gate when every prerequisite is met', () => {
    render(<MandateTab {...makeProps(unSignedWithDocs)} />);
    expect(fillSigning()).not.toBeDisabled();
    expect(screen.queryByText(`${P}.signGate`)).not.toBeInTheDocument();
  });

  // NOTE: the "Pièces justificatives" card (upload/delete UI + filename display) is hidden while
  // SHOW_MANDATE_DOCS=false. Tests covering onDeleteDoc and the uploaded-filename display were
  // removed with it and should be restored (git history) when the docs step comes back.
});

// ── Signed view ───────────────────────────────────────────────────────────────

describe('MandateTab — signé', () => {
  it('affiche la bannière verte et la référence', () => {
    render(<MandateTab {...makeProps(signedState)} />);
    expect(screen.getByText(/association\.profile\.mandate\.signed\.title/)).toBeInTheDocument();
    expect(screen.getByText('MND-2026-0001')).toBeInTheDocument();
  });

  it('n\'affiche pas les étapes du flux', () => {
    render(<MandateTab {...makeProps(signedState)} />);
    expect(screen.queryByText('association.profile.mandate.step1.title')).not.toBeInTheDocument();
  });

  it('keeps the signatory card visible once the mandate is signed', () => {
    render(<MandateTab {...makeProps(signedState)} />);
    expect(screen.getByTestId('signatory-card')).toBeInTheDocument();
  });

  it('warns that collection is suspended and lists the missing prerequisites', () => {
    render(<MandateTab {...makeProps({ ...signedState, receiptPrerequisitesMissing: ['SIGNATURE', 'LEGAL_OBJECT'] })} />);
    expect(screen.getByRole('alert')).toHaveTextContent(`${P}.suspendedBanner`);
    expect(screen.getByText(`${P}.items.SIGNATURE`)).toBeInTheDocument();
    expect(screen.getByText(`${P}.items.LEGAL_OBJECT`)).toBeInTheDocument();
    expect(screen.queryByText(`${P}.items.HEAD_OFFICE_ADDRESS`)).not.toBeInTheDocument();
  });

  it('shows no suspension banner when every prerequisite is met', () => {
    render(<MandateTab {...makeProps(signedState)} />);
    expect(screen.queryByText(`${P}.suspendedBanner`)).not.toBeInTheDocument();
  });

  it('appelle onDownloadPdf au clic PDF', async () => {
    const props = makeProps(signedState);
    render(<MandateTab {...props} />);
    const pdfBtn = screen.getByRole('button', { name: /association\.profile\.mandate\.signed\.downloadPdf/i });
    await act(async () => { fireEvent.click(pdfBtn); });
    await waitFor(() => expect(props.onDownloadPdf).toHaveBeenCalledOnce());
  });

  it('ouvre le modal de révocation au clic du bouton révoquer', () => {
    render(<MandateTab {...makeProps(signedState)} />);
    const revokeBtn = screen.getByRole('button', { name: /association\.profile\.mandate\.signed\.revokeBtn/i });
    fireEvent.click(revokeBtn);
    expect(screen.getByText('association.profile.mandate.revokeModal.title')).toBeInTheDocument();
  });

  it('appelle onRevoke au clic de confirmation du modal', async () => {
    const props = makeProps(signedState);
    render(<MandateTab {...props} />);
    const revokeBtn = screen.getByRole('button', { name: /association\.profile\.mandate\.signed\.revokeBtn/i });
    fireEvent.click(revokeBtn);
    const confirmBtn = screen.getByRole('button', { name: /association\.profile\.mandate\.revokeModal\.confirm/i });
    await act(async () => { fireEvent.click(confirmBtn); });
    await waitFor(() => expect(props.onRevoke).toHaveBeenCalledOnce());
  });

  it('ferme le modal au clic Annuler sans appeler onRevoke', async () => {
    const props = makeProps(signedState);
    render(<MandateTab {...props} />);
    fireEvent.click(screen.getByRole('button', { name: /association\.profile\.mandate\.signed\.revokeBtn/i }));
    fireEvent.click(screen.getByRole('button', { name: /association\.profile\.mandate\.revokeModal\.cancel/i }));
    expect(screen.queryByText('association.profile.mandate.revokeModal.title')).not.toBeInTheDocument();
    expect(props.onRevoke).not.toHaveBeenCalled();
  });
});
