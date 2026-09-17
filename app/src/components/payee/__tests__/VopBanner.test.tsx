import { describe, it, expect, vi } from 'vitest';
import { render, screen } from '@testing-library/react';
import { VopBanner } from '../VopBanner';
import { VopResult } from '@/types/payee';

vi.mock('next-intl', () => ({
  useTranslations:
    () =>
    (key: string, params?: Record<string, string>) =>
      params ? `${key}:${JSON.stringify(params)}` : key,
}));

describe('VopBanner', () => {
  // ── MATCH ──────────────────────────────────────────────────────────────────

  it('renders green banner for MATCH', () => {
    const { container } = render(<VopBanner vopResult={VopResult.MATCH} />);

    expect(container.firstChild).toHaveClass('alert-success');
  });

  it('renders check mark and translation key for MATCH', () => {
    render(<VopBanner vopResult={VopResult.MATCH} />);

    expect(screen.getByText(/payees\.iban\.vop\.match/)).toBeInTheDocument();
  });

  // ── CLOSE_MATCH ────────────────────────────────────────────────────────────

  it('renders yellow banner for CLOSE_MATCH', () => {
    const { container } = render(
      <VopBanner vopResult={VopResult.CLOSE_MATCH} suggestedName="Dupont Jean" />,
    );

    expect(container.firstChild).toHaveClass('alert-warning');
  });

  it('includes suggested name in CLOSE_MATCH message', () => {
    render(<VopBanner vopResult={VopResult.CLOSE_MATCH} suggestedName="Dupont Jean" />);

    expect(
      screen.getByText(/payees\.iban\.vop\.closeMatch/),
    ).toBeInTheDocument();
    expect(
      screen.getByText((text) => text.includes('Dupont Jean')),
    ).toBeInTheDocument();
  });

  // ── NO_MATCH ───────────────────────────────────────────────────────────────

  it('renders red banner for NO_MATCH', () => {
    const { container } = render(<VopBanner vopResult={VopResult.NO_MATCH} />);

    expect(container.firstChild).toHaveClass('alert-error');
  });

  it('renders cross and translation key for NO_MATCH', () => {
    render(<VopBanner vopResult={VopResult.NO_MATCH} />);

    expect(screen.getByText(/payees\.iban\.vop\.noMatch/)).toBeInTheDocument();
  });

  // ── NOT_POSSIBLE ───────────────────────────────────────────────────────────

  it('renders muted banner for NOT_POSSIBLE', () => {
    const { container } = render(<VopBanner vopResult={VopResult.NOT_POSSIBLE} />);

    // NOT_POSSIBLE is neither success nor failure, so it carries the one payee-specific
    // modifier rather than an alert-* variant.
    expect(container.firstChild).toHaveClass('payee-alert-neutral');
  });

  it('renders question mark and translation key for NOT_POSSIBLE', () => {
    render(<VopBanner vopResult={VopResult.NOT_POSSIBLE} />);

    expect(screen.getByText(/payees\.iban\.vop\.notPossible/)).toBeInTheDocument();
  });

  // ── Shared structure ───────────────────────────────────────────────────────

  it('always renders on the shared alert primitive', () => {
    // The banner must stay part of the dashboard `alert` family rather than drift back to
    // one-off Tailwind colours, which is what made the payee screens look foreign.
    const { container } = render(<VopBanner vopResult={VopResult.MATCH} />);

    const div = container.firstChild as HTMLElement;
    expect(div).toHaveClass('alert');
    expect(div).toHaveClass('payee-alert-inline');
  });
});
