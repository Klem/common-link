'use client';

import { useState, useCallback } from 'react';
import { useTranslations } from 'next-intl';
import { SirenSearchCard, SireneResultPanel, PayeeList } from '@/components/payee';
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

  const { payees, isLoading, fetchPayees, addPayeeIban, removePayeeIban, removePayee } = usePayees();
  const { verifyingIbanId, verify } = useVopVerify();

  const [mode, setMode] = useState<'company' | 'person'>('company');
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

  const handleVerifyVop = async (payeeId: string, ibanId: string) => {
    await verify(payeeId, ibanId, fetchPayees);
  };

  return (
    <div className="page">
      <div className="page-head">
        <div>
          <h1>{t('payees.pageTitle')}</h1>
          <p>{t('payees.pageSubtitle')}</p>
        </div>
        <div style={{ position: 'relative', display: 'flex', alignItems: 'center' }}>
          <button className="rm-help-btn" onClick={() => setHelpOpen(!helpOpen)}>?</button>
          <div className={`rm-help-panel${helpOpen ? ' open' : ''}`}>
            <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', marginBottom: 12 }}>
              <span className="rm-help-title">{t('payees.help.title')}</span>
              <button
                onClick={() => setHelpOpen(false)}
                style={{ background: 'none', border: 'none', color: 'var(--slate-lavender)', cursor: 'pointer', fontSize: 18, lineHeight: 1 }}
              >✕</button>
            </div>
            <p style={{ color: 'var(--slate-lavender)' }}>{t('payees.help.text1')}</p>
            <p style={{ color: 'var(--slate-lavender)', marginTop: 10 }}>{t('payees.help.text2')}</p>
          </div>
        </div>
      </div>

      {/* Add card */}
      <div className="card no-hover" style={{ marginBottom: 24 }}>
        <div className="card-h">
          <h3>{mode === 'company' ? t('payees.search.title') : t('payees.person.cardTitle')}</h3>
        </div>
        <div className="card-b">
          <div style={{ display: 'flex', gap: 8, marginBottom: 18 }}>
            <button
              className={`btn btn-sm ${mode === 'company' ? 'btn-primary' : 'btn-secondary'}`}
              onClick={() => { setMode('company'); setPersonError(''); }}
            >🏢 {t('payees.mode.company')}</button>
            <button
              className={`btn btn-sm ${mode === 'person' ? 'btn-primary' : 'btn-secondary'}`}
              onClick={() => { setMode('person'); setShowPanel(false); }}
            >👤 {t('payees.mode.person')}</button>
          </div>

          {mode === 'company' && <SirenSearchCard onResult={handleResult} />}

          {mode === 'person' && (
            <div>
              <div style={{ display: 'flex', gap: 10, alignItems: 'flex-end' }}>
                <div style={{ flex: 1 }}>
                  <label className="cm-label">{t('payees.person.firstName')}</label>
                  <input className="cm-fi" type="text" placeholder="Marie" value={firstName}
                    onChange={(e) => setFirstName(e.target.value)} autoComplete="off" />
                </div>
                <div style={{ flex: 1 }}>
                  <label className="cm-label">{t('payees.person.lastName')}</label>
                  <input className="cm-fi" type="text" placeholder="Dupont" value={lastName}
                    onChange={(e) => { setLastName(e.target.value); setPersonError(''); }}
                    autoComplete="off" />
                </div>
                <button
                  className="cm-btn cm-btn-primary"
                  disabled={!lastName.trim() || isCreating}
                  onClick={handleAddPerson}
                  style={{ height: 44, flexShrink: 0 }}
                >✚ {t('payees.person.add')}</button>
              </div>
              {personError && (
                <div style={{ marginTop: 8, color: 'var(--warm-coral)', fontSize: 13 }}>{personError}</div>
              )}
            </div>
          )}
        </div>
      </div>

      {/* Sirene result panel */}
      {showPanel && sireneResult && (
        <div style={{ marginBottom: 24 }}>
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
        onDeletePayee={handleDeletePayee}
        onAddIban={handleAddIban}
        onDeleteIban={handleDeleteIban}
        onVerifyVop={handleVerifyVop}
        verifyingIbanId={verifyingIbanId}
      />
    </div>
  );
}
