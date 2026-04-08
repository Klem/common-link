import { describe, it, expect, vi } from 'vitest';
import { render, screen } from '@testing-library/react';
import { VopBanner } from '../VopBanner';

vi.mock('next-intl', () => ({
  useTranslations:
    () =>
    (key: string, params?: Record<string, string>) =>
      params ? `${key}:${JSON.stringify(params)}` : key,
}));

describe('VopBanner', () => {
  // ── MATCH ──────────────────────────────────────────────────────────────────

  it('renders green banner for MATCH', () => {
    const { container } = render(<VopBanner vopResult="MATCH" />);

    const div = container.firstChild as HTMLElement;
    expect(div).toHaveClass('bg-green/8');
    expect(div).toHaveClass('border-green');
    expect(div).toHaveClass('text-green');
  });

  it('renders check mark and translation key for MATCH', () => {
    render(<VopBanner vopResult="MATCH" />);

    expect(screen.getByText(/beneficiaries\.iban\.vop\.match/)).toBeInTheDocument();
  });

  // ── CLOSE_MATCH ────────────────────────────────────────────────────────────

  it('renders yellow banner for CLOSE_MATCH', () => {
    const { container } = render(
      <VopBanner vopResult="CLOSE_MATCH" suggestedName="Dupont Jean" />,
    );

    const div = container.firstChild as HTMLElement;
    expect(div).toHaveClass('bg-yellow/8');
    expect(div).toHaveClass('border-yellow');
    expect(div).toHaveClass('text-yellow');
  });

  it('includes suggested name in CLOSE_MATCH message', () => {
    render(<VopBanner vopResult="CLOSE_MATCH" suggestedName="Dupont Jean" />);

    expect(
      screen.getByText(/beneficiaries\.iban\.vop\.closeMatch/),
    ).toBeInTheDocument();
    expect(
      screen.getByText((text) => text.includes('Dupont Jean')),
    ).toBeInTheDocument();
  });

  // ── NO_MATCH ───────────────────────────────────────────────────────────────

  it('renders red banner for NO_MATCH', () => {
    const { container } = render(<VopBanner vopResult="NO_MATCH" />);

    const div = container.firstChild as HTMLElement;
    expect(div).toHaveClass('bg-red/8');
    expect(div).toHaveClass('border-red');
    expect(div).toHaveClass('text-red');
  });

  it('renders cross and translation key for NO_MATCH', () => {
    render(<VopBanner vopResult="NO_MATCH" />);

    expect(screen.getByText(/beneficiaries\.iban\.vop\.noMatch/)).toBeInTheDocument();
  });

  // ── NOT_POSSIBLE ───────────────────────────────────────────────────────────

  it('renders muted banner for NOT_POSSIBLE', () => {
    const { container } = render(<VopBanner vopResult="NOT_POSSIBLE" />);

    const div = container.firstChild as HTMLElement;
    expect(div).toHaveClass('bg-muted/8');
    expect(div).toHaveClass('border-muted');
    expect(div).toHaveClass('text-text-2');
  });

  it('renders question mark and translation key for NOT_POSSIBLE', () => {
    render(<VopBanner vopResult="NOT_POSSIBLE" />);

    expect(screen.getByText(/beneficiaries\.iban\.vop\.notPossible/)).toBeInTheDocument();
  });

  // ── Shared structure ───────────────────────────────────────────────────────

  it('always renders with shared base classes', () => {
    const { container } = render(<VopBanner vopResult="MATCH" />);

    const div = container.firstChild as HTMLElement;
    expect(div).toHaveClass('rounded-[8px]');
    expect(div).toHaveClass('p-[10px]');
    expect(div).toHaveClass('text-[12px]');
    expect(div).toHaveClass('mt-[6px]');
  });
});
