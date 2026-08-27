import { describe, it, expect, vi } from 'vitest';
import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import { ReportCampaignModal } from '../ReportCampaignModal';
import { reportCampaign } from '@/lib/api/public';

vi.mock('next-intl', () => ({
  useTranslations: () => (key: string) => key,
}));

vi.mock('@/lib/api/public', () => ({
  reportCampaign: vi.fn(),
}));

describe('ReportCampaignModal', () => {
  it('renders nothing when closed', () => {
    const { container } = render(
      <ReportCampaignModal isOpen={false} widgetToken="clk_abc" onClose={vi.fn()} />
    );
    expect(container).toBeEmptyDOMElement();
  });

  it('disables submit until a message is entered', () => {
    render(<ReportCampaignModal isOpen widgetToken="clk_abc" onClose={vi.fn()} />);
    expect(screen.getByText('report.submit')).toBeDisabled();

    fireEvent.change(screen.getByLabelText(/report.messageLabel/), { target: { value: 'Contenu problématique' } });
    expect(screen.getByText('report.submit')).not.toBeDisabled();
  });

  it('submits the message and optional e-mail, then shows the confirmation', async () => {
    vi.mocked(reportCampaign).mockResolvedValueOnce(undefined);
    render(<ReportCampaignModal isOpen widgetToken="clk_abc" onClose={vi.fn()} />);

    fireEvent.change(screen.getByLabelText(/report.messageLabel/), { target: { value: 'Contenu problématique' } });
    fireEvent.change(screen.getByLabelText(/report.emailLabel/), { target: { value: 'temoin@example.org' } });
    fireEvent.click(screen.getByText('report.submit'));

    await waitFor(() => expect(screen.getByText('report.success')).toBeInTheDocument());
    expect(reportCampaign).toHaveBeenCalledWith('clk_abc', {
      message: 'Contenu problématique',
      reporterEmail: 'temoin@example.org',
    });
  });

  it('omits reporterEmail entirely when left blank', async () => {
    vi.mocked(reportCampaign).mockResolvedValueOnce(undefined);
    render(<ReportCampaignModal isOpen widgetToken="clk_abc" onClose={vi.fn()} />);

    fireEvent.change(screen.getByLabelText(/report.messageLabel/), { target: { value: 'Contenu problématique' } });
    fireEvent.click(screen.getByText('report.submit'));

    await waitFor(() => expect(reportCampaign).toHaveBeenCalled());
    expect(reportCampaign).toHaveBeenCalledWith('clk_abc', {
      message: 'Contenu problématique',
      reporterEmail: undefined,
    });
  });

  it('shows an error and keeps the form when the request fails', async () => {
    vi.mocked(reportCampaign).mockRejectedValueOnce(new Error('network'));
    render(<ReportCampaignModal isOpen widgetToken="clk_abc" onClose={vi.fn()} />);

    fireEvent.change(screen.getByLabelText(/report.messageLabel/), { target: { value: 'Contenu problématique' } });
    fireEvent.click(screen.getByText('report.submit'));

    await waitFor(() => expect(screen.getByText('report.error')).toBeInTheDocument());
    expect(screen.getByLabelText(/report.messageLabel/)).toBeInTheDocument();
  });

  it('calls onClose when the cancel button is clicked', () => {
    const onClose = vi.fn();
    render(<ReportCampaignModal isOpen widgetToken="clk_abc" onClose={onClose} />);
    fireEvent.click(screen.getByText('report.cancel'));
    expect(onClose).toHaveBeenCalled();
  });
});
