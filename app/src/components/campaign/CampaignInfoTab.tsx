'use client';

import { useState, useEffect, useMemo } from 'react';
import { useTranslations } from 'next-intl';
import { BudgetSide } from '@/types/campaign';
import type { CampaignDto, UpdateCampaignRequest } from '@/types/campaign';

interface CampaignInfoTabProps {
  campaign: CampaignDto;
  onSave: (data: UpdateCampaignRequest) => void;
  isSaving: boolean;
}

function computeTotalCharges(campaign: CampaignDto): number {
  return campaign.budgetSections
    .filter((s) => s.side === BudgetSide.EXPENSE)
    .flatMap((s) => s.items)
    .reduce((sum, item) => sum + item.amount, 0);
}

export function CampaignInfoTab({ campaign, onSave, isSaving }: CampaignInfoTabProps) {
  const t = useTranslations('dashboard.campaigns');

  const [name, setName] = useState(campaign.name);
  const [goal, setGoal] = useState(String(campaign.goal));
  const [goalLinked, setGoalLinked] = useState(false);
  const [startDate, setStartDate] = useState(campaign.startDate ?? '');
  const [endDate, setEndDate] = useState(campaign.endDate ?? '');
  const [description, setDescription] = useState(campaign.description ?? '');
  const [category, setCategory] = useState(campaign.category ?? '');
  const [reason, setReason] = useState(campaign.reason ?? '');
  const [impactGoals, setImpactGoals] = useState(campaign.impactGoals ?? '');

  useEffect(() => {
    setName(campaign.name);
    setGoal(String(campaign.goal));
    setGoalLinked(false);
    setStartDate(campaign.startDate ?? '');
    setEndDate(campaign.endDate ?? '');
    setDescription(campaign.description ?? '');
    setCategory(campaign.category ?? '');
    setReason(campaign.reason ?? '');
    setImpactGoals(campaign.impactGoals ?? '');
  }, [campaign]);

  const totalCharges = useMemo(() => computeTotalCharges(campaign), [campaign]);

  const handleGoalLink = (checked: boolean) => {
    setGoalLinked(checked);
    if (checked) setGoal(String(totalCharges));
  };

  const dateError = useMemo(() => {
    if (!startDate || !endDate) return '';
    const diff = (new Date(endDate).getTime() - new Date(startDate).getTime()) / 86400000;
    return diff < 7 ? t('editor.info.dateError') : '';
  }, [startDate, endDate, t]);

  const isDirty =
    name !== campaign.name ||
    goal !== String(campaign.goal) ||
    startDate !== (campaign.startDate ?? '') ||
    endDate !== (campaign.endDate ?? '') ||
    description !== (campaign.description ?? '') ||
    category !== (campaign.category ?? '') ||
    reason !== (campaign.reason ?? '') ||
    impactGoals !== (campaign.impactGoals ?? '');

  const handleSave = () => {
    if (dateError) return;
    const data: UpdateCampaignRequest = {};
    if (name !== campaign.name) data.name = name;
    if (goal !== String(campaign.goal)) data.goal = Number(goal);
    if (startDate !== (campaign.startDate ?? '')) data.startDate = startDate || undefined;
    if (endDate !== (campaign.endDate ?? '')) data.endDate = endDate || undefined;
    if (description !== (campaign.description ?? '')) data.description = description;
    if (category !== (campaign.category ?? '')) data.category = category || undefined;
    if (reason !== (campaign.reason ?? '')) data.reason = reason || undefined;
    if (impactGoals !== (campaign.impactGoals ?? '')) data.impactGoals = impactGoals || undefined;
    onSave(data);
  };

  return (
    <div className="cm-card">
      <div className="cm-card-title">{t('editor.info.title')}</div>

      {/* Row 1: Nom + Objectif */}
      <div className="row2" style={{ marginBottom: 14 }}>
        <div>
          <label className="cm-label">{t('editor.info.name.label')}</label>
          <input
            className="cm-fi"
            type="text"
            value={name}
            onChange={(e) => setName(e.target.value)}
            placeholder={t('editor.info.name.placeholder')}
          />
        </div>
        <div>
          <label className="cm-label">
            {t('editor.info.goal.label')}{' '}
            <span className="tip" data-tip={t('editor.info.goal.tip')}>?</span>
          </label>
          <input
            className="cm-fi"
            type="number"
            value={goal}
            onChange={(e) => { setGoalLinked(false); setGoal(e.target.value); }}
            placeholder={t('editor.info.goal.placeholder')}
            min={0}
          />
          <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', marginTop: 4, gap: 8, flexWrap: 'wrap' }}>
            <label style={{ display: 'inline-flex', alignItems: 'center', gap: 6, fontSize: '11.5px', color: 'var(--slate-lavender)', cursor: 'pointer' }}>
              <input
                type="checkbox"
                checked={goalLinked}
                onChange={(e) => handleGoalLink(e.target.checked)}
                style={{ accentColor: 'var(--bright-teal)' }}
              />
              <span>{t('editor.info.goalLink')}</span>
            </label>
            {goalLinked && (
              <span style={{ fontSize: 11, color: 'var(--slate-lavender)' }}>
                = {totalCharges.toLocaleString()} €
              </span>
            )}
          </div>
        </div>
      </div>

      {/* Row 2: Dates */}
      <div className="row2" style={{ marginBottom: 14 }}>
        <div>
          <label className="cm-label">{t('editor.info.startDate.label')}</label>
          <input
            className="cm-fi"
            type="date"
            value={startDate}
            onChange={(e) => setStartDate(e.target.value)}
          />
        </div>
        <div>
          <label className="cm-label">
            {t('editor.info.endDate.label')}{' '}
            <span style={{ color: 'var(--slate-lavender)', fontWeight: 400, textTransform: 'none', letterSpacing: 0, fontSize: 10 }}>
              {t('editor.info.endDate.hint')}
            </span>
          </label>
          <input
            className="cm-fi"
            type="date"
            value={endDate}
            onChange={(e) => setEndDate(e.target.value)}
          />
          {dateError && (
            <div id="info-date-error">
              <span>⚠</span>
              <span>{dateError}</span>
            </div>
          )}
        </div>
      </div>

      {/* Catégorie */}
      <div style={{ marginBottom: 14 }}>
        <label className="cm-label">{t('editor.info.category.label')}</label>
        <select className="cm-fi" value={category} onChange={(e) => setCategory(e.target.value)}>
          <option value="">—</option>
          <option value="Education">🎓 Éducation</option>
          <option value="Alimentation">🍎 Alimentation</option>
          <option value="Environnement">🌱 Environnement</option>
          <option value="Santé">🏥 Santé</option>
        </select>
      </div>

      {/* Description */}
      <div style={{ marginBottom: 14 }}>
        <label className="cm-label">{t('editor.info.description.label')}</label>
        <textarea
          className="cm-fi"
          value={description}
          onChange={(e) => setDescription(e.target.value)}
          placeholder={t('editor.info.description.placeholder')}
          style={{ minHeight: 90 }}
        />
      </div>

      {/* Raison */}
      <div style={{ marginBottom: 14 }}>
        <label className="cm-label">
          {t('editor.info.reason.label')}{' '}
          <span className="tip" data-tip={t('editor.info.reason.tip')}>?</span>
        </label>
        <textarea
          className="cm-fi"
          value={reason}
          onChange={(e) => setReason(e.target.value)}
          placeholder={t('editor.info.reason.placeholder')}
          style={{ minHeight: 70 }}
        />
      </div>

      {/* Objectifs d'impact */}
      <div style={{ marginTop: 14 }}>
        <label className="cm-label">
          {t('editor.info.impactGoals.label')}{' '}
          <span className="tip" data-tip={t('editor.info.impactGoals.tip')}>?</span>
        </label>
        <textarea
          className="cm-fi"
          value={impactGoals}
          onChange={(e) => setImpactGoals(e.target.value)}
          placeholder={t('editor.info.impactGoals.placeholder')}
          style={{ minHeight: 90 }}
        />
      </div>

      {/* Image de couverture */}
      <div style={{ marginTop: 14 }}>
        <label className="cm-label">{t('editor.info.coverImage.label')}</label>
        <div className="upload">
          <div className="upload-icon">🖼️</div>
          <p>{t('editor.info.coverImage.uploadPrompt')}</p>
          <small>{t('editor.info.coverImage.hint')}</small>
        </div>
      </div>

      {/* Enregistrer */}
      <div style={{ display: 'flex', justifyContent: 'flex-end', marginTop: 20 }}>
        <button
          type="button"
          onClick={handleSave}
          disabled={!isDirty || isSaving || !!dateError}
          className="cm-btn cm-btn-primary cm-btn-sm"
        >
          {isSaving ? '⏳' : t('editor.info.save')}
        </button>
      </div>
    </div>
  );
}
