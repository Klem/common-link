import { describe, it, expect, vi } from 'vitest';
import { render, screen, fireEvent } from '@testing-library/react';
import { RoleToggle } from '../RoleToggle';

vi.mock('next-intl', () => ({
  useTranslations: () => (key: string) => key,
}));

describe('RoleToggle', () => {
  it('renders two role buttons', () => {
    render(<RoleToggle value="ASSOCIATION" onChange={vi.fn()} />);
    expect(screen.getAllByRole('button')).toHaveLength(2);
  });

  it('calls onChange with the selected role', () => {
    const onChange = vi.fn();
    render(<RoleToggle value="ASSOCIATION" onChange={onChange} />);
    const buttons = screen.getAllByRole('button');
    fireEvent.click(buttons[1]); // DONOR button
    expect(onChange).toHaveBeenCalledWith('DONOR');
  });
});
