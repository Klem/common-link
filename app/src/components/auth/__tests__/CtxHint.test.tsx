import { describe, it, expect, vi } from 'vitest';
import { render, screen } from '@testing-library/react';
import { CtxHint } from '../CtxHint';

vi.mock('next-intl', () => ({
  useTranslations: () => (key: string) => key,
}));

describe('CtxHint', () => {
  it('renders association variant with green border', () => {
    const { container } = render(<CtxHint variant="association" />);
    const el = container.firstChild as HTMLElement;
    expect(el.className).toContain('[border-left-color:var(--color-green)]');
  });

  it('renders donor variant with yellow border', () => {
    const { container } = render(<CtxHint variant="donor" />);
    const el = container.firstChild as HTMLElement;
    expect(el.className).toContain('[border-left-color:var(--color-yellow)]');
  });

  it('displays the hint text', () => {
    render(<CtxHint variant="association" />);
    expect(screen.getByText('hints.association')).toBeInTheDocument();
  });
});
