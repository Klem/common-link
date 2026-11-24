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

function doc(version: string, content: string, documentType: 'CGV' | 'PRIVACY' = 'CGV') {
  return {
    documentType,
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
    // The CGV is pending, the privacy policy already accepted: accepting the CGV closes the gate.
    const accepted = new Set<string>(['PRIVACY']);
    vi.mocked(getLegalAcceptanceState).mockImplementation(async (type) => state(accepted.has(type)));
    vi.mocked(getLegalDocument).mockResolvedValue(doc('v3', 'Texte v3.'));
    vi.mocked(acceptLegalDocument).mockImplementation(async (type) => {
      accepted.add(type);
      return state(true);
    });

    render(<AssociationLegalGate />);
    await waitFor(() => expect(screen.getByText('Texte v3.')).toBeInTheDocument());

    const accept = screen.getByRole('button', { name: 'gate.accept.CGV' });
    expect(accept).toBeDisabled();
    expect(screen.queryByRole('button', { name: 'close' })).not.toBeInTheDocument();

    fireEvent.click(screen.getByRole('checkbox', { name: 'gate.checkbox.CGV' }));
    fireEvent.click(accept);

    await waitFor(() => expect(screen.queryByText('Texte v3.')).not.toBeInTheDocument());
    expect(acceptLegalDocument).toHaveBeenCalledWith('CGV', 'v3');
  });

  it('asks for the association terms, then the privacy policy, one after the other', async () => {
    const accepted = new Set<string>();
    vi.mocked(getLegalAcceptanceState).mockImplementation(async (type) => state(accepted.has(type)));
    vi.mocked(getLegalDocument).mockImplementation(async (type) =>
      type === 'CGV' ? doc('v3', 'Conditions v3.') : doc('p1', 'Confidentialité p1.', 'PRIVACY'),
    );
    vi.mocked(acceptLegalDocument).mockImplementation(async (type) => {
      accepted.add(type);
      return state(true);
    });

    render(<AssociationLegalGate />);
    await waitFor(() => screen.getByText('Conditions v3.'));
    fireEvent.click(screen.getByRole('checkbox', { name: 'gate.checkbox.CGV' }));
    fireEvent.click(screen.getByRole('button', { name: 'gate.accept.CGV' }));

    await waitFor(() => expect(screen.getByText('Confidentialité p1.')).toBeInTheDocument());
    expect(acceptLegalDocument).toHaveBeenLastCalledWith('CGV', 'v3');
    // The privacy policy is acknowledged, not accepted: its own intro, checkbox and button wording.
    expect(screen.getByText('gate.intro.PRIVACY')).toBeInTheDocument();
    const checkbox = screen.getByRole('checkbox', { name: 'gate.checkbox.PRIVACY' }) as HTMLInputElement;
    expect(checkbox.checked).toBe(false);
    fireEvent.click(checkbox);
    fireEvent.click(screen.getByRole('button', { name: 'gate.accept.PRIVACY' }));

    await waitFor(() => expect(screen.queryByText('Confidentialité p1.')).not.toBeInTheDocument());
    expect(acceptLegalDocument).toHaveBeenLastCalledWith('PRIVACY', 'p1');
  });

  it('reloads the newer text and asks again on 409 LEGAL_VERSION_OUTDATED', async () => {
    vi.mocked(getLegalAcceptanceState).mockResolvedValue(state(false));
    vi.mocked(getLegalDocument)
      .mockResolvedValueOnce(doc('v3', 'Texte v3.'))
      .mockResolvedValueOnce(doc('v4', 'Texte v4.'));
    vi.mocked(acceptLegalDocument).mockRejectedValue({ response: { status: 409, data: { code: 'LEGAL_VERSION_OUTDATED' } } });

    render(<AssociationLegalGate />);
    await waitFor(() => screen.getByText('Texte v3.'));
    fireEvent.click(screen.getByRole('checkbox', { name: 'gate.checkbox.CGV' }));
    fireEvent.click(screen.getByRole('button', { name: 'gate.accept.CGV' }));

    await waitFor(() => expect(screen.getByText('Texte v4.')).toBeInTheDocument());
    expect(screen.getByText('outdated')).toBeInTheDocument();
    expect((screen.getByRole('checkbox', { name: 'gate.checkbox.CGV' }) as HTMLInputElement).checked).toBe(false);
  });

  it('keeps the gate open with an error when the acceptance fails otherwise', async () => {
    vi.mocked(getLegalAcceptanceState).mockResolvedValue(state(false));
    vi.mocked(getLegalDocument).mockResolvedValue(doc('v3', 'Texte v3.'));
    vi.mocked(acceptLegalDocument).mockRejectedValue(new Error('network'));

    render(<AssociationLegalGate />);
    await waitFor(() => screen.getByText('Texte v3.'));
    fireEvent.click(screen.getByRole('checkbox', { name: 'gate.checkbox.CGV' }));
    fireEvent.click(screen.getByRole('button', { name: 'gate.accept.CGV' }));

    await waitFor(() => expect(screen.getByText('gate.error')).toBeInTheDocument());
    expect(screen.getByText('Texte v3.')).toBeInTheDocument();
  });
});
