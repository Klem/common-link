import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, fireEvent, waitFor, act } from '@testing-library/react';
import { VerificationTab } from '@/components/settings/VerificationTab';
import type { VerificationStateDto, OptionalDocumentDto } from '@/types/verification';

// ── Mocks ────────────────────────────────────────────────────────────────────

vi.mock('next-intl', () => ({
  useTranslations: () => (key: string) => key,
}));

const mockUploadRequired = vi.fn().mockResolvedValue(undefined);
const mockDeleteRequired = vi.fn().mockResolvedValue(undefined);
const mockSubmitDossier = vi.fn().mockResolvedValue(undefined);
const mockUploadOptional = vi.fn().mockResolvedValue(undefined);
const mockDeleteOptional = vi.fn().mockResolvedValue(undefined);

let mockState: VerificationStateDto = { status: 'UNVERIFIED', requiredDocuments: [] };
let mockOptDocs: OptionalDocumentDto[] = [];

vi.mock('@/hooks/dashboard/useVerification', () => ({
  useVerification: () => ({
    state: mockState,
    optionalDocs: mockOptDocs,
    isLoading: false,
    uploadRequired: mockUploadRequired,
    deleteRequired: mockDeleteRequired,
    submitDossier: mockSubmitDossier,
    uploadOptional: mockUploadOptional,
    deleteOptional: mockDeleteOptional,
  }),
}));

// ── Fixtures ─────────────────────────────────────────────────────────────────

const emptySlots: VerificationStateDto['requiredDocuments'] = [
  { docType: 'VERIF_STATUTS', uploaded: false },
  { docType: 'VERIF_RNA_RECEIPT', uploaded: false },
  { docType: 'VERIF_REPRESENTATIVE_ID', uploaded: false },
];

const fullSlots: VerificationStateDto['requiredDocuments'] = [
  { docType: 'VERIF_STATUTS', uploaded: true, id: 'id1', fileName: 'statuts.pdf', sizeBytes: 102400, uploadedAt: '2026-07-01T10:00:00Z' },
  { docType: 'VERIF_RNA_RECEIPT', uploaded: true, id: 'id2', fileName: 'rna.pdf', sizeBytes: 51200, uploadedAt: '2026-07-01T10:01:00Z' },
  { docType: 'VERIF_REPRESENTATIVE_ID', uploaded: true, id: 'id3', fileName: 'cni.jpg', sizeBytes: 204800, uploadedAt: '2026-07-01T10:02:00Z' },
];

const sampleOptDocs: OptionalDocumentDto[] = [
  { id: 'opt1', fileName: 'rapport-2024.pdf', category: 'rapport', contentType: 'application/pdf', sizeBytes: 1048576, uploadedAt: '2024-12-31T00:00:00Z' },
];

// ── Tests ────────────────────────────────────────────────────────────────────

describe('VerificationTab — banner states', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    mockOptDocs = [];
  });

  it('VERIFIED : affiche le titre vérifié et pas de bouton submit', () => {
    mockState = { status: 'VERIFIED', requiredDocuments: fullSlots };
    render(<VerificationTab />);
    expect(screen.getByText('verification.banner.verified.title')).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: /verification\.submit/i })).not.toBeInTheDocument();
  });

  it('PENDING : bouton submit désactivé, texte "pending.btn"', () => {
    mockState = { status: 'PENDING', requiredDocuments: fullSlots };
    render(<VerificationTab />);
    expect(screen.getByText('verification.banner.pending.title')).toBeInTheDocument();
    const btn = screen.getByRole('button', { name: /verification\.banner\.pending\.btn/i });
    expect(btn).toBeDisabled();
  });

  it('REJECTED : motif affiché dans le sous-titre de la bannière', () => {
    mockState = { status: 'REJECTED', rejectionReason: 'Documents illisibles', requiredDocuments: emptySlots };
    render(<VerificationTab />);
    expect(screen.getByText('verification.banner.rejected.title')).toBeInTheDocument();
    expect(screen.getByText('Documents illisibles')).toBeInTheDocument();
  });

  it('REJECTED + 3 docs : bouton submit activé (resoumettre)', () => {
    mockState = { status: 'REJECTED', rejectionReason: 'Statuts expiré', requiredDocuments: fullSlots };
    render(<VerificationTab />);
    const btn = screen.getByRole('button', { name: /verification\.submit/i });
    expect(btn).not.toBeDisabled();
  });

  it('UNVERIFIED + 0 docs : bouton submit désactivé', () => {
    mockState = { status: 'UNVERIFIED', requiredDocuments: emptySlots };
    render(<VerificationTab />);
    expect(screen.getByText('verification.banner.incomplete.title')).toBeInTheDocument();
    const btn = screen.getByRole('button', { name: /verification\.submit/i });
    expect(btn).toBeDisabled();
  });

  it('UNVERIFIED + 3 docs : bouton submit activé', () => {
    mockState = { status: 'UNVERIFIED', requiredDocuments: fullSlots };
    render(<VerificationTab />);
    expect(screen.getByText('verification.banner.ready.title')).toBeInTheDocument();
    const btn = screen.getByRole('button', { name: /verification\.submit/i });
    expect(btn).not.toBeDisabled();
  });
});

