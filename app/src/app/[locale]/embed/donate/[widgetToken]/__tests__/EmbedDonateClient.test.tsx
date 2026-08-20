import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import { EmbedDonateClient } from '../EmbedDonateClient';

vi.mock('next-intl', () => ({
  useTranslations: () => (key: string) => key,
}));

vi.mock('@/lib/api/public', () => ({
  getWidget: vi.fn(),
  createGuestDonation: vi.fn(),
}));

import { getWidget, createGuestDonation } from '@/lib/api/public';

const mockGetWidget = getWidget as ReturnType<typeof vi.fn>;
const mockCreateGuestDonation = createGuestDonation as ReturnType<typeof vi.fn>;

const sampleWidget = {
  associationName: 'Les Petits Écoliers',
  campaignId: 'camp-123',
  campaignName: 'Collecte de livres',
  campaignEmoji: '📚',
  campaignDescription: 'Une belle campagne.',
  goal: 5000,
  raised: 1200,
  campaignCoverImage: null,
  currency: 'EUR',
  widgetAllowedOrigin: null,
};

function fillValidForm() {
  fireEvent.click(screen.getByText('25 €'));
  fireEvent.change(screen.getByLabelText(/identity.email/i), {
    target: { value: 'jean@example.com' },
  });
  fireEvent.change(screen.getByLabelText(/identity.fullName/i), {
    target: { value: 'Jean Dupont' },
  });
  fireEvent.change(screen.getByLabelText(/identity.birthDate/i), {
    target: { value: '1990-01-15' },
  });
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

describe('EmbedDonateClient', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    localStorage.clear();
    mockGetWidget.mockResolvedValue(sampleWidget);
    // Mock window.top
    Object.defineProperty(window, 'top', {
      value: window,
      writable: true,
    });
  });

  it('shows loading state initially', () => {
    mockGetWidget.mockReturnValue(new Promise(() => {}));
    render(<EmbedDonateClient widgetToken="clk_test" sourceSite={null} locale="fr" />);
    expect(screen.getByText('loading')).toBeInTheDocument();
  });

  it('renders campaign info after widget loads', async () => {
    render(<EmbedDonateClient widgetToken="clk_test" sourceSite={null} locale="fr" />);
    await waitFor(() => {
      expect(screen.getByText('Les Petits Écoliers')).toBeInTheDocument();
      expect(screen.getByText(/Collecte de livres/)).toBeInTheDocument();
    });
    expect(screen.getByText('Une belle campagne.')).toBeInTheDocument();
  });

  it('shows error when widget token is 404', async () => {
    mockGetWidget.mockRejectedValue({ response: { status: 404 } });
    render(<EmbedDonateClient widgetToken="clk_bad" sourceSite={null} locale="fr" />);
    await waitFor(() => {
      expect(screen.getByText('unavailable')).toBeInTheDocument();
    });
  });

  it('refuses submit when amount is below minimum', async () => {
    render(<EmbedDonateClient widgetToken="clk_test" sourceSite={null} locale="fr" />);
    await waitFor(() => screen.getByText('Les Petits Écoliers'));

    // Set amount to 0 (below min)
    const amountInput = screen.getByPlaceholderText('amounts.customPlaceholder');
    fireEvent.change(amountInput, { target: { value: '0', valueAsNumber: 0 } });
    // Fill rest of form
    fireEvent.change(screen.getByLabelText(/identity.email/i), {
      target: { value: 'jean@example.com' },
    });
    fireEvent.change(screen.getByLabelText(/identity.fullName/i), {
      target: { value: 'Jean Dupont' },
    });
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
    fireEvent.click(screen.getByRole('button', { name: 'submit' }));

    await waitFor(() => {
      expect(screen.getByText('errors.amountMin')).toBeInTheDocument();
    });
    expect(mockCreateGuestDonation).not.toHaveBeenCalled();
  });

  it('refuses submit when consent is missing', async () => {
    render(<EmbedDonateClient widgetToken="clk_test" sourceSite={null} locale="fr" />);
    await waitFor(() => screen.getByText('Les Petits Écoliers'));

    fireEvent.click(screen.getByText('25 €'));
    fireEvent.change(screen.getByLabelText(/identity.email/i), {
      target: { value: 'jean@example.com' },
    });
    fireEvent.change(screen.getByLabelText(/identity.fullName/i), {
      target: { value: 'Jean Dupont' },
    });
    fireEvent.change(screen.getByLabelText(/identity.addressLine1/i), {
      target: { value: '12 rue de la Paix' },
    });
    fireEvent.change(screen.getByLabelText(/identity.postalCode/i), {
      target: { value: '75001' },
    });
    fireEvent.change(screen.getByLabelText(/identity.city/i), {
      target: { value: 'Paris' },
    });
    // DO NOT check consent
    fireEvent.click(screen.getByRole('button', { name: 'submit' }));

    await waitFor(() => {
      expect(screen.getByText('errors.consentRequired')).toBeInTheDocument();
    });
    expect(mockCreateGuestDonation).not.toHaveBeenCalled();
  });

  it('refuses submit when fullName is empty', async () => {
    render(<EmbedDonateClient widgetToken="clk_test" sourceSite={null} locale="fr" />);
    await waitFor(() => screen.getByText('Les Petits Écoliers'));

    fireEvent.click(screen.getByText('25 €'));
    fireEvent.change(screen.getByLabelText(/identity.email/i), {
      target: { value: 'jean@example.com' },
    });
    // NO fullName
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
    fireEvent.click(screen.getByRole('button', { name: 'submit' }));

    await waitFor(() => {
      expect(screen.getAllByText('errors.fieldRequired').length).toBeGreaterThan(0);
    });
    expect(mockCreateGuestDonation).not.toHaveBeenCalled();
  });

  it('refuses submit when addressLine1 is empty', async () => {
    render(<EmbedDonateClient widgetToken="clk_test" sourceSite={null} locale="fr" />);
    await waitFor(() => screen.getByText('Les Petits Écoliers'));

    fireEvent.click(screen.getByText('25 €'));
    fireEvent.change(screen.getByLabelText(/identity.email/i), {
      target: { value: 'jean@example.com' },
    });
    fireEvent.change(screen.getByLabelText(/identity.fullName/i), {
      target: { value: 'Jean Dupont' },
    });
    // NO addressLine1
    fireEvent.change(screen.getByLabelText(/identity.postalCode/i), {
      target: { value: '75001' },
    });
    fireEvent.change(screen.getByLabelText(/identity.city/i), {
      target: { value: 'Paris' },
    });
    fireEvent.click(screen.getByLabelText(/consent.label/i));
    fireEvent.click(screen.getByRole('button', { name: 'submit' }));

    await waitFor(() => {
      expect(screen.getAllByText('errors.fieldRequired').length).toBeGreaterThan(0);
    });
    expect(mockCreateGuestDonation).not.toHaveBeenCalled();
  });

  it('calls createGuestDonation with full payload and redirects on valid submit', async () => {
    mockCreateGuestDonation.mockResolvedValue({
      checkoutUrl: 'https://checkout.mollie.com/pay/tr_test123',
      paymentId: 'tr_test123',
    });
    const locationSpy = vi.fn();
    Object.defineProperty(window, 'location', {
      value: { href: '' },
      writable: true,
    });
    Object.defineProperty(window, 'top', {
      value: { location: { set href(v: string) { locationSpy(v); } } },
      writable: true,
    });

    render(<EmbedDonateClient widgetToken="clk_test" sourceSite="https://example.com" locale="fr" />);
    await waitFor(() => screen.getByText('Les Petits Écoliers'));

    fillValidForm();
    fireEvent.click(screen.getByRole('button', { name: 'submit' }));

    await waitFor(() => {
      expect(mockCreateGuestDonation).toHaveBeenCalledWith('clk_test', expect.objectContaining({
        amount: 25,
        donorEmail: 'jean@example.com',
        donorFullName: 'Jean Dupont',
        donorAddressLine1: '12 rue de la Paix',
        donorPostalCode: '75001',
        donorCity: 'Paris',
        donorCountry: 'FR',
        consent: true,
        sourceSite: 'https://example.com',
      }));
    });
    expect(locationSpy).toHaveBeenCalledWith('https://checkout.mollie.com/pay/tr_test123');
  });
});
