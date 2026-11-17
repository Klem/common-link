import { describe, it, expect, vi } from 'vitest';
import { act, fireEvent, render, screen } from '@testing-library/react';
import { CampaignFundsCard } from '../CampaignFundsCard';
import type { CampaignCashOut, CampaignFunds } from '@/types/campaignFunds';

vi.mock('next-intl', () => ({
  useTranslations: (ns: string) => (key: string, values?: Record<string, unknown>) =>
    values ? `${ns}.${key} ${JSON.stringify(values)}` : `${ns}.${key}`,
  useFormatter: () => ({ relativeTime: () => 'il y a 5 minutes', dateTime: () => '09:15' }),
}));

const T = 'dashboard.campaigns.payments.funds';

const cashOut = (over: Partial<CampaignCashOut> = {}): CampaignCashOut => ({
  idCashOut: 60001, cashOutDate: '2026-11-10', netTotal: 1001, campaignAmount: 30, state: 'IN_TRANSIT',
  autoAvailableOn: '2026-11-12', confirmedAt: null, confirmedBy: null, ...over,
});

const funds = (over: Partial<CampaignFunds> = {}): CampaignFunds => ({
  provider: 'HELLOASSO', raised: 165, atProvider: 40, payoutPreparing: 25, inTransit: 30, available: 70, withdrawn: 0,
  paidOut: 50, pendingPayouts: 0, disbursable: 20, cashOuts: [cashOut()], manualReleases: [],
  lastSyncAt: '2026-11-12T09:00:00Z', syncError: false, syncErrorCode: null, nextRefreshAvailableAt: null, ...over,
});

function renderCard(f: CampaignFunds | null, extra: Partial<Parameters<typeof CampaignFundsCard>[0]> = {}) {
  const props = { funds: f, isLoading: false, error: false, refreshing: false, onRefresh: vi.fn(), onOpenReceipt: vi.fn(), ...extra };
  render(<CampaignFundsCard {...props} />);
  return props;
}

const legendButton = (step: string) => screen.getByRole('button', { name: new RegExp(`${T}.steps.${step}.label`) });

