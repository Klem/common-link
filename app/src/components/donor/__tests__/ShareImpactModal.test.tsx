import { describe, it, expect, vi } from 'vitest';
import { render, screen } from '@testing-library/react';
import { ShareImpactModal } from '../ShareImpactModal';
import type { ShareableImpact } from '@/lib/impactWording';

vi.mock('next-intl', () => ({
  useTranslations: () => (key: string, values?: Record<string, unknown>) =>
    values ? `${key}:${JSON.stringify(values)}` : key,
}));

const impact: ShareableImpact = {
  campaignId: 'camp-1',
  campaignName: 'Reforestation',
  campaignEmoji: '🌳',
  associationName: 'Terre Verte',
  impactGoals: '50 arbres plantés',
  storyText: '200 arbres déjà plantés cette saison.',
};

describe('ShareImpactModal', () => {
  it('renders nothing when impact is null', () => {
    const { container } = render(<ShareImpactModal impact={null} onClose={vi.fn()} />);
    expect(container).toBeEmptyDOMElement();
  });

  it('renders a card preview with no donor amount or share', () => {
    const { container } = render(<ShareImpactModal impact={impact} onClose={vi.fn()} />);

    expect(container.textContent).toContain('Reforestation');
    expect(container.textContent).toContain('Terre Verte');
    // No euro amount, no percentage, no donor-specific wording anywhere in the card or the dialog.
    expect(container.textContent).not.toMatch(/\d+[.,]?\d*\s?€/);
    expect(container.textContent).not.toMatch(/%/);
    expect(container.textContent?.toLowerCase()).not.toMatch(/financé|contribution de|votre don/);
  });

  it('builds well-formed share links for LinkedIn, X and WhatsApp', () => {
    render(<ShareImpactModal impact={impact} onClose={vi.fn()} />);

    const linkedin = screen.getByText('linkedin').closest('a');
    const x = screen.getByText('x').closest('a');
    const whatsapp = screen.getByText('whatsapp').closest('a');

    expect(linkedin).toHaveAttribute('href', expect.stringContaining('https://www.linkedin.com/sharing/share-offsite/'));
    expect(x).toHaveAttribute('href', expect.stringContaining('https://twitter.com/intent/tweet'));
    expect(whatsapp).toHaveAttribute('href', expect.stringContaining('https://wa.me/'));
    [linkedin, x, whatsapp].forEach((link) => {
      expect(link).toHaveAttribute('target', '_blank');
      expect(link).toHaveAttribute('rel', expect.stringContaining('noopener'));
    });
  });

  it('calls onClose when the close button is clicked', () => {
    const onClose = vi.fn();
    render(<ShareImpactModal impact={impact} onClose={onClose} />);

    screen.getByLabelText('close').click();
    expect(onClose).toHaveBeenCalled();
  });
});
