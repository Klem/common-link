import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import MollieOnboardingGuide from '../MollieOnboardingGuide';
import { BeneficialOwnerType } from '@/types/admin';

// Translator mock: "key" or "key {json of non-function values}" so counts and names are assertable.
vi.mock('next-intl', () => {
  const format = (key: string, values?: Record<string, unknown>) => {
    const plain = Object.fromEntries(Object.entries(values ?? {}).filter(([, v]) => typeof v !== 'function'));
    return Object.keys(plain).length ? `${key} ${JSON.stringify(plain)}` : key;
  };
  const t = Object.assign((key: string, values?: Record<string, unknown>) => format(key, values), {
    rich: (key: string, values?: Record<string, unknown>) => format(key, values),
  });
  return { useTranslations: () => t };
});

const writeText = vi.fn().mockResolvedValue(undefined);

beforeEach(() => {
  writeText.mockClear();
  Object.assign(navigator, { clipboard: { writeText } });
});

describe('MollieOnboardingGuide — 10-step translator (S4)', () => {
  it('walks through the 10 screens of the hosted wizard, in order', () => {
    render(<MollieOnboardingGuide />);

    const titles = screen.getAllByText(/^steps\..*\.title$/).map((n) => n.textContent);
    expect(titles).toEqual([
      'steps.account.title',
      'steps.legalForm.title',
      'steps.organization.title',
      'steps.registration.title',
      'steps.activity.title',
      'steps.publicInterest.title',
      'steps.stakeholders.title',
      'steps.identity.title',
      'steps.bank.title',
      'steps.review.title',
    ]);
  });

  it('badges steps 1-3 as pre-filled and the public-interest step as to be confirmed', () => {
    const { container } = render(<MollieOnboardingGuide siren="775672272" />);

    expect(container.querySelectorAll('.tr-step.prefilled')).toHaveLength(3);
    expect(screen.getAllByText('prefilled')).toHaveLength(3);
    expect(screen.getByText('toConfirm')).toBeInTheDocument();
  });

  it('shows the D14 legal form instruction and the D8 fees warning', () => {
    render(<MollieOnboardingGuide />);

    expect(screen.getByText('steps.legalForm.p1')).toBeInTheDocument();
    expect(screen.getByText('steps.legalForm.warning')).toBeInTheDocument();
    expect(screen.getByText('steps.activity.fees')).toBeInTheDocument();
  });

  it('expands a step on click and marks it as current', () => {
    const { container } = render(<MollieOnboardingGuide />);

    const head = screen.getByRole('button', { name: /steps\.legalForm\.title/ });
    expect(head).toHaveAttribute('aria-expanded', 'false');
    fireEvent.click(head);
    expect(head).toHaveAttribute('aria-expanded', 'true');
    expect(container.querySelector('.tr-step.current')).toContainElement(head);
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

    fireEvent.click(screen.getByRole('button', { name: /steps\.account\.title/ }));
    fireEvent.click(screen.getAllByRole('button', { name: /^copyAria/ })[0]);
    await waitFor(() => expect(writeText).toHaveBeenCalledWith('Marie Dupont'));
  });

  it('offers a SIREN copy row only for a valid SIREN, never an RNA', () => {
    const { rerender } = render(<MollieOnboardingGuide siren="775672272" />);
    expect(screen.getByText('values.siren')).toBeInTheDocument();

    rerender(<MollieOnboardingGuide siren="W751004076" />);
    expect(screen.queryByText('values.siren')).not.toBeInTheDocument();
  });

  it('lists the people to declare, one identity document each', () => {
    render(
      <MollieOnboardingGuide
        people={[
          { name: 'Claire Martin', role: 'Présidente', type: BeneficialOwnerType.REPRESENTATIVE },
          { name: 'Karim Benali', role: null, type: BeneficialOwnerType.BENEFICIAL_OWNER },
        ]}
      />,
    );

    expect(screen.getByText('steps.stakeholders.p1 {"count":2}')).toBeInTheDocument();
    expect(screen.getByText('steps.identity.p1 {"count":2}')).toBeInTheDocument();
    expect(screen.getByText('Claire Martin')).toBeInTheDocument();
    expect(screen.getByText('Présidente')).toBeInTheDocument();
    expect(screen.getByText('values.person')).toBeInTheDocument();
  });

  it('quotes the association name in the bank step only when known', () => {
    const { rerender } = render(<MollieOnboardingGuide name="Les Amis" />);
    expect(screen.getByText('steps.bank.p1 {"name":"Les Amis"}')).toBeInTheDocument();

    rerender(<MollieOnboardingGuide name="  " />);
    expect(screen.getByText('steps.bank.p1WithoutName')).toBeInTheDocument();
  });

  it('omits a value we do not hold rather than showing an empty row', () => {
    const { container } = render(<MollieOnboardingGuide name={null} addressLine1="   " />);

    expect(container.querySelector('.copy-row')).toBeNull();
  });
});
