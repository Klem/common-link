import { describe, it, expect, vi } from 'vitest';
import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import { ReportForm } from '../ReportForm';
import { reportCampaign } from '@/lib/api/public';

vi.mock('next-intl', () => ({
  useTranslations: () => (key: string) => key,
}));

vi.mock('@/lib/api/public', () => ({
  reportCampaign: vi.fn(),
}));

describe('ReportForm', () => {
  it('disables submit until a message is entered', () => {
    render(<ReportForm widgetToken="clk_abc" />);
    expect(screen.getByText('submit')).toBeDisabled();

    fireEvent.change(screen.getByLabelText(/messageLabel/), { target: { value: 'Contenu problématique' } });
    expect(screen.getByText('submit')).not.toBeDisabled();
  });

  it('submits the message and optional e-mail, then shows the confirmation', async () => {
    vi.mocked(reportCampaign).mockResolvedValueOnce(undefined);
    render(<ReportForm widgetToken="clk_abc" />);

    fireEvent.change(screen.getByLabelText(/messageLabel/), { target: { value: 'Contenu problématique' } });
    fireEvent.change(screen.getByLabelText(/emailLabel/), { target: { value: 'temoin@example.org' } });
    fireEvent.click(screen.getByText('submit'));

    await waitFor(() => expect(screen.getByText('success')).toBeInTheDocument());
    expect(reportCampaign).toHaveBeenCalledWith('clk_abc', {
      message: 'Contenu problématique',
      reporterEmail: 'temoin@example.org',
    });
  });

  it('omits reporterEmail entirely when left blank', async () => {
    vi.mocked(reportCampaign).mockResolvedValueOnce(undefined);
    render(<ReportForm widgetToken="clk_abc" />);

    fireEvent.change(screen.getByLabelText(/messageLabel/), { target: { value: 'Contenu problématique' } });
    fireEvent.click(screen.getByText('submit'));

    await waitFor(() => expect(reportCampaign).toHaveBeenCalled());
    expect(reportCampaign).toHaveBeenCalledWith('clk_abc', {
      message: 'Contenu problématique',
      reporterEmail: undefined,
    });
  });

  it('shows an error and keeps the form when the request fails', async () => {
    vi.mocked(reportCampaign).mockRejectedValueOnce(new Error('network'));
    render(<ReportForm widgetToken="clk_abc" />);

    fireEvent.change(screen.getByLabelText(/messageLabel/), { target: { value: 'Contenu problématique' } });
    fireEvent.click(screen.getByText('submit'));

    await waitFor(() => expect(screen.getByText('error')).toBeInTheDocument());
    expect(screen.getByLabelText(/messageLabel/)).toBeInTheDocument();
  });
});
