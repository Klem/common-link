import { describe, it, expect, vi } from 'vitest';
import { render, screen, fireEvent } from '@testing-library/react';
import { PayeeRow } from '../PayeeRow';
import { IbanVerificationStatus, type PayeeDto } from '@/types/payee';

vi.mock('next-intl', () => ({
  useTranslations: () => (key: string) => key,
}));
vi.mock('@/lib/api/payee', () => ({
  getPayeePayouts: vi.fn().mockResolvedValue([]),
}));

function makePayee(overrides: Partial<PayeeDto> = {}): PayeeDto {
  return {
    id: 'payee-1',
    payeeType: 'COMPANY',
    name: 'ACME Corp',
    identifier1: '123456789',
    identifier2: null,
    activityCode: null,
    category: null,
    city: null,
    postalCode: null,
    active: true,
    hasPayouts: false,
    ibans: [],
    createdAt: new Date().toISOString(),
    ...overrides,
  };
}

function renderRow(overrides: Partial<PayeeDto> = {}) {
  const onAddIban = vi.fn();
  render(
    <PayeeRow
      payee={makePayee(overrides)}
      onDeletePayee={vi.fn()}
      onToggleActive={vi.fn()}
      onAddIban={onAddIban}
      onDeleteIban={vi.fn()}
      onVerifyVop={vi.fn()}
      onToggleIbanActive={vi.fn()}
      verifyingIbanId={null}
    />,
  );
  return { onAddIban };
}

describe('PayeeRow — client-side IBAN format validation', () => {
  it('shows an inline error and does not call onAddIban for a malformed IBAN', () => {
    const { onAddIban } = renderRow();

    fireEvent.click(screen.getByText('＋ payees.iban.addIban'));
    fireEvent.change(screen.getByPlaceholderText('payees.iban.inputPlaceholder'), {
      target: { value: 'NOTANIBAN123' },
    });
    fireEvent.click(screen.getByText('payees.iban.add'));

    expect(screen.getByText('payees.iban.invalidFormat')).toBeInTheDocument();
    expect(onAddIban).not.toHaveBeenCalled();
  });

  it('calls onAddIban and clears the form for a valid IBAN', () => {
    const { onAddIban } = renderRow();

    fireEvent.click(screen.getByText('＋ payees.iban.addIban'));
    fireEvent.change(screen.getByPlaceholderText('payees.iban.inputPlaceholder'), {
      target: { value: 'DE89370400440532013000' },
    });
    fireEvent.click(screen.getByText('payees.iban.add'));

    expect(onAddIban).toHaveBeenCalledWith('payee-1', 'DE89370400440532013000');
    expect(screen.queryByText('payees.iban.invalidFormat')).toBeNull();
  });

  it('clears the format error as soon as the user edits the input again', () => {
    renderRow();

    fireEvent.click(screen.getByText('＋ payees.iban.addIban'));
    const input = screen.getByPlaceholderText('payees.iban.inputPlaceholder');
    fireEvent.change(input, { target: { value: 'NOTANIBAN123' } });
    fireEvent.click(screen.getByText('payees.iban.add'));
    expect(screen.getByText('payees.iban.invalidFormat')).toBeInTheDocument();

    fireEvent.change(input, { target: { value: 'NOTANIBAN1234' } });
    expect(screen.queryByText('payees.iban.invalidFormat')).toBeNull();
  });
});

describe('PayeeRow — IBAN row wiring', () => {
  it('passes payee.hasPayouts down to each IbanRow', () => {
    const iban = {
      id: 'iban-1',
      iban: 'DE89370400440532013000',
      status: IbanVerificationStatus.VERIFIED,
      vopResult: null,
      vopSuggestedName: null,
      verifiedAt: null,
      active: true,
    };
    renderRow({ hasPayouts: true, ibans: [iban] });

    // hasPayouts=true on a VERIFIED iban → disable only, no delete button
    expect(screen.getByTitle('payees.iban.disable')).toBeInTheDocument();
    expect(screen.queryByTitle('payees.list.delete')).toBeNull();
  });
});
