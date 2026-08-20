import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import { DonationForm } from '../DonationForm';

vi.mock('next-intl', () => ({
  useTranslations: () => (key: string) => key,
}));

vi.mock('@/lib/api/public', () => ({
  createGuestDonation: vi.fn(),
}));

import { createGuestDonation } from '@/lib/api/public';

const mockCreateGuestDonation = createGuestDonation as ReturnType<typeof vi.fn>;

/**
 * Fills every field the backend still requires.
 *
 * @param birthDate ISO date, or `null` to leave the facultative birth date empty — the case the
 *   widget must accept since the value is only used for the freeze screening and never kept.
 */
function fillValidForm(birthDate: string | null = '1990-01-15') {
  fireEvent.click(screen.getByText('25 €'));
  fireEvent.change(screen.getByLabelText(/identity.email/i), {
    target: { value: 'jean@example.com' },
  });
  fireEvent.change(screen.getByLabelText(/identity.fullName/i), {
    target: { value: 'Jean Dupont' },
  });
  if (birthDate !== null) {
    fireEvent.change(screen.getByLabelText(/identity.birthDate/i), {
      target: { value: birthDate },
    });
  }
  fireEvent.change(screen.getByLabelText(/identity.addressLine1/i), {
    target: { value: '12 rue de la Paix' },
  });
  fireEvent.change(screen.getByLabelText(/identity.postalCode/i), {
    target: { value: '75001' },
  });
  fireEvent.change(screen.getByLabelText(/identity.city/i), {
    target: { value: 'Paris' },
  });
  fireEvent.click(screen.getByLabelText(/consent.label/i));
}

describe('DonationForm — skin default', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    Object.defineProperty(window, 'top', { value: window, writable: true });
  });

  it('renders amount buttons with btn-outline class for unselected amounts', () => {
    render(
      <DonationForm widgetToken="clk_test" sourceSite={null} locale="fr" skin="default" />,
    );
    const btn10 = screen.getByText('10 €');
    expect(btn10.className).toContain('btn-outline');
    expect(btn10.className).not.toContain('btn-primary');
  });

  it('renders selected amount button with btn-primary class', async () => {
    render(
      <DonationForm widgetToken="clk_test" sourceSite={null} locale="fr" skin="default" />,
    );
    fireEvent.click(screen.getByText('25 €'));
    await waitFor(() => {
      expect(screen.getByText('25 €').className).toContain('btn-primary');
      expect(screen.getByText('25 €').className).not.toContain('btn-outline');
    });
  });

  it('uses form-group, form-label, form-input classes for fields', () => {
    render(
      <DonationForm widgetToken="clk_test" sourceSite={null} locale="fr" skin="default" />,
    );
    const emailInput = screen.getByLabelText(/identity.email/i);
    expect(emailInput.className).toContain('form-input');
    expect(emailInput.closest('.form-group')).not.toBeNull();
  });

  it('submit button uses btn-primary class', () => {
    render(
      <DonationForm widgetToken="clk_test" sourceSite={null} locale="fr" skin="default" />,
    );
    const btn = screen.getByRole('button', { name: 'submit' });
    expect(btn.className).toContain('btn-primary');
  });
});

describe('DonationForm — skin landing', () => {
  it('renders amount buttons with lp-amount-btn class', () => {
    render(
      <DonationForm widgetToken="clk_test" sourceSite={null} locale="fr" skin="landing" />,
    );
    expect(screen.getByText('10 €').className).toContain('lp-amount-btn');
  });

  it('selected amount uses lp-amount-btn--active class', async () => {
    render(
      <DonationForm widgetToken="clk_test" sourceSite={null} locale="fr" skin="landing" />,
    );
    fireEvent.click(screen.getByText('50 €'));
    await waitFor(() => {
      expect(screen.getByText('50 €').className).toContain('lp-amount-btn--active');
    });
  });

  it('submit button uses lp-submit-btn class', () => {
    render(
      <DonationForm widgetToken="clk_test" sourceSite={null} locale="fr" skin="landing" />,
    );
    const btn = screen.getByRole('button', { name: 'submit' });
    expect(btn.className).toContain('lp-submit-btn');
  });
});

