'use client';

import { useState, useEffect } from 'react';
import { useTranslations } from 'next-intl';
import { listBeneficialOwners, addBeneficialOwner, discardBeneficialOwner } from '@/lib/api/admin';
import type { BeneficialOwnerDto } from '@/types/admin';
import { BeneficialOwnerOrigin } from '@/types/admin';

interface Props {
  associationId: string;
  /** Officers collected from the latest registry scan — offered as suggestions. */
  officers: string[];
  /** Called whenever the retained owner count changes — used by the parent to gate approval. */
  onRetainedCountChange?: (count: number) => void;
}

export function BeneficialOwnersPanel({ associationId, officers, onRetainedCountChange }: Props) {
  const t = useTranslations('curator.dossier');

  const [owners, setOwners] = useState<BeneficialOwnerDto[]>([]);
  const [loading, setLoading] = useState(true);
  const [failed, setFailed] = useState(false);

  // Discard confirmation state: ownerId being confirmed, or null
  const [confirmDiscard, setConfirmDiscard] = useState<string | null>(null);
  const [discarding, setDiscarding] = useState(false);

  // Add form state
  const [showForm, setShowForm] = useState(false);
  const [formName, setFormName] = useState('');
  const [formDob, setFormDob] = useState('');
  const [formRole, setFormRole] = useState('');
  const [formNameError, setFormNameError] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);

  const load = () => {
    setLoading(true);
    setFailed(false);
    listBeneficialOwners(associationId)
      .then(setOwners)
      .catch(() => setFailed(true))
      .finally(() => setLoading(false));
  };

  useEffect(() => {
    load();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [associationId]);

  // Track whether the form was triggered from a registry officer suggestion
  const [formOriginFromRegistry, setFormOriginFromRegistry] = useState(false);

  const retained = owners.filter((o) => !o.discarded);
  const discarded = owners.filter((o) => o.discarded);

  useEffect(() => {
    onRetainedCountChange?.(retained.length);
  // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [retained.length]);

  const handleDiscard = async (ownerId: string) => {
    setDiscarding(true);
    try {
      await discardBeneficialOwner(associationId, ownerId);
      setOwners((prev) => prev.map((o) => o.id === ownerId ? { ...o, discarded: true } : o));
    } finally {
      setDiscarding(false);
      setConfirmDiscard(null);
    }
  };

  const handleAddSubmit = async (name: string, origin: BeneficialOwnerOrigin) => {
    const trimmed = name.trim();
    if (!trimmed) { setFormNameError(t('ubo.add.nameRequired')); return; }
    if (trimmed.length > 200) { setFormNameError(t('ubo.add.nameTooLong')); return; }
    setFormNameError(null);
    setSubmitting(true);
    try {
      const created = await addBeneficialOwner(associationId, {
        name: trimmed,
        role: formRole.trim() || null,
        dateOfBirth: formDob.trim() || null,
        origin,
      });
      setOwners((prev) => [...prev, created]);
      setShowForm(false);
      setFormName('');
      setFormDob('');
      setFormRole('');
      setFormOriginFromRegistry(false);
    } finally {
      setSubmitting(false);
    }
  };

  const containerStyle: React.CSSProperties = {
    border: '1px solid var(--color-border)',
    borderRadius: 8,
    padding: '12px 16px',
    marginBottom: 24,
    fontSize: 13,
  };

  if (loading) {
    return (
      <div style={containerStyle}>
        <p style={{ color: 'var(--color-text-2)', margin: 0 }}>{t('ubo.loading')}</p>
      </div>
    );
  }

  if (failed) {
    return (
      <div style={containerStyle}>
        <p style={{ color: 'var(--color-error)', margin: 0 }}>{t('ubo.error')}</p>
      </div>
    );
  }

  return (
    <div style={containerStyle}>
      <span style={{ fontWeight: 700, fontSize: 13, display: 'block', marginBottom: 10 }}>
        {t('ubo.title')}
      </span>

      {/* Blocking warning — no retained owner */}
      {retained.length === 0 && (
        <div
          style={{
            background: 'rgba(231,76,60,0.08)',
            border: '1px solid rgba(231,76,60,0.25)',
            borderRadius: 6,
            padding: '8px 12px',
            marginBottom: 12,
            color: 'var(--color-error)',
            fontWeight: 600,
            fontSize: 13,
          }}
        >
          {t('ubo.noRetained')}
        </div>
      )}

      {/* Retained owners */}
      {retained.length > 0 && (
        <div style={{ display: 'flex', flexDirection: 'column', gap: 6, marginBottom: 8 }}>
          {retained.map((owner) => (
            <div
              key={owner.id}
              style={{
                display: 'flex',
                alignItems: 'center',
                justifyContent: 'space-between',
                padding: '6px 10px',
                border: '1px solid var(--color-border)',
                borderRadius: 6,
                background: 'rgba(39,174,96,0.05)',
              }}
            >
              <div>
                <span style={{ fontWeight: 600 }}>{owner.name}</span>
                {owner.dateOfBirth && (
                  <span style={{ color: 'var(--color-text-2)', marginLeft: 8 }}>{owner.dateOfBirth}</span>
                )}
                {owner.role && (
                  <span style={{ color: 'var(--color-text-2)', marginLeft: 8, fontStyle: 'italic' }}>
                    {owner.role}
                  </span>
                )}
                <span
                  style={{
                    marginLeft: 8,
                    fontSize: 11,
                    color: 'var(--color-text-2)',
                    fontFamily: 'monospace',
                  }}
                >
                  {t(`ubo.origin.${owner.origin}`)}
                </span>
              </div>
              <div style={{ display: 'flex', gap: 6, alignItems: 'center' }}>
                <span
                  style={{
                    fontSize: 11,
                    padding: '1px 7px',
                    borderRadius: 10,
                    background: 'rgba(39,174,96,0.15)',
                    color: '#27ae60',
                    fontWeight: 600,
                  }}
                >
                  {t('ubo.retained')}
                </span>
                {confirmDiscard === owner.id ? (
                  <>
                    <span style={{ fontSize: 12, color: 'var(--color-text-2)' }}>{t('ubo.discardConfirm')}</span>
                    <button
                      className="btn btn-sm"
                      style={{ background: 'var(--color-error)', color: '#fff', fontSize: 11 }}
                      disabled={discarding}
                      onClick={() => handleDiscard(owner.id)}
                    >
                      ✓
                    </button>
                    <button
                      className="btn btn-secondary btn-sm"
                      style={{ fontSize: 11 }}
                      disabled={discarding}
                      onClick={() => setConfirmDiscard(null)}
                    >
                      ✕
                    </button>
                  </>
                ) : (
                  <button
                    className="btn btn-secondary btn-sm"
                    style={{ fontSize: 11 }}
                    onClick={() => setConfirmDiscard(owner.id)}
                  >
                    {t('ubo.discard')}
                  </button>
                )}
              </div>
            </div>
          ))}
        </div>
      )}

      {/* Discarded owners */}
      {discarded.length > 0 && (
        <div style={{ display: 'flex', flexDirection: 'column', gap: 4, marginBottom: 8 }}>
          {discarded.map((owner) => (
            <div
              key={owner.id}
              style={{
                display: 'flex',
                alignItems: 'center',
                justifyContent: 'space-between',
                padding: '4px 10px',
                opacity: 0.6,
              }}
            >
              <span style={{ textDecoration: 'line-through', color: 'var(--color-text-2)' }}>
                {owner.name}
              </span>
              <span
                style={{
                  fontSize: 11,
                  padding: '1px 7px',
                  borderRadius: 10,
                  background: 'rgba(127,127,127,0.1)',
                  color: 'var(--color-text-2)',
                  fontWeight: 600,
                }}
              >
                {t('ubo.discarded')}
              </span>
            </div>
          ))}
        </div>
      )}

      {/* Registry officers — suggested starting points */}
      {officers.length > 0 && (
        <div
          style={{
            marginTop: 10,
            paddingTop: 10,
            borderTop: '1px dashed var(--color-border)',
          }}
        >
          <span style={{ fontSize: 12, fontWeight: 600, color: 'var(--color-text-2)', display: 'block', marginBottom: 6 }}>
            {t('ubo.officers.title')}
          </span>
          <div style={{ display: 'flex', flexDirection: 'column', gap: 4 }}>
            {officers.map((name) => (
              <div
                key={name}
                style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', fontSize: 13 }}
              >
                <span>{name}</span>
                <button
                  className="btn btn-secondary btn-sm"
                  style={{ fontSize: 11 }}
                  onClick={() => {
                    setFormName(name);
                    setFormOriginFromRegistry(true);
                    setShowForm(true);
                  }}
                >
                  {t('ubo.officers.addHint')}
                </button>
              </div>
            ))}
          </div>
        </div>
      )}

      {/* Add form */}
      <div style={{ marginTop: 12, paddingTop: 10, borderTop: '1px solid var(--color-border)' }}>
        {!showForm ? (
          <button
            className="btn btn-secondary btn-sm"
            onClick={() => setShowForm(true)}
          >
            + {t('ubo.add.title')}
          </button>
        ) : (
          <div
            style={{
              display: 'flex',
              flexDirection: 'column',
              gap: 10,
              padding: '12px 14px',
              border: '1px solid var(--color-border)',
              borderRadius: 6,
              background: 'var(--color-bg-2, var(--color-bg))',
            }}
          >
            <span style={{ fontWeight: 600, fontSize: 13 }}>{t('ubo.add.title')}</span>

            {/* Name */}
            <div style={{ display: 'flex', flexDirection: 'column', gap: 4 }}>
              <label style={{ fontSize: 12, color: 'var(--color-text-2)' }}>{t('ubo.add.name')}</label>
              <input
                type="text"
                value={formName}
                onChange={(e) => { setFormName(e.target.value); setFormNameError(null); }}
                maxLength={200}
                disabled={submitting}
                style={{
                  padding: '6px 10px',
                  border: `1px solid ${formNameError ? 'var(--color-error)' : 'var(--color-border)'}`,
                  borderRadius: 5,
                  fontSize: 13,
                  background: 'var(--color-bg)',
                  color: 'var(--color-text)',
                }}
              />
              {formNameError && (
                <span style={{ fontSize: 11, color: 'var(--color-error)' }}>{formNameError}</span>
              )}
            </div>

            {/* Date of birth */}
            <div style={{ display: 'flex', flexDirection: 'column', gap: 4 }}>
              <label style={{ fontSize: 12, color: 'var(--color-text-2)' }}>{t('ubo.add.dateOfBirth')}</label>
              <input
                type="text"
                value={formDob}
                onChange={(e) => setFormDob(e.target.value)}
                placeholder="YYYY-MM-DD"
                maxLength={100}
                disabled={submitting}
                style={{
                  padding: '6px 10px',
                  border: '1px solid var(--color-border)',
                  borderRadius: 5,
                  fontSize: 13,
                  background: 'var(--color-bg)',
                  color: 'var(--color-text)',
                }}
              />
            </div>

            {/* Role */}
            <div style={{ display: 'flex', flexDirection: 'column', gap: 4 }}>
              <label style={{ fontSize: 12, color: 'var(--color-text-2)' }}>{t('ubo.add.role')}</label>
              <input
                type="text"
                value={formRole}
                onChange={(e) => setFormRole(e.target.value)}
                maxLength={200}
                disabled={submitting}
                style={{
                  padding: '6px 10px',
                  border: '1px solid var(--color-border)',
                  borderRadius: 5,
                  fontSize: 13,
                  background: 'var(--color-bg)',
                  color: 'var(--color-text)',
                }}
              />
            </div>

            <div style={{ display: 'flex', gap: 8 }}>
              <button
                className="btn btn-primary btn-sm"
                disabled={submitting}
                onClick={() => handleAddSubmit(
                  formName,
                  formOriginFromRegistry ? BeneficialOwnerOrigin.REGISTRY : BeneficialOwnerOrigin.DECLARED,
                )}
              >
                {t('ubo.add.submit')}
              </button>
              <button
                className="btn btn-secondary btn-sm"
                disabled={submitting}
                onClick={() => {
                  setShowForm(false);
                  setFormName('');
                  setFormDob('');
                  setFormRole('');
                  setFormNameError(null);
                  setFormOriginFromRegistry(false);
                }}
              >
                {/* reuse existing key */}
                ✕
              </button>
            </div>
          </div>
        )}
      </div>
    </div>
  );
}
