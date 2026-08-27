import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import { LegalDocumentModal } from '../LegalDocumentModal';
import { LegalDocumentType } from '@/types/legal';

vi.mock('next-intl', () => ({
  useTranslations: () => (key: string) => key,
}));

vi.mock('@/lib/api/public', () => ({
  getLegalDocument: vi.fn(),
}));

import { getLegalDocument } from '@/lib/api/public';

const mockGetLegalDocument = getLegalDocument as ReturnType<typeof vi.fn>;

const sampleDoc = {
  documentType: 'CGU',
  version: '2026-08-26',
  content: 'Texte des CGU.',
  publishedAt: '2026-08-26T00:00:00Z',
};

describe('LegalDocumentModal', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it('shows a loading state, then the fetched content and version', async () => {
    mockGetLegalDocument.mockResolvedValue(sampleDoc);
    render(<LegalDocumentModal documentType={LegalDocumentType.CGU} onClose={vi.fn()} />);

    expect(screen.getByText('loading')).toBeInTheDocument();

    await waitFor(() => {
      expect(screen.getByText('Texte des CGU.')).toBeInTheDocument();
    });
    expect(getLegalDocument).toHaveBeenCalledWith('CGU');
  });

  it('shows an error message when the fetch fails', async () => {
    mockGetLegalDocument.mockRejectedValue(new Error('network'));
    render(<LegalDocumentModal documentType={LegalDocumentType.CGU} onClose={vi.fn()} />);

    await waitFor(() => {
      expect(screen.getByText('loadError')).toBeInTheDocument();
    });
  });

  it('calls onClose when the header close button is clicked', async () => {
    mockGetLegalDocument.mockResolvedValue(sampleDoc);
    const onClose = vi.fn();
    render(<LegalDocumentModal documentType={LegalDocumentType.CGU} onClose={onClose} />);
    await waitFor(() => screen.getByText('Texte des CGU.'));

    // Header (×) and footer ("close") buttons share the same accessible name under the test's
    // literal-key next-intl mock — assert on the specific element, not by ambiguous role+name.
    fireEvent.click(document.querySelector('.modal-close')!);

    expect(onClose).toHaveBeenCalled();
  });

  it('calls onClose when the footer close button is clicked', async () => {
    mockGetLegalDocument.mockResolvedValue(sampleDoc);
    const onClose = vi.fn();
    render(<LegalDocumentModal documentType={LegalDocumentType.CGU} onClose={onClose} />);
    await waitFor(() => screen.getByText('Texte des CGU.'));

    fireEvent.click(document.querySelector('.modal-footer button')!);

    expect(onClose).toHaveBeenCalled();
  });

  it('calls onClose on Escape', async () => {
    mockGetLegalDocument.mockResolvedValue(sampleDoc);
    const onClose = vi.fn();
    render(<LegalDocumentModal documentType={LegalDocumentType.CGU} onClose={onClose} />);
    await waitFor(() => screen.getByText('Texte des CGU.'));

    fireEvent.keyDown(window, { key: 'Escape' });

    expect(onClose).toHaveBeenCalled();
  });

  it('calls onClose on backdrop click but not on a click inside the dialog', async () => {
    mockGetLegalDocument.mockResolvedValue(sampleDoc);
    const onClose = vi.fn();
    render(<LegalDocumentModal documentType={LegalDocumentType.CGV} onClose={onClose} />);
    await waitFor(() => screen.getByText('Texte des CGU.'));

    fireEvent.click(screen.getByText('Texte des CGU.'));
    expect(onClose).not.toHaveBeenCalled();

    fireEvent.click(screen.getByRole('dialog'));
    expect(onClose).toHaveBeenCalled();
  });

  it('does not check any acceptance checkbox itself — purely informational', async () => {
    mockGetLegalDocument.mockResolvedValue(sampleDoc);
    render(<LegalDocumentModal documentType={LegalDocumentType.CGU} onClose={vi.fn()} />);
    await waitFor(() => screen.getByText('Texte des CGU.'));

    expect(screen.queryByRole('checkbox')).toBeNull();
  });
});
