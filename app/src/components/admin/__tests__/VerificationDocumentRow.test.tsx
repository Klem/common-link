import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import { VerificationDocumentRow } from '../VerificationDocumentRow';

// ── Mocks ────────────────────────────────────────────────────────────────────

vi.mock('next-intl', () => ({
  useTranslations: () => (key: string) => key,
}));

const mockDownload = vi.fn();
vi.mock('@/lib/api/admin', () => ({
  downloadVerificationDocument: (...args: unknown[]) => mockDownload(...args),
}));

const mockAddToast = vi.fn();
vi.mock('@/stores/toastStore', () => ({
  useToastStore: (sel: (s: { addToast: typeof mockAddToast }) => unknown) =>
    sel({ addToast: mockAddToast }),
}));

vi.mock('@/components/admin/adminShared', () => ({
  DOC_TYPE_I18N_KEY: {
    VERIF_STATUTS: 'docType.verifStatuts',
    VERIF_RNA_RECEIPT: 'docType.verifRnaReceipt',
    VERIF_REPRESENTATIVE_ID: 'docType.verifRepresentativeId',
  },
}));

// Stub URL APIs
const revokeObjectURL = vi.fn();
const createObjectURL = vi.fn(() => 'blob:http://localhost/abc');
Object.defineProperty(global, 'URL', {
  value: { createObjectURL, revokeObjectURL },
  writable: true,
});

// ── Fixtures ─────────────────────────────────────────────────────────────────

const ASSOC_ID = 'assoc-123';
const DOC_ID = 'doc-456';

// ── Tests ────────────────────────────────────────────────────────────────────

describe('VerificationDocumentRow — required variant', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    mockDownload.mockResolvedValue({ blob: new Blob(['x'], { type: 'application/pdf' }), fileName: 'test.pdf' });
  });

  it('shows missing badge and no action button for an unuploaded required slot', () => {
    render(
      <VerificationDocumentRow
        variant="required"
        associationId={ASSOC_ID}
        slot={{ docType: 'VERIF_STATUTS', uploaded: false }}
      />,
    );
    expect(screen.getByText('verificationDetail.doc.missing')).toBeInTheDocument();
    expect(screen.queryByText('verificationDetail.doc.view')).not.toBeInTheDocument();
    expect(screen.queryByText('↓')).not.toBeInTheDocument();
  });

  it('shows view and download buttons for an uploaded required slot', () => {
    render(
      <VerificationDocumentRow
        variant="required"
        associationId={ASSOC_ID}
        slot={{
          docType: 'VERIF_RNA_RECEIPT',
          uploaded: true,
          id: DOC_ID,
          fileName: 'rna.pdf',
          sizeBytes: 50000,
          uploadedAt: '2026-01-01T00:00:00Z',
        }}
      />,
    );
    expect(screen.getByText('verificationDetail.doc.view')).toBeInTheDocument();
    expect(screen.getByTitle('verificationDetail.doc.download')).toBeInTheDocument();
  });

  it('calls downloadVerificationDocument and revokes URL when download clicked', async () => {
    render(
      <VerificationDocumentRow
        variant="required"
        associationId={ASSOC_ID}
        slot={{
          docType: 'VERIF_STATUTS',
          uploaded: true,
          id: DOC_ID,
          fileName: 'statuts.pdf',
          sizeBytes: 10000,
        }}
      />,
    );

    fireEvent.click(screen.getByTitle('verificationDetail.doc.download'));

    await waitFor(() => {
      expect(mockDownload).toHaveBeenCalledWith(ASSOC_ID, DOC_ID);
      expect(revokeObjectURL).toHaveBeenCalledWith('blob:http://localhost/abc');
    });
  });

  it('shows error toast when download fails', async () => {
    mockDownload.mockRejectedValue(new Error('Network error'));

    render(
      <VerificationDocumentRow
        variant="required"
        associationId={ASSOC_ID}
        slot={{ docType: 'VERIF_STATUTS', uploaded: true, id: DOC_ID, fileName: 'x.pdf' }}
      />,
    );

    fireEvent.click(screen.getByTitle('verificationDetail.doc.download'));

    await waitFor(() => {
      expect(mockAddToast).toHaveBeenCalledWith('error', 'admin.verificationDetail.doc.downloadError');
    });
  });
});

describe('VerificationDocumentRow — optional variant', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    mockDownload.mockResolvedValue({ blob: new Blob(['x'], { type: 'image/png' }), fileName: 'photo.png' });
  });

  it('always shows view and download buttons (optional docs are always uploaded)', () => {
    render(
      <VerificationDocumentRow
        variant="optional"
        associationId={ASSOC_ID}
        doc={{
          id: DOC_ID,
          fileName: 'photo.png',
          category: 'REPORT',
          contentType: 'image/png',
          sizeBytes: 20000,
          uploadedAt: '2026-03-01T00:00:00Z',
        }}
      />,
    );
    expect(screen.getByText('verificationDetail.doc.view')).toBeInTheDocument();
    expect(screen.getByTitle('verificationDetail.doc.download')).toBeInTheDocument();
  });

  it('calls downloadVerificationDocument for optional doc', async () => {
    render(
      <VerificationDocumentRow
        variant="optional"
        associationId={ASSOC_ID}
        doc={{
          id: DOC_ID,
          fileName: 'photo.png',
          category: null,
          contentType: 'image/png',
          sizeBytes: 20000,
          uploadedAt: '2026-03-01T00:00:00Z',
        }}
      />,
    );

    fireEvent.click(screen.getByTitle('verificationDetail.doc.download'));

    await waitFor(() => {
      expect(mockDownload).toHaveBeenCalledWith(ASSOC_ID, DOC_ID);
    });
  });
});
