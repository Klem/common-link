'use client';

import { useState, useEffect, useMemo } from 'react';
import { useTranslations } from 'next-intl';
import { useDebouncedPatchSave } from '@/hooks/campaign/useDebouncedSave';
import type { CampaignDto, UpdateCampaignRequest } from '@/types/campaign';

interface CampaignInfoTabProps {
  campaign: CampaignDto;
  onSave: (data: UpdateCampaignRequest, silent?: boolean) => void;
  isSaving: boolean;
}

export function CampaignInfoTab({ campaign, onSave, isSaving }: CampaignInfoTabProps) {
  const t = useTranslations('dashboard.campaigns');

  const [name, setName] = useState(campaign.name);
  const [goal, setGoal] = useState(String(campaign.goal));
  const [startDate, setStartDate] = useState(campaign.startDate ?? '');
  const [endDate, setEndDate] = useState(campaign.endDate ?? '');
  const [description, setDescription] = useState(campaign.description ?? '');
  const [category, setCategory] = useState(campaign.category ?? '');
  const [reason, setReason] = useState(campaign.reason ?? '');
  const [impactGoals, setImpactGoals] = useState(campaign.impactGoals ?? '');

  useEffect(() => {
    setName(campaign.name);
    setGoal(String(campaign.goal));
    setStartDate(campaign.startDate ?? '');
    setEndDate(campaign.endDate ?? '');
    setDescription(campaign.description ?? '');
    setCategory(campaign.category ?? '');
    setReason(campaign.reason ?? '');
    setImpactGoals(campaign.impactGoals ?? '');
  }, [campaign]);

  const dateError = useMemo(() => {
    if (!startDate || !endDate) return '';
    const diff = (new Date(endDate).getTime() - new Date(startDate).getTime()) / 86400000;
    return diff < 7 ? t('editor.info.dateError') : '';
  }, [startDate, endDate, t]);

  const { schedule, cancel } = useDebouncedPatchSave<UpdateCampaignRequest>((patch) => {
    if (!dateError) onSave(patch, true);
  });

  const handleNameChange = (v: string) => { setName(v); schedule({ name: v }); };
  const handleGoalChange = (v: string) => {
    setGoal(v);
    const num = Number(v);
    if (!isNaN(num)) schedule({ goal: num });
  };
  const handleStartDateChange = (v: string) => { setStartDate(v); schedule({ startDate: v || undefined }); };
  const handleEndDateChange = (v: string) => { setEndDate(v); schedule({ endDate: v || undefined }); };
  const handleCategoryChange = (v: string) => { setCategory(v); schedule({ category: v || undefined }); };
  const handleDescriptionChange = (v: string) => { setDescription(v); schedule({ description: v }); };
  const handleReasonChange = (v: string) => { setReason(v); schedule({ reason: v || undefined }); };
  const handleImpactGoalsChange = (v: string) => { setImpactGoals(v); schedule({ impactGoals: v || undefined }); };

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
    cancel();
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
      <div className="row2 mb-14">
        <div>
          <label className="cm-label">{t('editor.info.name.label')}</label>
          <input
            className="cm-fi"
            type="text"
            value={name}
            onChange={(e) => handleNameChange(e.target.value)}
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
            onChange={(e) => handleGoalChange(e.target.value)}
            placeholder={t('editor.info.goal.placeholder')}
            min={0}
          />
        </div>
      </div>

      {/* Row 2: Dates */}
      <div className="row2 mb-14">
        <div>
          <label className="cm-label">{t('editor.info.startDate.label')}</label>
          <input
            className="cm-fi"
            type="date"
            value={startDate}
            onChange={(e) => handleStartDateChange(e.target.value)}
          />
        </div>
        <div>
          <label className="cm-label">
            {t('editor.info.endDate.label')}{' '}
            <span className="cm-label-hint">
              {t('editor.info.endDate.hint')}
            </span>
          </label>
          <input
            className="cm-fi"
            type="date"
            value={endDate}
            onChange={(e) => handleEndDateChange(e.target.value)}
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
      <div className="mb-14">
        <label className="cm-label">{t('editor.info.category.label')}</label>
        <select className="cm-fi" value={category} onChange={(e) => handleCategoryChange(e.target.value)}>
          <option value="">—</option>
          <option value="Education">🎓 Éducation</option>
          <option value="Alimentation">🍎 Alimentation</option>
          <option value="Environnement">🌱 Environnement</option>
          <option value="Santé">🏥 Santé</option>
        </select>
      </div>

      {/* Description */}
      <div className="mb-14">
        <label className="cm-label">{t('editor.info.description.label')}</label>
        <textarea
          className="cm-fi cm-fi-h90"
          value={description}
          onChange={(e) => handleDescriptionChange(e.target.value)}
          placeholder={t('editor.info.description.placeholder')}
        />
      </div>

      {/* Raison */}
      <div className="mb-14">
        <label className="cm-label">
          {t('editor.info.reason.label')}{' '}
          <span className="tip" data-tip={t('editor.info.reason.tip')}>?</span>
        </label>
        <textarea
          className="cm-fi cm-fi-h70"
          value={reason}
          onChange={(e) => handleReasonChange(e.target.value)}
          placeholder={t('editor.info.reason.placeholder')}
        />
      </div>

      {/* Objectifs d'impact */}
      <div className="mt-14">
        <label className="cm-label">
          {t('editor.info.impactGoals.label')}{' '}
          <span className="tip" data-tip={t('editor.info.impactGoals.tip')}>?</span>
        </label>
        <textarea
          className="cm-fi cm-fi-h90"
          value={impactGoals}
          onChange={(e) => handleImpactGoalsChange(e.target.value)}
          placeholder={t('editor.info.impactGoals.placeholder')}
        />
      </div>

      {/* Image de couverture */}
      <div className="mt-14">
        <label className="cm-label">{t('editor.info.coverImage.label')}</label>
        <div className="upload">
          <div className="upload-icon">🖼️</div>
          <p>{t('editor.info.coverImage.uploadPrompt')}</p>
          <small>{t('editor.info.coverImage.hint')}</small>
        </div>
      </div>

      {/* Enregistrer */}
      <div className="form-save-row">
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
