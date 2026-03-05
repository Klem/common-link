import { describe, it, expect, vi } from 'vitest';
import { render, screen, fireEvent } from '@testing-library/react';
import { ViewTabs } from '../ViewTabs';

vi.mock('next-intl', () => ({
  useTranslations: () => (key: string) => key,
}));

describe('ViewTabs', () => {
  it('renders two tab buttons', () => {
    render(<ViewTabs value="login" onChange={vi.fn()} />);
    expect(screen.getAllByRole('button')).toHaveLength(2);
  });

  it('calls onChange when a tab is clicked', () => {
    const onChange = vi.fn();
    render(<ViewTabs value="login" onChange={onChange} />);
    fireEvent.click(screen.getAllByRole('button')[1]); // signup tab
    expect(onChange).toHaveBeenCalledWith('signup');
  });
});
