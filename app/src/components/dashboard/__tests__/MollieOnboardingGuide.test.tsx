import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import MollieOnboardingGuide from '../MollieOnboardingGuide';

vi.mock('next-intl', () => ({
  useTranslations: () => (key: string) => key,
}));

const writeText = vi.fn().mockResolvedValue(undefined);

beforeEach(() => {
  writeText.mockClear();
  Object.assign(navigator, { clipboard: { writeText } });
});

// jsdom renders the children of a collapsed <details>, so these assertions prove the steps and
// their values are present — not that they are visible before the user expands a step.
describe('MollieOnboardingGuide', () => {
  it('walks through every screen of the hosted wizard, in order', () => {
    render(<MollieOnboardingGuide />);

    const labels = screen.getAllByText(/^steps\..*\.mollieLabel$/).map((n) => n.textContent);
    expect(labels).toEqual([
      'steps.account.mollieLabel',
      'steps.legalForm.mollieLabel',
      'steps.organization.mollieLabel',
      'steps.registration.mollieLabel',
      'steps.activity.mollieLabel',
      'steps.stakeholders.mollieLabel',
      'steps.identity.mollieLabel',
      'steps.bank.mollieLabel',
      'steps.review.mollieLabel',
    ]);
  });

  it('shows the same steps whether or not the association holds a SIREN', () => {
    const { container: withSiren } = render(<MollieOnboardingGuide siren="775672272" />);
    const { container: withoutSiren } = render(<MollieOnboardingGuide identifier="W751004076" />);

    const steps = (root: HTMLElement) =>
      [...root.querySelectorAll('.mollie-guide-label')].map((n) => n.textContent);
    expect(steps(withSiren)).toEqual(steps(withoutSiren));
  });

  it('offers the stored values for copy, so nothing has to be retyped', async () => {
    render(
      <MollieOnboardingGuide
        name="Fondation Lumière"
        addressLine1="12 rue des Lilas"
        postalCode="75011"
        city="Paris"
        contactName="Marie Dupont"
        contactEmail="contact@asso.fr"
        legalObject="Accompagnement scolaire"
      />,
    );

    expect(screen.getByText('Fondation Lumière')).toBeInTheDocument();
    expect(screen.getByText('12 rue des Lilas, 75011 Paris')).toBeInTheDocument();
    expect(screen.getByText('Accompagnement scolaire')).toBeInTheDocument();

    fireEvent.click(screen.getAllByRole('button', { name: 'copy' })[0]);

    await waitFor(() => expect(writeText).toHaveBeenCalledWith('Marie Dupont'));
  });

  it('labels the identifier as a SIREN only when it is one, never an RNA', () => {
    const { rerender } = render(<MollieOnboardingGuide siren="775672272" />);
    expect(screen.getByText('values.siren')).toBeInTheDocument();

    rerender(<MollieOnboardingGuide identifier="W751004076" />);
    expect(screen.getByText('values.rna')).toBeInTheDocument();
    expect(screen.queryByText('values.siren')).not.toBeInTheDocument();
  });

  it('flags as pre-filled only what the client link actually carried', () => {
    // No SIREN means no registrationNumber was sent, so that step is not announced as pre-filled.
    const { container } = render(<MollieOnboardingGuide identifier="W751004076" />);

    expect(container.querySelectorAll('.mollie-guide-badge')).toHaveLength(3);
  });

  it('announces the registration step as pre-filled once a SIREN is known', () => {
    const { container } = render(<MollieOnboardingGuide siren="775672272" />);

    expect(container.querySelectorAll('.mollie-guide-badge')).toHaveLength(4);
  });

  it('omits a value we do not hold rather than showing an empty row', () => {
    const { container } = render(<MollieOnboardingGuide name={null} addressLine1="   " />);

    expect(container.querySelector('.mollie-guide-value')).toBeNull();
  });
});
