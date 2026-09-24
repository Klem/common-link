import { describe, it, expect } from 'vitest';
import {
  BridgePaymentStatus,
  PayoutKind,
  PayoutStatus,
  isPayoutInFlight,
  lastAttemptFailed,
  needsBankAuthorisation,
  type PayoutDto,
} from '../payment';

/** A payout awaiting its bank, with only the fields these predicates read varied per test. */
function payout(overrides: Partial<PayoutDto> = {}): PayoutDto {
  return {
    id: 'p1',
    campaignId: 'c1',
    payeeId: 'b1',
    payeeName: 'Scale That',
    payeeIbanId: 'i1',
    ibanValue: 'FR7630006000011234567890189',
    amount: 3.1,
    kind: PayoutKind.EXPENSE,
    typeCode: '60-mat',
    label: 'Achat matériel pédagogique',
    status: PayoutStatus.PENDING,
    createdAt: '2026-09-24T07:58:15Z',
    confirmedAt: null,
    onchainJobId: null,
    bridgeStatus: BridgePaymentStatus.CREA,
    bridgeLastError: null,
    bridgeCheckoutUrl: 'https://pay.bridgeapi.io/link/abc',
    ...overrides,
  };
}

describe('needsBankAuthorisation', () => {
  it('offers the link while the association has not opened it', () => {
    expect(needsBankAuthorisation(payout())).toBe(true);
  });

  it('still offers the link once the payer has entered the tunnel', () => {
    // Bridge stamps ACTC the moment the tunnel opens and keeps it for the whole bank
    // authentication. Gating on CREA alone made the button vanish at the instant it was clicked:
    // an association that closed the tab had no action left until the link expired — a full day
    // in production, for a transfer where nothing had been debited.
    expect(needsBankAuthorisation(payout({ bridgeStatus: BridgePaymentStatus.ACTC }))).toBe(true);
  });

  it('never offers a link that has died', () => {
    // releaseReservation and finaliseFailed both clear the URL; the row keeps its link id as an
    // audit trail, which is why the URL and not the id is what gates this.
    expect(
      needsBankAuthorisation(payout({ bridgeStatus: BridgePaymentStatus.ACTC, bridgeCheckoutUrl: null })),
    ).toBe(false);
  });

  it('does not offer a link once the bank is executing', () => {
    // From PDNG on the money is moving: re-opening would invite a second transfer.
    expect(needsBankAuthorisation(payout({ bridgeStatus: BridgePaymentStatus.PDNG }))).toBe(false);
  });

  it('does not offer a link on a payout with no initiation at all', () => {
    expect(needsBankAuthorisation(payout({ bridgeStatus: null, bridgeCheckoutUrl: null }))).toBe(false);
  });
});

describe('lastAttemptFailed', () => {
  it('marks a released payout as retryable', () => {
    expect(
      lastAttemptFailed(
        payout({
          bridgeStatus: null,
          bridgeCheckoutUrl: null,
          bridgeLastError: 'Bank authorisation window expired before the transfer was authorised',
        }),
      ),
    ).toBe(true);
  });

  it('does not mark a payout that was never attempted', () => {
    expect(lastAttemptFailed(payout({ bridgeStatus: null, bridgeCheckoutUrl: null }))).toBe(false);
  });

  it('does not mark a payout whose transfer is engaged', () => {
    expect(lastAttemptFailed(payout({ bridgeLastError: 'previous failure' }))).toBe(false);
  });
});

describe('isPayoutInFlight', () => {
  it.each([
    BridgePaymentStatus.CREA,
    BridgePaymentStatus.ACTC,
    BridgePaymentStatus.PDNG,
    BridgePaymentStatus.PART,
  ])('counts %s as engaged', (status) => {
    expect(isPayoutInFlight(payout({ bridgeStatus: status }))).toBe(true);
  });

  it.each([BridgePaymentStatus.ACSC, BridgePaymentStatus.RJCT])(
    'does not count the terminal %s',
    (status) => {
      expect(isPayoutInFlight(payout({ bridgeStatus: status }))).toBe(false);
    },
  );
});
