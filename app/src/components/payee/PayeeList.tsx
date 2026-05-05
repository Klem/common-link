import { useTranslations } from 'next-intl';
import type { PayeeDto } from '@/types/payee';
import { PayeeRow } from './PayeeRow';
import { EmptyStateCard } from '@/components/dashboard';

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
    <div className="card card-no-hover">
      <div className="card-header-bar">
        <h2 className="font-display font-bold text-base text-text">
          {t('payees.list.title')}
        </h2>
        <span className="badge badge-active">
          {t('payees.list.count', { count: payees.length })}
        </span>
      </div>

      {isLoading ? (
        <div className="flex items-center justify-center py-10">
          <span className="inline-block w-6 h-6 border-2 border-border border-t-green rounded-full animate-spin" />
        </div>
      ) : payees.length === 0 ? (
        <EmptyStateCard
          icon="🏢"
          title={t('payees.list.empty')}
          subtitle={t('payees.list.emptyHint')}
        />
      ) : (
        <div className="divide-y divide-border">
          {payees.map((payee) => (
            <PayeeRow
              key={payee.id}
              payee={payee}
              onDeletePayee={onDeletePayee}
              onAddIban={onAddIban}
              onDeleteIban={onDeleteIban}
              onVerifyVop={onVerifyVop}
              verifyingIbanId={verifyingIbanId}
            />
          ))}
        </div>
      )}
    </div>
  );
}
