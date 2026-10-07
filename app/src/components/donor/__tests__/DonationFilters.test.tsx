import { describe, it, expect, vi } from 'vitest';
import { render, screen, fireEvent } from '@testing-library/react';
import { DonationFilters } from '../DonationFilters';
import type { AssociationOptionDto } from '@/types/donor';

vi.mock('next-intl', () => ({
  useTranslations: () => (key: string) => key,
}));

const associations: AssociationOptionDto[] = [
  { id: 'asso-1', name: 'Terre Verte' },
  { id: 'asso-2', name: 'Solidarité' },
];

describe('DonationFilters', () => {
  it('renders both selects with an associated label', () => {
    render(
      <DonationFilters
        associations={associations}
        years={[2025, 2026]}
        associationId={null}
        year={null}
        onAssociationChange={vi.fn()}
        onYearChange={vi.fn()}
      />,
    );
    expect(screen.getByLabelText('association')).toBeInTheDocument();
    expect(screen.getByLabelText('year')).toBeInTheDocument();
  });

  it('lists all associations as options', () => {
    render(
      <DonationFilters
        associations={associations}
        years={[2025, 2026]}
        associationId={null}
        year={null}
        onAssociationChange={vi.fn()}
        onYearChange={vi.fn()}
      />,
    );
    expect(screen.getByText('Terre Verte')).toBeInTheDocument();
    expect(screen.getByText('Solidarité')).toBeInTheDocument();
  });

  it('calls onAssociationChange when selecting an association', () => {
    const onAssociationChange = vi.fn();
    render(
      <DonationFilters
        associations={associations}
        years={[2025, 2026]}
        associationId={null}
        year={null}
        onAssociationChange={onAssociationChange}
        onYearChange={vi.fn()}
      />,
    );
    fireEvent.change(screen.getByLabelText('association'), { target: { value: 'asso-1' } });
    expect(onAssociationChange).toHaveBeenCalledWith('asso-1');
  });

  it('calls onYearChange with a number when selecting a year', () => {
    const onYearChange = vi.fn();
    render(
      <DonationFilters
        associations={associations}
        years={[2025, 2026]}
        associationId={null}
        year={null}
        onAssociationChange={vi.fn()}
        onYearChange={onYearChange}
      />,
    );
    fireEvent.change(screen.getByLabelText('year'), { target: { value: '2025' } });
    expect(onYearChange).toHaveBeenCalledWith(2025);
  });

  it('calls onYearChange with null when resetting to "all years"', () => {
    const onYearChange = vi.fn();
    render(
      <DonationFilters
        associations={associations}
        years={[2025, 2026]}
        associationId={null}
        year={2025}
        onAssociationChange={vi.fn()}
        onYearChange={onYearChange}
      />,
    );
    fireEvent.change(screen.getByLabelText('year'), { target: { value: '' } });
    expect(onYearChange).toHaveBeenCalledWith(null);
  });
});
