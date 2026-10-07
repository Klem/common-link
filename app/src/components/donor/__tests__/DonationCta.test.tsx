import { describe, it, expect, vi } from 'vitest';
import { render, screen } from '@testing-library/react';
import { DonationCta } from '../DonationCta';

vi.mock('next-intl', () => ({
  useTranslations: () => (key: string, values?: Record<string, unknown>) =>
    key === 'donate' ? `Soutenir ${values?.name}` : key,
}));

describe('DonationCta', () => {
  it('renders nothing when donationUrl is null', () => {
    const { container } = render(
      <DonationCta associationName="Terre Verte" donationUrl={null} />,
    );
    expect(container).toBeEmptyDOMElement();
  });

  it('renders a link to the association, never a specific project', () => {
    render(
      <DonationCta associationName="Terre Verte" donationUrl="https://commonlink.org/fr/lp/clk_x" />,
    );
    const link = screen.getByRole('link', { name: /Terre Verte/ });
    expect(link).toHaveAttribute('href', 'https://commonlink.org/fr/lp/clk_x');
  });
});
