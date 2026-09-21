import { describe, it, expect, vi } from 'vitest';
import { render, screen, fireEvent } from '@testing-library/react';
import MollieInfoModal from '../MollieInfoModal';

vi.mock('next-intl', () => ({
  useTranslations: () => (key: string) => key,
}));

describe('MollieInfoModal', () => {
  it('states who Mollie is, why the check is mandatory, and where documents and funds go', () => {
    render(<MollieInfoModal isOpen onClose={vi.fn()} />);

    expect(screen.getByText('title')).toBeInTheDocument();
    expect(screen.getByText('sections.who.body')).toBeInTheDocument();
    expect(screen.getByText('sections.why.body')).toBeInTheDocument();
    expect(screen.getByText('sections.documents.body')).toBeInTheDocument();
    expect(screen.getByText('sections.funds.body')).toBeInTheDocument();
  });

  it('links the public register and the security page, so the claims can be checked', () => {
    render(<MollieInfoModal isOpen onClose={vi.fn()} />);

    const register = screen.getByRole('link', { name: 'sections.verify.registerLink' });
    expect(register).toHaveAttribute(
      'href',
      'https://www.dnb.nl/en/public-register/information-detail/?registerCode=WFTEG&relationNumber=F0038',
    );
    expect(register).toHaveAttribute('target', '_blank');
    expect(register).toHaveAttribute('rel', 'noopener noreferrer');

    expect(screen.getByRole('link', { name: 'sections.verify.securityLink' })).toHaveAttribute(
      'href',
      'https://www.mollie.com/security',
    );
  });

  it('closes on the backdrop, the cross and the footer button', () => {
    const onClose = vi.fn();
    const { container } = render(<MollieInfoModal isOpen onClose={onClose} />);

    fireEvent.click(screen.getByLabelText('close'));
    fireEvent.click(screen.getByText('close'));
    fireEvent.click(container.querySelector('.modal-backdrop')!);

    expect(onClose).toHaveBeenCalledTimes(3);
  });

  it('never triggers a connection — it only informs', () => {
    render(<MollieInfoModal isOpen onClose={vi.fn()} />);

    expect(screen.queryByText('mollie.modal.connect')).not.toBeInTheDocument();
  });

  it('renders nothing when closed', () => {
    const { container } = render(<MollieInfoModal isOpen={false} onClose={vi.fn()} />);

    expect(container).toBeEmptyDOMElement();
  });
});
