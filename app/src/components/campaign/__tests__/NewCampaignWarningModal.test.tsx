import { describe, it, expect, vi } from 'vitest';
import { render, screen } from '@testing-library/react';
import { NewCampaignWarningModal } from '../NewCampaignWarningModal';
import { VerificationStatus } from '@/types/association';

vi.mock('next-intl', () => ({
  useTranslations: () => (key: string) => key,
}));

function renderModal(verificationStatus: VerificationStatus) {
  return render(
    <NewCampaignWarningModal
      verificationStatus={verificationStatus}
      onClose={vi.fn()}
      onContinue={vi.fn()}
      onGoVerify={vi.fn()}
    />,
  );
}

describe('NewCampaignWarningModal', () => {
  /** Regression guard: every non-verified status used to render the same "not yet verified" line. */
  it.each([
    VerificationStatus.UNVERIFIED,
    VerificationStatus.PENDING,
    VerificationStatus.REJECTED,
  ])('renders the message matching the %s status', (status) => {
    renderModal(status);

    expect(screen.getByText(`status.${status}`)).toBeInTheDocument();
  });

  it('offers the verification call-to-action when the association can act on its dossier', () => {
    renderModal(VerificationStatus.UNVERIFIED);

    expect(screen.getByText('verify')).toBeInTheDocument();
  });

  it('hides the verification call-to-action while the dossier is under review', () => {
    renderModal(VerificationStatus.PENDING);

    expect(screen.queryByText('verify')).not.toBeInTheDocument();
  });
});
