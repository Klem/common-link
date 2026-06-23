import { useState } from 'react';
import { useTranslations } from 'next-intl';
import type { PayeeDto } from '@/types/payee';
import { PayeeRow } from './PayeeRow';

type Filter = 'all' | 'company' | 'person';

interface PayeeListProps {
  payees: PayeeDto[];
  isLoading: boolean;
  onDeletePayee: (id: string) => void;
  onAddIban: (payeeId: string, iban: string) => void;
  onDeleteIban: (payeeId: string, ibanId: string) => void;
  onVerifyVop: (payeeId: string, ibanId: string) => void;
  verifyingIbanId: string | null;
}

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
  const [filter, setFilter] = useState<Filter>('all');

  const filtered = filter === 'all'
    ? payees
    : payees.filter((p) => p.payeeType === (filter === 'company' ? 'COMPANY' : 'PERSON'));

  return (
    <div className="card no-hover">
      <div className="card-h">
        <h3>
          {t('payees.list.title')}{' '}
          <span className="badge-count" style={{ background: 'var(--deep-indigo)' }}>
            {payees.length}
          </span>
        </h3>
        <div className="col-filter">
          <button
            className={`col-filter-btn${filter === 'all' ? ' active' : ''}`}
            onClick={() => setFilter('all')}
          >{t('payees.filter.all')}</button>
          <button
            className={`col-filter-btn${filter === 'company' ? ' active' : ''}`}
            onClick={() => setFilter('company')}
          >🏢 {t('payees.filter.companies')}</button>
          <button
            className={`col-filter-btn${filter === 'person' ? ' active' : ''}`}
            onClick={() => setFilter('person')}
          >👤 {t('payees.filter.persons')}</button>
        </div>
      </div>

      {isLoading ? (
        <div className="rm-empty-recip"><span className="rm-spinner" /></div>
      ) : filtered.length === 0 ? (
        <div className="rm-empty-recip">
          {filter === 'all' ? t('payees.list.empty') : t('payees.list.emptyFiltered')}
        </div>
      ) : (
        <div style={{ padding: '8px 0' }}>
          {filtered.map((payee) => (
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
