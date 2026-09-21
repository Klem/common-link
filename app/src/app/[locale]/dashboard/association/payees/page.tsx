'use client';

import { useState, useCallback } from 'react';
import { useTranslations } from 'next-intl';
import { SirenSearchCard, SireneResultPanel, PayeeList, type PayeeFilter } from '@/components/payee';
import { createPayee } from '@/lib/api/payee';
import { usePayees } from '@/hooks/payee/usePayees';
import { useVopVerify } from '@/hooks/payee/useVopVerify';
import { useToastStore } from '@/stores/toastStore';
import type { SireneSearchResultDto, CreatePayeeRequest } from '@/types/payee';

function toCreateRequest(result: SireneSearchResultDto): CreatePayeeRequest {
  return {
    name: result.name,
    payeeType: 'COMPANY',
    identifier1: result.siren,
    identifier2: result.siret ?? undefined,
    activityCode: result.nafCode ?? undefined,
    category: result.category ?? undefined,
    city: result.city ?? undefined,
    postalCode: result.postalCode ?? undefined,
    active: result.active,
  };
}

export default function PayeesPage() {
  const t = useTranslations('dashboard');
  const { addToast } = useToastStore();

  const { payees, isLoading, fetchPayees, addPayeeIban, removePayeeIban, removePayee, setPayeeActive, togglePayeeIbanActive } = usePayees();
  const { verifyingIbanId, verify } = useVopVerify();

  const [mode, setMode] = useState<'company' | 'person'>('company');
  const [payeeFilter, setPayeeFilter] = useState<PayeeFilter>('all');
  const [helpOpen, setHelpOpen] = useState(false);
  const [sireneResult, setSireneResult] = useState<SireneSearchResultDto | null>(null);
  const [showPanel, setShowPanel] = useState(false);
  const [isCreating, setIsCreating] = useState(false);
  const [firstName, setFirstName] = useState('');
  const [lastName, setLastName] = useState('');
  const [personError, setPersonError] = useState('');

  const handleResult = useCallback((result: SireneSearchResultDto) => {
    setSireneResult(result);
    setShowPanel(true);
  }, []);

  const handleClose = () => setShowPanel(false);

  /**
   * Switches the "add beneficiary" form mode. The list filter follows the mode
   * so the list below always shows the type the user is currently adding.
   */
  const handleModeChange = (next: 'company' | 'person') => {
    setMode(next);
    setPayeeFilter(next);
    setPersonError('');
    if (next === 'person') setShowPanel(false);
  };

  /**
   * Switches the list filter. A typed filter mirrors back onto the form mode;
   * "all" is exempt and leaves the form mode untouched.
   */
  const handleFilterChange = (f: PayeeFilter) => {
    if (f === 'all') {
      setPayeeFilter('all');
      return;
    }
    handleModeChange(f);
  };

  const handleSelect = async () => {
    if (!sireneResult) return;
    setIsCreating(true);
    try {
      await createPayee(toCreateRequest(sireneResult));
      addToast('success', 'payeeCreated');
      setShowPanel(false);
      setSireneResult(null);
      await fetchPayees();
    } catch {
      addToast('error', 'errors.serverError');
    } finally {
      setIsCreating(false);
    }
  };

  const handleAddPerson = async () => {
    if (!lastName.trim()) {
      setPersonError(t('payees.person.error'));
      return;
    }
    const name = `${firstName.trim()} ${lastName.trim()}`.trim();
    setPersonError('');
    setIsCreating(true);
    try {
      await createPayee({ name, payeeType: 'PERSON' });
      addToast('success', 'payeeCreated');
      setFirstName('');
      setLastName('');
      await fetchPayees();
    } catch {
      addToast('error', 'errors.serverError');
    } finally {
      setIsCreating(false);
    }
  };

  const handleAddIban = async (payeeId: string, iban: string) => {
    try { await addPayeeIban(payeeId, iban); } catch { addToast('error', 'errors.serverError'); }
  };

  const handleDeleteIban = async (payeeId: string, ibanId: string) => {
    try { await removePayeeIban(payeeId, ibanId); } catch { addToast('error', 'errors.serverError'); }
  };

  const handleDeletePayee = async (id: string) => {
    try { await removePayee(id); } catch { addToast('error', 'errors.serverError'); }
  };

  const handleToggleActive = async (id: string, active: boolean) => {
    try { await setPayeeActive(id, active); } catch { addToast('error', 'errors.serverError'); }
  };

  const handleVerifyVop = async (payeeId: string, ibanId: string) => {
    await verify(payeeId, ibanId, fetchPayees);
  };

  const handleToggleIbanActive = async (payeeId: string, ibanId: string, active: boolean) => {
    try { await togglePayeeIbanActive(payeeId, ibanId, active); } catch { addToast('error', 'errors.serverError'); }
  };

  return (
    <div className="page">
      <div className="page-head">
        <div>
          <h1>{t('payees.pageTitle')}</h1>
          <p>{t('payees.pageSubtitle')}</p>
        </div>
        <div className="payee-help-wrap">
          <button className="payee-help-btn" onClick={() => setHelpOpen(!helpOpen)}>?</button>
          <div className={`payee-help-panel${helpOpen ? ' open' : ''}`}>
            <div className="payee-help-header">
              <span className="payee-help-title">{t('payees.help.title')}</span>
              <button
                onClick={() => setHelpOpen(false)}
                className="payee-help-close"
              >✕</button>
            </div>
            <p className="payee-help-text">{t('payees.help.text1')}</p>
            <p className="payee-help-text">{t('payees.help.text2')}</p>
          </div>
        </div>
      </div>

      {/* Add card */}
      <div className="card card-no-hover payees-add-card">
        <div className="card-h">
          <h3>{mode === 'company' ? t('payees.search.title') : t('payees.person.cardTitle')}</h3>
        </div>
        <div className="card-b">
          <div className="payee-mode-tabs">
            <button
              className={`btn btn-sm ${mode === 'company' ? 'btn-primary' : 'btn-secondary'}`}
              onClick={() => handleModeChange('company')}
            >🏢 {t('payees.mode.company')}</button>
            <button
              className={`btn btn-sm ${mode === 'person' ? 'btn-primary' : 'btn-secondary'}`}
              onClick={() => handleModeChange('person')}
            >👤 {t('payees.mode.person')}</button>
          </div>

          {mode === 'company' && <SirenSearchCard onResult={handleResult} />}

          {mode === 'person' && (
            <div>
              <div className="payee-person-row">
                <div className="fg">
                  <label className="fl">{t('payees.person.firstName')}</label>
                  <input className="fi" type="text" placeholder="Marie" value={firstName}
                    onChange={(e) => setFirstName(e.target.value)} autoComplete="off" />
                </div>
                <div className="fg">
                  <label className="fl">{t('payees.person.lastName')}</label>
                  <input className="fi" type="text" placeholder="Dupont" value={lastName}
                    onChange={(e) => { setLastName(e.target.value); setPersonError(''); }}
                    autoComplete="off" />
                </div>
                <button
                  className="btn btn-primary"
                  disabled={!lastName.trim() || isCreating}
                  onClick={handleAddPerson}
                >✚ {t('payees.person.add')}</button>
              </div>
              {personError && (
                <div className="payee-person-error">{personError}</div>
              )}
            </div>
          )}
        </div>
      </div>

      {/* Sirene result panel */}
      {showPanel && sireneResult && (
        <div className="sirene-panel-wrap">
          <SireneResultPanel
            result={sireneResult}
            onSelect={handleSelect}
            onClose={handleClose}
            isLoading={isCreating}
          />
        </div>
      )}

      <PayeeList
        payees={payees}
        isLoading={isLoading}
        filter={payeeFilter}
        onFilterChange={handleFilterChange}
        onDeletePayee={handleDeletePayee}
        onToggleActive={handleToggleActive}
        onAddIban={handleAddIban}
        onDeleteIban={handleDeleteIban}
        onVerifyVop={handleVerifyVop}
        onToggleIbanActive={handleToggleIbanActive}
        verifyingIbanId={verifyingIbanId}
      />
    </div>
  );
}
