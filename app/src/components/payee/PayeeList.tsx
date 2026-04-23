import { useTranslations } from 'next-intl';
import type { PayeeDto } from '@/types/payee';
import { PayeeRow } from './PayeeRow';

interface PayeeListProps {
  /** Payees to display. */
  payees: PayeeDto[];
  /** True while the list is being loaded. */
  isLoading: boolean;
  /** Called when the user deletes a payee. */
  onDeletePayee: (id: string) => void;
  /** Called when the user adds an IBAN to a payee. */
  onAddIban: (payeeId: string, iban: string) => void;
  /** Called when the user deletes a payee IBAN. */
  onDeleteIban: (payeeId: string, ibanId: string) => void;
  /** Called when the user triggers VOP verification on an IBAN. */
  onVerifyVop: (payeeId: string, ibanId: string) => void;
  /** UUID of the IBAN currently being VOP-verified, or null. */
  verifyingIbanId: string | null;
}

/**
 * Card listing all payees for an association.
 *
 * Shows a loading skeleton, an empty state, or the list of PayeeRow entries
 * separated by dividers.
 */
export function PayeeList({
  payees,
  isLoading,
  onDeletePayee,
  onAddIban,
  onDeleteIban,
  onVerifyVop,
  verifyingIbanId,
}: PayeeListProps) {
  const t = useTranslations('dashboard');

  return (
    <div className="bg-bg-2 border border-border rounded-xl overflow-hidden">
      {/* Header */}
      <div className="flex items-center justify-between p-[20px] border-b border-border">
        <h2 className="font-display font-bold text-[16px] text-text">
          {t('payees.list.title')}
        </h2>
        <span className="bg-green/12 text-green rounded-full px-[10px] py-[3px] text-[12px] font-bold">
          {t('payees.list.count', { count: payees.length })}
        </span>
      </div>

      {/* Content */}
      {isLoading ? (
        <div className="flex items-center justify-center py-[40px]">
          <span className="inline-block w-6 h-6 border-2 border-border border-t-green rounded-full animate-spin" />
        </div>
      ) : payees.length === 0 ? (
        <div className="flex flex-col items-center justify-center py-[40px] text-center px-[24px]">
          <p className="text-muted text-[14px]">{t('payees.list.empty')}</p>
          <p className="text-muted text-[12px] mt-[4px]">{t('payees.list.emptyHint')}</p>
        </div>
      ) : (
        <div>
          {payees.map((payee, index) => (
            <div key={payee.id}>
              {index > 0 && <div className="border-t border-border" />}
              <PayeeRow
                payee={payee}
                onDeletePayee={onDeletePayee}
                onAddIban={onAddIban}
                onDeleteIban={onDeleteIban}
                onVerifyVop={onVerifyVop}
                verifyingIbanId={verifyingIbanId}
              />
            </div>
          ))}
        </div>
      )}
    </div>
  );
}
