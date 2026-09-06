import { describe, it, expect, vi } from 'vitest';
import { render, screen, fireEvent } from '@testing-library/react';
import { IbanRow } from '../IbanRow';
import { IbanVerificationStatus, type PayeeIbanDto } from '@/types/payee';

vi.mock('next-intl', () => ({
  useTranslations: () => (key: string) => key,
}));

function makeIban(overrides: Partial<PayeeIbanDto> = {}): PayeeIbanDto {
  return {
    id: 'iban-1',
    iban: 'DE89370400440532013000',
    status: IbanVerificationStatus.VERIFIED,
    vopResult: null,
    vopSuggestedName: null,
    verifiedAt: null,
    active: true,
    ...overrides,
  };
}

describe('IbanRow', () => {
  it('shows a disable button (no delete) for a VERIFIED IBAN when the payee has payouts', () => {
    const onDeleteIban = vi.fn();
    render(
      <IbanRow
        iban={makeIban()}
        payeeId="payee-1"
        isVerifyingVop={false}
        payeeHasPayouts={true}
        onDeleteIban={onDeleteIban}
        onVerifyVop={vi.fn()}
        onToggleActive={vi.fn()}
      />,
    );

    expect(screen.getByTitle('payees.iban.disable')).toBeInTheDocument();
    expect(screen.queryByTitle('payees.list.delete')).toBeNull();
  });

  it('shows both disable and delete buttons for a VERIFIED IBAN when the payee has no payouts', () => {
    render(
      <IbanRow
        iban={makeIban()}
        payeeId="payee-1"
        isVerifyingVop={false}
        payeeHasPayouts={false}
        onDeleteIban={vi.fn()}
        onVerifyVop={vi.fn()}
        onToggleActive={vi.fn()}
      />,
    );

    expect(screen.getByTitle('payees.iban.disable')).toBeInTheDocument();
    expect(screen.getByTitle('payees.list.delete')).toBeInTheDocument();
  });

  it('calls onToggleActive(id, false) when the disable button is clicked', () => {
    const onToggleActive = vi.fn();
    render(
      <IbanRow
        iban={makeIban()}
        payeeId="payee-1"
        isVerifyingVop={false}
        payeeHasPayouts={false}
        onDeleteIban={vi.fn()}
        onVerifyVop={vi.fn()}
        onToggleActive={onToggleActive}
      />,
    );

    fireEvent.click(screen.getByTitle('payees.iban.disable'));
    expect(onToggleActive).toHaveBeenCalledWith('iban-1', false);
  });

  it('shows only an enable button and greys out the row when the IBAN is disabled', () => {
    const onToggleActive = vi.fn();
    const { container } = render(
      <IbanRow
        iban={makeIban({ active: false })}
        payeeId="payee-1"
        isVerifyingVop={false}
        payeeHasPayouts={true}
        onDeleteIban={vi.fn()}
        onVerifyVop={vi.fn()}
        onToggleActive={onToggleActive}
      />,
    );

    expect(container.firstChild).toHaveClass('rm-iban-disabled');
    expect(screen.queryByTitle('payees.iban.disable')).toBeNull();
    expect(screen.queryByTitle('payees.list.delete')).toBeNull();

    fireEvent.click(screen.getByTitle('payees.iban.enable'));
    expect(onToggleActive).toHaveBeenCalledWith('iban-1', true);
  });

  it('does not show a disable button for a non-VERIFIED IBAN (delete only)', () => {
    render(
      <IbanRow
        iban={makeIban({ status: IbanVerificationStatus.FORMAT_VALID })}
        payeeId="payee-1"
        isVerifyingVop={false}
        payeeHasPayouts={false}
        onDeleteIban={vi.fn()}
        onVerifyVop={vi.fn()}
        onToggleActive={vi.fn()}
      />,
    );

    expect(screen.queryByTitle('payees.iban.disable')).toBeNull();
    expect(screen.getByTitle('payees.list.delete')).toBeInTheDocument();
  });
});
