import { describe, it, expect, vi, beforeEach } from 'vitest';
import { act, render, screen, fireEvent, waitFor } from '@testing-library/react';
import { AssociationLegalGate } from '../AssociationLegalGate';

vi.mock('next-intl', () => ({
  useTranslations: () => (key: string) => key,
}));

vi.mock('@/lib/api/legal', async (importOriginal) => ({
  // Keeps the real isLegalVersionOutdated: the 409 branch is part of what is tested.
  ...(await importOriginal<typeof import('@/lib/api/legal')>()),
  getLegalAcceptanceState: vi.fn(),
  acceptLegalDocument: vi.fn(),
}));

vi.mock('@/lib/api/public', () => ({
  getLegalDocument: vi.fn(),
}));

import { acceptLegalDocument, getLegalAcceptanceState } from '@/lib/api/legal';
import { getLegalDocument } from '@/lib/api/public';

function doc(version: string, content: string) {
  return {
    documentType: 'CGV' as const,
    version,
    format: 'TEXT' as const,
    content,
    publishedAt: '2026-11-16T00:00:00Z',
  };
}

function state(accepted: boolean, currentVersion = 'v3') {
  return { documentType: 'CGV' as const, currentVersion, accepted };
}

beforeEach(() => {
  vi.clearAllMocks();
});

describe('AssociationLegalGate', () => {
  it('renders nothing and loads no text when the current version is already accepted', async () => {
    vi.mocked(getLegalAcceptanceState).mockResolvedValue(state(true));

    const { container } = render(<AssociationLegalGate />);
    await act(async () => {});

    expect(container).toBeEmptyDOMElement();
    expect(getLegalDocument).not.toHaveBeenCalled();
  });

  it('fails open when the state cannot be read', async () => {
    vi.mocked(getLegalAcceptanceState).mockRejectedValue(new Error('network'));

    const { container } = render(<AssociationLegalGate />);
    await act(async () => {});

    expect(container).toBeEmptyDOMElement();
  });

  it('shows the current text and accepts only once the box is ticked', async () => {
    vi.mocked(getLegalAcceptanceState).mockResolvedValue(state(false));
    vi.mocked(getLegalDocument).mockResolvedValue(doc('v3', 'Texte v3.'));
    vi.mocked(acceptLegalDocument).mockResolvedValue(state(true));

    render(<AssociationLegalGate />);
    await waitFor(() => expect(screen.getByText('Texte v3.')).toBeInTheDocument());

    const accept = screen.getByRole('button', { name: 'gate.accept' });
    expect(accept).toBeDisabled();
    expect(screen.queryByRole('button', { name: 'close' })).not.toBeInTheDocument();

    fireEvent.click(screen.getByRole('checkbox', { name: 'gate.checkbox' }));
    fireEvent.click(accept);

    await waitFor(() => expect(screen.queryByText('Texte v3.')).not.toBeInTheDocument());
    expect(acceptLegalDocument).toHaveBeenCalledWith('CGV', 'v3');
  });

  it('reloads the newer text and asks again on 409 LEGAL_VERSION_OUTDATED', async () => {
    vi.mocked(getLegalAcceptanceState).mockResolvedValue(state(false));
    vi.mocked(getLegalDocument)
      .mockResolvedValueOnce(doc('v3', 'Texte v3.'))
      .mockResolvedValueOnce(doc('v4', 'Texte v4.'));
    vi.mocked(acceptLegalDocument).mockRejectedValue({ response: { status: 409, data: { code: 'LEGAL_VERSION_OUTDATED' } } });

    render(<AssociationLegalGate />);
    await waitFor(() => screen.getByText('Texte v3.'));
    fireEvent.click(screen.getByRole('checkbox', { name: 'gate.checkbox' }));
    fireEvent.click(screen.getByRole('button', { name: 'gate.accept' }));

    await waitFor(() => expect(screen.getByText('Texte v4.')).toBeInTheDocument());
    expect(screen.getByText('outdated')).toBeInTheDocument();
    expect((screen.getByRole('checkbox', { name: 'gate.checkbox' }) as HTMLInputElement).checked).toBe(false);
  });

  it('keeps the gate open with an error when the acceptance fails otherwise', async () => {
    vi.mocked(getLegalAcceptanceState).mockResolvedValue(state(false));
    vi.mocked(getLegalDocument).mockResolvedValue(doc('v3', 'Texte v3.'));
    vi.mocked(acceptLegalDocument).mockRejectedValue(new Error('network'));

    render(<AssociationLegalGate />);
    await waitFor(() => screen.getByText('Texte v3.'));
    fireEvent.click(screen.getByRole('checkbox', { name: 'gate.checkbox' }));
    fireEvent.click(screen.getByRole('button', { name: 'gate.accept' }));

    await waitFor(() => expect(screen.getByText('gate.error')).toBeInTheDocument());
    expect(screen.getByText('Texte v3.')).toBeInTheDocument();
  });
});
