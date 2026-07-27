import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, fireEvent, waitFor, act } from '@testing-library/react';
import { MandateTab } from '@/components/settings/MandateTab';
import type { MandateStateDto } from '@/types/mandate';

// ── Mocks ────────────────────────────────────────────────────────────────────

vi.mock('next-intl', () => ({
  useTranslations: () => (key: string) => key,
}));

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
  signed: false, blocked: true, mandateDocs: emptyDocs,
};

const unSignedState: MandateStateDto = {
  signed: false, blocked: false, mandateDocs: emptyDocs,
};

const unSignedWithDocs: MandateStateDto = {
  signed: false, blocked: false, mandateDocs: fullDocs,
};

const signedState: MandateStateDto = {
  signed: true,
  blocked: false,
  reference: 'MND-2026-0001',
  signedAt: '2026-07-08T10:00:00Z',
  eligibility: 'OIG_66',
  revokedAt: null,
  mandateDocs: fullDocs,
};

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

// ── État bloqué ──────────────────────────────────────────────────────────────

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
});

// ── Non signé : activation progressive ───────────────────────────────────────

describe('MandateTab — non signé', () => {
  it('affiche les étapes visibles, la carte docs étant masquée (SHOW_MANDATE_DOCS=false)', () => {
    render(<MandateTab {...makeProps(unSignedState)} />);
    expect(screen.getByText('association.profile.mandate.step1.title')).toBeInTheDocument();
    expect(screen.queryByText('association.profile.mandate.step2.title')).not.toBeInTheDocument();
    expect(screen.getByText('association.profile.mandate.step3.title')).toBeInTheDocument();
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

  // NOTE: the "Pièces justificatives" card (upload/delete UI + filename display) is hidden while
  // SHOW_MANDATE_DOCS=false. Tests covering onDeleteDoc and the uploaded-filename display were
  // removed with it and should be restored (git history) when the docs step comes back.
});

// ── Vue signée ────────────────────────────────────────────────────────────────

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