describe('DonationForm — 409 blocked state', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    Object.defineProperty(window, 'top', { value: window, writable: true });
  });

  it('shows errors.notCollecting and disables form on 409 response', async () => {
    mockCreateGuestDonation.mockRejectedValue({ response: { status: 409 } });
    render(
      <DonationForm widgetToken="clk_test" sourceSite={null} locale="fr" skin="default" />,
    );

    fillValidForm();
    fireEvent.click(screen.getByRole('button', { name: 'submit' }));

    await waitFor(() => {
      expect(screen.getByText('errors.notCollecting')).toBeInTheDocument();
    });

    expect(screen.getByRole('button', { name: 'submit' })).toBeDisabled();
    expect(screen.getByText('25 €')).toBeDisabled();
  });

  it('shows errors.submitFailed on generic error', async () => {
    mockCreateGuestDonation.mockRejectedValue(new Error('network'));
    render(
      <DonationForm widgetToken="clk_test" sourceSite={null} locale="fr" skin="default" />,
    );

    fillValidForm();
    fireEvent.click(screen.getByRole('button', { name: 'submit' }));

    await waitFor(() => {
      expect(screen.getByText('errors.submitFailed')).toBeInTheDocument();
    });

    expect(screen.getByRole('button', { name: 'submit' })).not.toBeDisabled();
  });
});

describe('DonationForm — submitLabel prop', () => {
  it('uses custom submitLabel when amount is selected', async () => {
    render(
      <DonationForm
        widgetToken="clk_test"
        sourceSite={null}
        locale="fr"
        skin="default"
        submitLabel={(amount) => (amount ? `Donate ${amount}€` : undefined)}
      />,
    );
    fireEvent.click(screen.getByText('50 €'));
    await waitFor(() => {
      expect(screen.getByRole('button', { name: 'Donate 50€' })).toBeInTheDocument();
    });
  });

  it('falls back to submit key when submitLabel returns undefined', () => {
    render(
      <DonationForm
        widgetToken="clk_test"
        sourceSite={null}
        locale="fr"
        skin="default"
        submitLabel={() => undefined}
      />,
    );
    expect(screen.getByRole('button', { name: 'submit' })).toBeInTheDocument();
  });
});

/**
 * Collection cap: a donation above what the campaign may still take must be refused *before* the
 * payment, never refunded after. The backend is the authority (`COLLECTION_CAP_EXCEEDED`); the form
 * only spares the donor a wasted round trip.
 */
describe('DonationForm — collection cap', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    Object.defineProperty(window, 'top', { value: window, writable: true });
  });

  it('leaves the cap entirely to the backend when no capacity is provided', () => {
    render(<DonationForm widgetToken="clk_test" sourceSite={null} locale="fr" skin="default" />);

    expect(screen.queryByText('amounts.remaining')).not.toBeInTheDocument();
    expect(screen.getByText('100 €')).not.toBeDisabled();
  });

  it('disables the presets above the remaining capacity', () => {
    render(
      <DonationForm
        widgetToken="clk_test"
        sourceSite={null}
        locale="fr"
        skin="default"
        remainingCapacity={30}
      />,
    );

    expect(screen.getByText('25 €')).not.toBeDisabled();
    expect(screen.getByText('50 €')).toBeDisabled();
    expect(screen.getByText('100 €')).toBeDisabled();
  });

  it('blocks the submit and explains why when the amount exceeds the capacity', async () => {
    render(
      <DonationForm
        widgetToken="clk_test"
        sourceSite={null}
        locale="fr"
        skin="default"
        remainingCapacity={30}
      />,
    );

    fireEvent.change(screen.getByLabelText(/amounts.custom/i), { target: { value: '80' } });

    await waitFor(() => {
      expect(screen.getByText('errors.capExceeded')).toBeInTheDocument();
    });
    expect(screen.getByRole('button', { name: 'submit' })).toBeDisabled();
  });

  it('renders a full campaign inert', () => {
    render(
      <DonationForm
        widgetToken="clk_test"
        sourceSite={null}
        locale="fr"
        skin="default"
        remainingCapacity={0}
      />,
    );

    expect(screen.getByText('errors.capFull')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'submit' })).toBeDisabled();
    expect(screen.getByText('10 €')).toBeDisabled();
  });

  /**
   * Landing preview of an unpublished campaign: the goal may still be zero, so the capacity is zero
   * too. Saying "this campaign has reached its cap" there would recreate the confusion the `disabled`
   * state was introduced to prevent.
   */
  it('says nothing about capacity in preview mode', () => {
    render(
      <DonationForm
        widgetToken="clk_test"
        sourceSite={null}
        locale="fr"
        skin="landing"
        disabled
        remainingCapacity={0}
      />,
    );

    expect(screen.queryByText('errors.capFull')).not.toBeInTheDocument();
    expect(screen.queryByText('amounts.remaining')).not.toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'submit' })).toBeDisabled();
  });

  /**
   * A cap refusal is not "this association cannot collect": the donor can succeed with a lower
   * amount, so the form must stay live instead of going inert like the generic 409 does.
   */
  it('keeps the form live after a COLLECTION_CAP_EXCEEDED refusal', async () => {
    mockCreateGuestDonation.mockRejectedValue({
      response: { status: 409, data: { code: 'COLLECTION_CAP_EXCEEDED', remainingCapacity: 12 } },
    });
    render(<DonationForm widgetToken="clk_test" sourceSite={null} locale="fr" skin="default" />);

    fillValidForm();
    fireEvent.click(screen.getByRole('button', { name: 'submit' }));

    await waitFor(() => {
      expect(screen.getByText('errors.capExceeded')).toBeInTheDocument();
    });
    expect(screen.queryByText('errors.notCollecting')).not.toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'submit' })).not.toBeDisabled();
    expect(screen.getByText('25 €')).not.toBeDisabled();
  });
});

