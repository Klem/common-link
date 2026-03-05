import { describe, it, expect, vi } from 'vitest';
import { render, screen } from '@testing-library/react';
import { AssoProfileForm } from '../AssoProfileForm';
import type { AssoResult } from '../AssoSearch';

vi.mock('next-intl', () => ({
  useTranslations: () => (key: string) => key,
}));

const mockAsso: AssoResult = {
  siren: '123456789',
  nom: 'Association Test',
  ville: 'Paris',
  codePostal: '75001',
  etat: 'A',
};

describe('AssoProfileForm', () => {
  it('renders pre-filled association name', () => {
    render(<AssoProfileForm asso={mockAsso} onSubmit={vi.fn()} />);
    const inputs = screen.getAllByDisplayValue('Association Test');
    expect(inputs.length).toBeGreaterThan(0);
  });

  it('renders the SIREN field pre-filled', () => {
    render(<AssoProfileForm asso={mockAsso} onSubmit={vi.fn()} />);
    expect(screen.getByDisplayValue('123456789')).toBeInTheDocument();
  });

  it('submit button is initially disabled', () => {
    render(<AssoProfileForm asso={mockAsso} onSubmit={vi.fn()} />);
    expect(screen.getByRole('button', { name: /signup\.association\.profile\.submit/i })).toBeDisabled();
  });
});
