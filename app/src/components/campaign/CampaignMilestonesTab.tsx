'use client';

import { useState, useEffect, useRef, useCallback } from 'react';
import { useTranslations } from 'next-intl';
import { useMilestones } from '@/hooks/campaign/useMilestones';
import { MilestoneStatus } from '@/types/campaign';
import type { CampaignDto, MilestoneDto, MilestoneStatus as MilestoneStatusType, UpdateMilestoneRequest } from '@/types/campaign';

const EMOJIS = [
  '🌱','🌿','🌳','🏠','🏫','🏥','🌊','💧','🍎','📚','🎒','🩺','🤝','🌟','⭐','💡',
  '🎨','🌈','🕊️','🌻','🏆','🚀','❤️','💚','💙','☀️','🌍','🎓','🍲','🔥','🎁','❄️','🧣',
];

interface CampaignMilestonesTabProps {
  campaign: CampaignDto;
  onMilestonesChanged: () => void;
}

export function CampaignMilestonesTab({ campaign, onMilestonesChanged }: CampaignMilestonesTabProps) {
  const t = useTranslations('dashboard.campaigns');
  const { isSaving, addNewMilestone, updateExistingMilestone, removeExistingMilestone } = useMilestones();

  const sorted = [...campaign.milestones].sort((a, b) => a.sortOrder - b.sortOrder);

  const handleAdd = async () => {
    const lastAmount = sorted.length > 0 ? sorted[sorted.length - 1].targetAmount : 0;
    const defaultAmount = Math.min(lastAmount, campaign.goal);
    await addNewMilestone(campaign.id, sorted.length, defaultAmount);
    onMilestonesChanged();
  };

  const handleDelete = async (milestoneId: string) => {
    await removeExistingMilestone(campaign.id, milestoneId);
    onMilestonesChanged();
  };

  return (
    <div>
      {/* Header */}
      <div style={{ display: 'flex', alignItems: 'flex-start', justifyContent: 'space-between', marginBottom: '16px', gap: '12px' }}>
        <div>
          <div style={{ fontFamily: "'Nunito Sans', sans-serif", fontSize: '15px', fontWeight: 900, color: 'var(--ink-navy)' }}>
            {t('editor.milestones.title')}
          </div>
          <div style={{ fontSize: '12px', color: 'var(--slate-lavender)', marginTop: '3px' }}>
            {t('editor.milestones.subtitle')}
          </div>
        </div>
        {sorted.length > 0 && (
          <button
            type="button"
            onClick={handleAdd}
            disabled={isSaving}
            className="cm-btn cm-btn-primary cm-btn-sm"
          >
            + {t('editor.milestones.add')}
          </button>
        )}
      </div>

      {/* Empty state */}
      {sorted.length === 0 && (
        <div className="cm-card">
          <div className="empty-state">
            <div className="empty-state-icon">🎯</div>
            <div className="empty-state-title">{t('editor.milestones.emptyTitle')}</div>
            <div className="empty-state-desc">{t('editor.milestones.emptyDesc')}</div>
            <div className="ms-formula" style={{ maxWidth: '440px', textAlign: 'left' }}>
              💡 {t('editor.milestones.formulaFrom')} <strong>5 000 €</strong>,{' '}
              {t('editor.milestones.formulaCanDo')} <em>{t('editor.milestones.formulaImpactPlaceholder')}</em>
            </div>
            <button type="button" onClick={handleAdd} disabled={isSaving} className="btn btn-primary">
              {t('editor.milestones.emptyCreate')}
            </button>
            <div style={{ marginTop: '12px', fontSize: '11.5px', color: 'var(--slate-lavender)' }}>
              {t('editor.milestones.emptyHint')}
            </div>
          </div>
        </div>
      )}

      {/* Milestone cards */}
      <div id="ms-list">
        {sorted.map((milestone, idx) => {
          const prevTarget = idx > 0 ? sorted[idx - 1].targetAmount : 0;
          return (
            <MilestoneCard
              key={milestone.id}
              milestone={milestone}
              idx={idx}
              campaignRaised={campaign.raised}
              campaignGoal={campaign.goal}
              campaignId={campaign.id}
              prevTarget={prevTarget}
              onUpdate={updateExistingMilestone}
              onDelete={handleDelete}
              t={t}
            />
          );
        })}
      </div>
    </div>
  );
}