describe('DonationForm — disabled prop (landing preview)', () => {
  /** Every control a donor can touch. */
  function controls(): HTMLElement[] {
    return [
      ...screen.getAllByRole('button'),
      ...screen.getAllByRole('textbox'),
      ...screen.getAllByRole('checkbox'),
      screen.getByLabelText(/amounts.custom/i),
    ];
  }

  it('leaves every control enabled by default', () => {
    render(<DonationForm widgetToken="clk_test" sourceSite={null} locale="fr" skin="landing" />);

    expect(controls().every((el) => !(el as HTMLInputElement).disabled)).toBe(true);
  });

  it('disables every control when disabled is set', () => {
    // Preview of an unpublished campaign: the backend refuses the payment (409), so nothing in the
    // form may be clickable — an association clicking a live button would think its page is broken.
    render(
      <DonationForm widgetToken="clk_test" sourceSite={null} locale="fr" skin="landing" disabled />,
    );

    expect(controls().every((el) => (el as HTMLInputElement).disabled)).toBe(true);
  });

  it('keeps the skin classes while disabled', () => {
    render(
      <DonationForm widgetToken="clk_test" sourceSite={null} locale="fr" skin="landing" disabled />,
    );

    const btn = screen.getByRole('button', { name: 'submit' });
    expect(btn.className).toContain('lp-submit-btn');
    expect(btn).toBeDisabled();
  });
});

describe('DonationForm — date de naissance facultative', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    Object.defineProperty(window, 'top', { value: window, writable: true });
    // The submission is rejected on purpose: the payload is asserted before the redirect, which
    // jsdom cannot perform.
    mockCreateGuestDonation.mockRejectedValue(new Error('network'));
  });

  it('submits with no birth date at all', async () => {
    render(
      <DonationForm widgetToken="clk_test" sourceSite={null} locale="fr" skin="default" />,
    );

    fillValidForm(null);
    fireEvent.click(screen.getByRole('button', { name: 'submit' }));

    await waitFor(() => {
      expect(mockCreateGuestDonation).toHaveBeenCalled();
    });
    expect(mockCreateGuestDonation.mock.calls[0][1].donorBirthDate).toBeUndefined();
  });

  it('sends the birth date when the donor fills it in', async () => {
    render(
      <DonationForm widgetToken="clk_test" sourceSite={null} locale="fr" skin="default" />,
    );

    fillValidForm('1990-01-15');
    fireEvent.click(screen.getByRole('button', { name: 'submit' }));

    await waitFor(() => {
      expect(mockCreateGuestDonation).toHaveBeenCalled();
    });
    expect(mockCreateGuestDonation.mock.calls[0][1].donorBirthDate).toBe('1990-01-15');
  });

  it('never asks for a birth city', () => {
    render(
      <DonationForm widgetToken="clk_test" sourceSite={null} locale="fr" skin="default" />,
    );

    expect(screen.queryByLabelText(/identity.birthCity/i)).toBeNull();
  });
});
