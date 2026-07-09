import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import { VerificationDecisionPanel } from '../VerificationDecisionPanel';
import { VerificationStatus } from '@/types/association';

// ── Mocks ────────────────────────────────────────────────────────────────────

vi.mock('next-intl', () => ({
  useTranslations: () => (key: string) => key,
}));

const mockApprove = vi.fn();
const mockReject = vi.fn();
vi.mock('@/lib/api/admin', () => ({
  approveVerification: (...args: unknown[]) => mockApprove(...args),
  rejectVerification: (...args: unknown[]) => mockReject(...args),
}));

const mockAddToast = vi.fn();
vi.mock('@/stores/toastStore', () => ({
  useToastStore: (sel: (s: { addToast: typeof mockAddToast }) => unknown) =>
    sel({ addToast: mockAddToast }),
}));

vi.mock('@/components/admin/adminShared', () => ({
  STATUS_BADGE_CLASS: {
    PENDING: 'badge badge-warning',
    VERIFIED: 'badge badge-success',
    REJECTED: 'badge badge-error',
    UNVERIFIED: 'badge badge-neutral',
  },
}));

// ── Helpers ───────────────────────────────────────────────────────────────────

const ASSOC_ID = 'assoc-abc';
const noop = vi.fn();

function renderPanel(status: VerificationStatus, props?: object) {
  return render(
    <VerificationDecisionPanel
      associationId={ASSOC_ID}
      status={status}
      onDecisionMade={noop}
      onNeedRefetch={noop}
      {...props}
    />,
  );
}

// ── Tests ────────────────────────────────────────────────────────────────────

describe('VerificationDecisionPanel — PENDING state', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    mockApprove.mockResolvedValue(undefined);
    mockReject.mockResolvedValue(undefined);
  });

  it('shows approve and reject buttons when PENDING', () => {
    renderPanel(VerificationStatus.PENDING);
    expect(screen.getByText('decision.approve')).toBeInTheDocument();
    expect(screen.getByText('decision.reject')).toBeInTheDocument();
  });

  it('approve requires arm confirmation before calling API', async () => {
    renderPanel(VerificationStatus.PENDING);

    // Click approve — arms the confirm
    fireEvent.click(screen.getByText('decision.approve'));
    expect(screen.getByText('decision.confirmApprove')).toBeInTheDocument();
    expect(mockApprove).not.toHaveBeenCalled();

    // Confirm — now calls API
    fireEvent.click(screen.getByText('✓'));
    await waitFor(() => expect(mockApprove).toHaveBeenCalledWith(ASSOC_ID));
    expect(noop).toHaveBeenCalledWith(VerificationStatus.VERIFIED, undefined);
  });

  it('approve can be cancelled from the armed state', () => {
    renderPanel(VerificationStatus.PENDING);
    fireEvent.click(screen.getByText('decision.approve'));
    expect(screen.getByText('decision.confirmApprove')).toBeInTheDocument();

    fireEvent.click(screen.getByText('✕'));
    expect(screen.queryByText('decision.confirmApprove')).not.toBeInTheDocument();
    expect(screen.getByText('decision.approve')).toBeInTheDocument();
  });

  it('reject reveals textarea and submit button', () => {
    renderPanel(VerificationStatus.PENDING);
    fireEvent.click(screen.getByText('decision.reject'));
    expect(screen.getByRole('textbox')).toBeInTheDocument();
    expect(screen.getByText('decision.submit')).toBeInTheDocument();
  });

  it('reject shows zod error when reason is empty', async () => {
    renderPanel(VerificationStatus.PENDING);
    fireEvent.click(screen.getByText('decision.reject'));
    fireEvent.click(screen.getByText('decision.submit'));
    await waitFor(() =>
      expect(screen.getByText('decision.reasonRequired')).toBeInTheDocument(),
    );
    expect(mockReject).not.toHaveBeenCalled();
  });

  it('reject calls API when reason is non-empty', async () => {
    renderPanel(VerificationStatus.PENDING);
    fireEvent.click(screen.getByText('decision.reject'));
    fireEvent.change(screen.getByRole('textbox'), { target: { value: 'Documents illisibles.' } });
    fireEvent.click(screen.getByText('decision.submit'));
    await waitFor(() => expect(mockReject).toHaveBeenCalledWith(ASSOC_ID, 'Documents illisibles.'));
    expect(noop).toHaveBeenCalledWith(VerificationStatus.REJECTED, 'Documents illisibles.');
  });

  it('approve shows notPending toast and calls refetch on 409', async () => {
    mockApprove.mockRejectedValue({ response: { status: 409 } });
    renderPanel(VerificationStatus.PENDING);
    fireEvent.click(screen.getByText('decision.approve'));
    fireEvent.click(screen.getByText('✓'));
    await waitFor(() => {
      expect(mockAddToast).toHaveBeenCalledWith('warning', 'admin.decision.notPending');
      expect(noop).toHaveBeenCalled(); // onNeedRefetch
    });
  });

  it('reject shows notPending toast and calls refetch on 409', async () => {
    mockReject.mockRejectedValue({ response: { status: 409 } });
    renderPanel(VerificationStatus.PENDING);
    fireEvent.click(screen.getByText('decision.reject'));
    fireEvent.change(screen.getByRole('textbox'), { target: { value: 'Motif.' } });
    fireEvent.click(screen.getByText('decision.submit'));
    await waitFor(() => {
      expect(mockAddToast).toHaveBeenCalledWith('warning', 'admin.decision.notPending');
    });
  });
});

describe('VerificationDecisionPanel — non-PENDING state', () => {
  it('shows verified badge and date for VERIFIED status', () => {
    renderPanel(VerificationStatus.VERIFIED, { verifiedAt: '2026-07-09T10:00:00Z' });
    expect(screen.queryByText('decision.approve')).not.toBeInTheDocument();
    expect(screen.queryByText('decision.reject')).not.toBeInTheDocument();
  });

  it('shows rejection reason for REJECTED status', () => {
    renderPanel(VerificationStatus.REJECTED, { rejectionReason: 'Pièce expirée.' });
    expect(screen.getByText('Pièce expirée.')).toBeInTheDocument();
    expect(screen.queryByText('decision.approve')).not.toBeInTheDocument();
  });
});