/* ─── MilestoneCard ─── */

interface MilestoneCardProps {
  milestone: MilestoneDto;
  idx: number;
  campaignRaised: number;
  campaignGoal: number;
  campaignId: string;
  prevTarget: number;
  onUpdate: (campaignId: string, milestoneId: string, data: UpdateMilestoneRequest) => Promise<void>;
  onDelete: (milestoneId: string) => Promise<void>;
  t: ReturnType<typeof useTranslations<'dashboard.campaigns'>>;
}

function MilestoneCard({
  milestone, idx, campaignRaised, campaignGoal, campaignId, prevTarget, onUpdate, onDelete, t,
}: MilestoneCardProps) {
  const [title, setTitle] = useState(milestone.title);
  const [impact, setImpact] = useState(milestone.description ?? '');
  const [proof, setProof] = useState(milestone.transparencyCommitment ?? '');
  const [targetAmount, setTargetAmount] = useState(String(milestone.targetAmount));
  const [pickerOpen, setPickerOpen] = useState(false);
  const [pendingDelete, setPendingDelete] = useState(false);
  const pickerRef = useRef<HTMLDivElement>(null);
  const cardRef = useRef<HTMLDivElement>(null);
  const debounceTimer = useRef<ReturnType<typeof setTimeout> | null>(null);

  useEffect(() => {
    setTitle(milestone.title);
    setImpact(milestone.description ?? '');
    setProof(milestone.transparencyCommitment ?? '');
    setTargetAmount(String(milestone.targetAmount));
  }, [milestone]);

  useEffect(() => {
    function handle(e: MouseEvent) {
      if (pickerRef.current && !pickerRef.current.contains(e.target as Node)) setPickerOpen(false);
    }
    if (pickerOpen) document.addEventListener('mousedown', handle);
    return () => document.removeEventListener('mousedown', handle);
  }, [pickerOpen]);

  useEffect(() => {
    function handle(e: MouseEvent) {
      if (cardRef.current && !cardRef.current.contains(e.target as Node)) setPendingDelete(false);
    }
    if (pendingDelete) document.addEventListener('mousedown', handle);
    return () => document.removeEventListener('mousedown', handle);
  }, [pendingDelete]);

  const scheduleUpdate = useCallback(
    (patch: UpdateMilestoneRequest) => {
      if (debounceTimer.current) clearTimeout(debounceTimer.current);
      debounceTimer.current = setTimeout(() => { onUpdate(campaignId, milestone.id, patch); }, 800);
    },
    [campaignId, milestone.id, onUpdate],
  );

  const handleTitleChange = (v: string) => { setTitle(v); scheduleUpdate({ title: v }); };
  const handleImpactChange = (v: string) => { setImpact(v); scheduleUpdate({ description: v }); };
  const handleProofChange = (v: string) => { setProof(v); scheduleUpdate({ transparencyCommitment: v }); };
  const handleTargetChange = (v: string) => {
    setTargetAmount(v);
    const num = parseFloat(v);
    if (!isNaN(num) && num > 0) scheduleUpdate({ targetAmount: num });
  };
  const handleEmojiSelect = (emoji: string) => {
    setPickerOpen(false);
    onUpdate(campaignId, milestone.id, { emoji });
  };
  const handleDeleteClick = () => {
    if (pendingDelete) { onDelete(milestone.id); }
    else { setPendingDelete(true); }
  };

  const numericAmount = parseFloat(targetAmount);
  const isUnreachable = !isNaN(numericAmount) && numericAmount > campaignGoal;
  const isBelowPrev = !isNaN(numericAmount) && idx > 0 && numericAmount <= prevTarget;
  const pct = milestone.targetAmount > 0
    ? Math.min(Math.round((campaignRaised / milestone.targetAmount) * 100), 100) : 0;
  const displayPct = milestone.status === MilestoneStatus.REACHED ? 100 : pct;

  const barColor = milestone.status === MilestoneStatus.REACHED
    ? 'var(--bright-teal)'
    : milestone.status === MilestoneStatus.CURRENT ? '#f5a623' : 'var(--mist-lavender)';

  const numCls = milestone.status === MilestoneStatus.REACHED
    ? 'reached' : milestone.status === MilestoneStatus.CURRENT ? 'current' : '';

  const cardCls = ['ms-guided',
    milestone.status === MilestoneStatus.REACHED ? 'reached' : '',
    milestone.status === MilestoneStatus.CURRENT ? 'current' : '',
    isUnreachable ? 'unreachable' : '',
  ].filter(Boolean).join(' ');

  const fmtEur = (n: number) =>
    n.toLocaleString('fr-FR', { style: 'currency', currency: 'EUR', maximumFractionDigits: 0 });

  return (
    <div className={cardCls} ref={cardRef}>
      {/* 2-step delete */}
      <button
        type="button"
        className="ms-remove"
        onClick={handleDeleteClick}
        style={pendingDelete ? { color: 'var(--warm-coral)', fontWeight: 700, fontSize: '10px', width: 'auto', right: '8px' } : undefined}
      >
        {pendingDelete ? t('editor.milestones.deleteConfirm2') : '✕'}
      </button>

      {/* Header: emoji + step number + title */}
      <div className="ms-guided-header">
        <div className="ms-emoji" ref={pickerRef} onClick={() => setPickerOpen((o) => !o)}>
          {milestone.emoji || '🎯'}
          <div className={`ms-emoji-pick${pickerOpen ? ' open' : ''}`}>
            {EMOJIS.map((em) => (
              <button key={em} type="button" className="ms-emoji-opt"
                onClick={(e) => { e.stopPropagation(); handleEmojiSelect(em); }}>
                {em}
              </button>
            ))}
          </div>
        </div>
        <div className={`ms-guided-num${numCls ? ` ${numCls}` : ''}`}>{idx + 1}</div>
        <div className="ms-guided-title-wrap" style={{ paddingRight: '28px' }}>
          <input
            className="ms-guided-title-input"
            value={title}
            onChange={(e) => handleTitleChange(e.target.value)}
            placeholder={t('editor.milestones.namePlaceholder')}
          />
        </div>
      </div>

      {/* Formula preview */}
      <div className="ms-formula">
        {t('editor.milestones.formulaFrom')}{' '}
        <strong>{numericAmount > 0 ? fmtEur(numericAmount) : t('editor.milestones.formulaAmountPlaceholder')}</strong>,{' '}
        {t('editor.milestones.formulaCanDo')}{' '}
        <em>{impact || t('editor.milestones.formulaImpactPlaceholder')}</em>
      </div>

      {/* Amount */}
      <div className="ms-guided-field">
        <div className="ms-guided-field-label">
          {t('editor.milestones.amountLabel')}
          <span className="pill-hint">{t('editor.milestones.pillAmountHint')}</span>
        </div>
        <div className="ms-guided-amount-row">
          <div className="ms-guided-amount-box"
            style={isUnreachable || isBelowPrev ? { borderColor: 'rgba(255,107,91,.5)' } : undefined}>
            <span>€</span>
            <input
              type="number"
              value={targetAmount}
              placeholder="5 000"
              min={prevTarget > 0 ? prevTarget + 1 : 1}
              onChange={(e) => handleTargetChange(e.target.value)}
            />
          </div>
          <span style={{ fontSize: '11px', color: 'var(--slate-lavender)', flex: 1 }}>
            {displayPct > 0 ? `${displayPct}${t('editor.milestones.pctSuffix')}` : ''}
          </span>
        </div>
        {isBelowPrev && (
          <div style={{ fontSize: '11px', color: 'var(--warm-coral)', marginTop: '4px', display: 'flex', alignItems: 'center', gap: '4px' }}>
            ⚠ {t('editor.milestones.orderInvalid')} ({fmtEur(prevTarget)})
          </div>
        )}
      </div>

      {/* Impact */}
      <div className="ms-guided-field">
        <div className="ms-guided-field-label">
          {t('editor.milestones.impactLabel')}
          <span className="pill-hint">{t('editor.milestones.pillImpactHint')}</span>
        </div>
        <input
          className="ms-guided-impact"
          type="text"
          value={impact}
          placeholder={t('editor.milestones.impactPlaceholder')}
          onChange={(e) => handleImpactChange(e.target.value)}
        />
      </div>

      {/* Proof / Transparency commitment */}
      <div className="ms-guided-field" style={{ marginBottom: 0 }}>
        <div className="ms-guided-field-label">
          {t('editor.milestones.proofLabel')}
          <span className="pill-hint">{t('editor.milestones.pillOptionalHint')}</span>
        </div>
        <textarea
          className="ms-guided-proof"
          value={proof}
          placeholder={t('editor.milestones.proofPlaceholder')}
          onChange={(e) => handleProofChange(e.target.value)}
        />
      </div>

      {/* Footer */}
      <div className="ms-guided-footer">
        <StatusBadge status={milestone.status} t={t} />
        {isUnreachable && (
          <span className="ms-badge-unreachable">⚠ {t('editor.milestones.unreachable')}</span>
        )}
        {isBelowPrev && !isUnreachable && (
          <span style={{ background: 'rgba(255,107,91,.1)', color: 'var(--warm-coral)', padding: '3px 9px', borderRadius: '50px', fontSize: '10px', fontWeight: 700 }}>
            ⚠ {t('editor.milestones.orderInvalid')}
          </span>
        )}
        <div className="ms-guided-bar">
          <div className="ms-guided-bar-fill" style={{ width: `${displayPct}%`, background: barColor }} />
        </div>
        <span style={{
          fontFamily: "'Syne', sans-serif", fontSize: '12px', fontWeight: 700,
          color: isUnreachable ? 'var(--warm-coral)'
            : milestone.status === MilestoneStatus.REACHED ? 'var(--teal-dark)'
            : milestone.status === MilestoneStatus.CURRENT ? '#b37800'
            : 'var(--slate-lavender)',
        }}>
          {numericAmount > 0 ? fmtEur(numericAmount) : '—'}
        </span>
      </div>
    </div>
  );
}

function StatusBadge({ status, t }: { status: MilestoneStatusType; t: ReturnType<typeof useTranslations<'dashboard.campaigns'>> }) {
  const base: React.CSSProperties = { padding: '3px 9px', borderRadius: '50px', fontSize: '10px', fontWeight: 700, fontFamily: "'Syne', sans-serif" };
  switch (status) {
    case MilestoneStatus.REACHED:
      return <span style={{ ...base, background: 'rgba(78,205,196,.12)', color: 'var(--teal-dark)' }}>✓ {t('editor.milestones.reached')}</span>;
    case MilestoneStatus.CURRENT:
      return <span style={{ ...base, background: 'rgba(255,179,71,.1)', color: '#b37800' }}>⏳ {t('editor.milestones.current')}</span>;
    default:
      return <span style={{ ...base, background: 'rgba(122,113,162,.1)', color: 'var(--slate-lavender)' }}>🔒 {t('editor.milestones.locked')}</span>;
  }
}
