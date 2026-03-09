import { describe, it, expect } from 'vitest';
import { render, screen } from '@testing-library/react';
import { StepIndicator } from '../StepIndicator';

const steps = ['Recherche', 'Connexion', 'Profil'];

describe('StepIndicator', () => {
  it('renders all step labels', () => {
    render(<StepIndicator steps={steps} currentStep={1} />);
    expect(screen.getByText('Recherche')).toBeInTheDocument();
    expect(screen.getByText('Connexion')).toBeInTheDocument();
    expect(screen.getByText('Profil')).toBeInTheDocument();
  });

  it('shows checkmark for completed steps', () => {
    render(<StepIndicator steps={steps} currentStep={2} />);
    expect(screen.getByText('✓')).toBeInTheDocument();
  });

  it('shows step number for active and pending steps', () => {
    render(<StepIndicator steps={steps} currentStep={2} />);
    expect(screen.getByText('2')).toBeInTheDocument();
    expect(screen.getByText('3')).toBeInTheDocument();
  });
});