describe('CampaignFundsCard', () => {
  it('is hidden for a Mollie association without HelloAsso donation on its way', () => {
    const { container } = render(
      <CampaignFundsCard funds={funds({ provider: 'MOLLIE', atProvider: 0, payoutPreparing: 0, inTransit: 0, cashOuts: [] })}
        isLoading={false} error={false} refreshing={false} onRefresh={vi.fn()} onOpenReceipt={vi.fn()} />,
    );
    expect(container).toBeEmptyDOMElement();
  });

  it('stays visible for a Mollie association with HelloAsso donations still in transit', () => {
    renderCard(funds({ provider: 'MOLLIE' }));
    expect(screen.getByText(`${T}.title`, { exact: false })).toBeInTheDocument();
  });

  it('shows a skeleton on first load', () => {
    renderCard(null, { isLoading: true });
    expect(document.querySelectorAll('.funds-skel')).toHaveLength(3);
  });

  it('partly in transit: four steps, the cue, and the receipt button opens the dialog', () => {
    const props = renderCard(funds());
    expect(document.querySelectorAll('.funds-bar span')).toHaveLength(4);
    expect(screen.getByText(`${T}.toConfirm {"count":1}`)).toBeInTheDocument();

    fireEvent.click(legendButton('inTransit'));
    fireEvent.click(screen.getByRole('button', { name: `${T}.tips.inTransit.confirm` }));

    expect(props.onOpenReceipt).toHaveBeenCalledWith(expect.objectContaining({ idCashOut: 60001 }));
    expect(screen.getByText(/inTransit.ofTotal/)).toBeInTheDocument();
  });

  it('a bar segment click pins its tooltip, a second click unpins it', () => {
    renderCard(funds());
    const segment = document.querySelector('.funds-seg-transit') as HTMLElement;
    const tip = document.getElementById('funds-tip-inTransit')!;
    fireEvent.mouseDown(segment);
    fireEvent.click(segment);
    fireEvent.mouseLeave(segment);
    expect(tip).toBeVisible();
    fireEvent.mouseDown(segment);
    fireEvent.click(segment);
    expect(tip).not.toBeVisible();
  });

  it('opening the receipt dialog keeps "En route" open, so the focus can come back to it', () => {
    renderCard(funds());
    act(() => legendButton('inTransit').focus());
    const confirm = screen.getByRole('button', { name: `${T}.tips.inTransit.confirm` });
    act(() => confirm.focus());
    fireEvent.click(confirm);
    act(() => (document.activeElement as HTMLElement).blur());
    expect(document.getElementById('funds-tip-inTransit')).toBeVisible();
  });

  it('steps at 0 € are hidden from bar and legend, except the arrived one', () => {
    renderCard(funds({ atProvider: 0, payoutPreparing: 0, inTransit: 0, available: 0, cashOuts: [], disbursable: 0 }));
    expect(document.querySelectorAll('.funds-bar span')).toHaveLength(1);
    expect(screen.queryByRole('button', { name: new RegExp('steps.atProvider') })).not.toBeInTheDocument();
    expect(legendButton('available')).toBeInTheDocument();
    expect(document.querySelector('.funds-total.zero')).not.toBeNull();
  });

  it('nothing available: the total takes the alert style', () => {
    renderCard(funds({ disbursable: 0 }));
    expect(document.querySelector('.funds-total')).toHaveClass('zero');
  });

  it('everything arrived: only the arrived step, its history with every confirmation mode and the releases', () => {
    renderCard(funds({
      atProvider: 0, payoutPreparing: 0, inTransit: 0,
      cashOuts: [
        cashOut({ idCashOut: 1, state: 'AVAILABLE', confirmedBy: 'MANUAL', confirmedAt: '2026-11-11T09:00:00Z' }),
        cashOut({ idCashOut: 2, state: 'AVAILABLE', confirmedBy: 'CURATOR_RELEASE', confirmedAt: '2026-11-11T09:00:00Z', netTotal: null }),
      ],
      manualReleases: [{ releasedAt: '2026-11-05T10:00:00Z', campaignAmount: 12 }],
    }));
    expect(document.querySelectorAll('.funds-bar span')).toHaveLength(1);
    expect(document.querySelector('.funds-cue')).toBeNull();

    fireEvent.click(legendButton('available'));
    expect(screen.getByText(/available.confirmedManual/)).toBeInTheDocument();
    expect(screen.getByText(/available.confirmedCurator/)).toBeInTheDocument();
    expect(screen.getByText(`${T}.tips.available.releaseSub`)).toBeInTheDocument();
    expect(screen.queryByText(`${T}.tips.available.none`)).not.toBeInTheDocument();
  });

  it('arrived without payout list but with donations available does not say nothing arrived', () => {
    renderCard(funds({ cashOuts: [], available: 70 }));
    fireEvent.click(legendButton('available'));
    expect(screen.queryByText(`${T}.tips.available.none`)).not.toBeInTheDocument();
  });

  it('the HelloAsso link opens HelloAsso in a new tab', () => {
    renderCard(funds());
    fireEvent.click(legendButton('atProvider'));
    const link = screen.getByRole('link', { name: `${T}.tips.atProvider.link` });
    expect(link).toHaveAttribute('href', 'https://admin.helloasso.com');
    expect(link).toHaveAttribute('target', '_blank');
  });

  it('a tracking failure shows a banner over the last data', () => {
    renderCard(funds({ syncError: true }));
    expect(screen.getByText(`${T}.syncError.title`)).toBeInTheDocument();
    expect(screen.getByText(/syncError.since/)).toBeInTheDocument();
    expect(document.querySelector('.funds-bar')).not.toBeNull();
  });

  it('a read failure without data offers to refresh', () => {
    const props = renderCard(null, { error: true });
    fireEvent.click(screen.getByRole('button', { name: `${T}.refresh` }));
    expect(props.onRefresh).toHaveBeenCalled();
  });

  it('refresh: offered, hidden while running, replaced by the next possible time', () => {
    const props = renderCard(funds());
    fireEvent.click(screen.getByRole('button', { name: `${T}.refresh` }));
    expect(props.onRefresh).toHaveBeenCalledTimes(1);
  });

  it('no refresh button while a request runs', () => {
    renderCard(funds(), { refreshing: true });
    expect(screen.getByText(`${T}.refreshing`)).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: `${T}.refresh` })).not.toBeInTheDocument();
  });

  it('no refresh button before the next refresh the server allows', () => {
    renderCard(funds({ nextRefreshAvailableAt: new Date(Date.now() + 600_000).toISOString() }));
    expect(document.querySelector('.funds-sync')).toHaveTextContent(`${T}.nextRefresh {"time":"09:15"}`);
    expect(screen.queryByRole('button', { name: `${T}.refresh` })).not.toBeInTheDocument();
  });
});