describe('VerificationTab — submit action', () => {
  beforeEach(() => vi.clearAllMocks());

  it('appelle submitDossier au clic du bouton actif', async () => {
    mockState = { status: 'UNVERIFIED', requiredDocuments: fullSlots };
    mockOptDocs = [];
    render(<VerificationTab />);
    const btn = screen.getByRole('button', { name: /verification\.submit/i });
    await act(async () => { fireEvent.click(btn); });
    await waitFor(() => expect(mockSubmitDossier).toHaveBeenCalledOnce());
  });
});

describe('VerificationTab — document slots', () => {
  beforeEach(() => { vi.clearAllMocks(); mockOptDocs = []; });

  it('affiche les 3 slots requis', () => {
    mockState = { status: 'UNVERIFIED', requiredDocuments: emptySlots };
    render(<VerificationTab />);
    expect(screen.getByText('verification.docs.statuts.label')).toBeInTheDocument();
    expect(screen.getByText('verification.docs.rna.label')).toBeInTheDocument();
    expect(screen.getByText('verification.docs.idrep.label')).toBeInTheDocument();
  });

  it('slot uploadé : affiche le nom de fichier et les boutons Replace/Delete', () => {
    mockState = { status: 'UNVERIFIED', requiredDocuments: fullSlots };
    render(<VerificationTab />);
    expect(screen.getByText(/statuts\.pdf/)).toBeInTheDocument();
    expect(screen.getAllByRole('button', { name: /verification\.requiredDocs\.replace/i })).toHaveLength(3);
    expect(screen.getAllByRole('button', { name: /verification\.requiredDocs\.delete/i })).toHaveLength(3);
  });

  it('PENDING : boutons replace/delete désactivés', () => {
    mockState = { status: 'PENDING', requiredDocuments: fullSlots };
    render(<VerificationTab />);
    screen.getAllByRole('button', { name: /verification\.requiredDocs\.replace/i })
      .forEach((btn) => expect(btn).toBeDisabled());
  });

  it('appelle deleteRequired au clic Supprimer sur un slot', async () => {
    mockState = { status: 'UNVERIFIED', requiredDocuments: fullSlots };
    render(<VerificationTab />);
    const delBtns = screen.getAllByRole('button', { name: /verification\.requiredDocs\.delete/i });
    await act(async () => { fireEvent.click(delBtns[0]); });
    await waitFor(() => expect(mockDeleteRequired).toHaveBeenCalledWith('VERIF_STATUTS'));
  });
});

describe('VerificationTab — optional docs', () => {
  beforeEach(() => vi.clearAllMocks());

  it('liste les documents facultatifs', () => {
    mockState = { status: 'UNVERIFIED', requiredDocuments: emptySlots };
    mockOptDocs = sampleOptDocs;
    render(<VerificationTab />);
    expect(screen.getByText('rapport-2024.pdf')).toBeInTheDocument();
  });

  it('appelle deleteOptional au clic Supprimer', async () => {
    mockState = { status: 'UNVERIFIED', requiredDocuments: emptySlots };
    mockOptDocs = sampleOptDocs;
    render(<VerificationTab />);
    const delBtn = screen.getByRole('button', { name: /verification\.optDocs\.delete/i });
    await act(async () => { fireEvent.click(delBtn); });
    await waitFor(() => expect(mockDeleteOptional).toHaveBeenCalledWith('opt1'));
  });

  it('affiche le message vide si aucun doc facultatif', () => {
    mockState = { status: 'UNVERIFIED', requiredDocuments: emptySlots };
    mockOptDocs = [];
    render(<VerificationTab />);
    expect(screen.getByText('verification.optDocs.empty')).toBeInTheDocument();
  });
});
