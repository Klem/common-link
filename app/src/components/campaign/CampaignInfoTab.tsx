'use client';

import { useState, useEffect, useMemo, useRef } from 'react';
import { useTranslations } from 'next-intl';
import { useDebouncedPatchSave } from '@/hooks/campaign/useDebouncedSave';
import {
  uploadCampaignCover,
  deleteCampaignCover,
  campaignCoverUrl,
} from '@/lib/api/campaign';
import type { CampaignDto, UpdateCampaignRequest } from '@/types/campaign';

/** Types MIME acceptés pour la couverture — miroir de `COVER_IMAGE_ALLOWED_MIME` côté back (règle 8). */
const COVER_ALLOWED_MIME = ['image/jpeg', 'image/png', 'image/webp'];

/** Taille maximale de la couverture — miroir de `MAX_COVER_IMAGE_SIZE` côté back (règle 8). */
const COVER_MAX_SIZE = 5 * 1024 * 1024;

interface CampaignInfoTabProps {
  campaign: CampaignDto;
  onSave: (data: UpdateCampaignRequest, silent?: boolean) => void;
  /** Appelé avec le DTO retourné après ajout ou retrait de la couverture. */
  onCoverChange: (updated: CampaignDto) => void;
  isSaving: boolean;
}

export function CampaignInfoTab({ campaign, onSave, onCoverChange, isSaving }: CampaignInfoTabProps) {
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

  /* ── Image de couverture ─────────────────────────────────────────────── */

  const coverInputRef = useRef<HTMLInputElement>(null);
  const [dragOver, setDragOver] = useState(false);
  const [coverBusy, setCoverBusy] = useState(false);
  const [coverError, setCoverError] = useState('');

  /**
   * Valide le fichier côté client (mêmes règles que le back) puis l'envoie.
   * Un fichier refusé ne déclenche aucun appel réseau.
   */
  const uploadCover = async (file: File) => {
    if (!COVER_ALLOWED_MIME.includes(file.type)) {
      setCoverError(t('editor.info.coverImage.errorType'));
      return;
    }
    if (file.size > COVER_MAX_SIZE) {
      setCoverError(t('editor.info.coverImage.errorSize'));
      return;
    }
    setCoverError('');
    setCoverBusy(true);
    try {
      onCoverChange(await uploadCampaignCover(campaign.id, file));
    } catch {
      setCoverError(t('editor.info.coverImage.errorUpload'));
    } finally {
      setCoverBusy(false);
    }
  };

  const handleCoverDrop = (e: React.DragEvent<HTMLDivElement>) => {
    e.preventDefault();
    setDragOver(false);
    const file = e.dataTransfer.files?.[0];
    if (file) uploadCover(file);
  };

  const handleCoverRemove = async () => {
    setCoverError('');
    setCoverBusy(true);
    try {
      onCoverChange(await deleteCampaignCover(campaign.id));
    } catch {
      setCoverError(t('editor.info.coverImage.errorUpload'));
    } finally {
      setCoverBusy(false);
    }
  };

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
            id="info-goal"
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
            id="info-start"
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
          id="info-desc"
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
          id="info-reason"
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
          id="info-impact-goals"
          className="cm-fi cm-fi-h90"
          value={impactGoals}
          onChange={(e) => handleImpactGoalsChange(e.target.value)}
          placeholder={t('editor.info.impactGoals.placeholder')}
        />
      </div>

      {/* Image de couverture */}
      <div className="mt-14">
        <label className="cm-label">{t('editor.info.coverImage.label')}</label>
        {campaign.coverImage ? (
          <div className="upload-preview">
            {/* eslint-disable-next-line @next/next/no-img-element -- served by the API, not by Next */}
            <img
              src={campaignCoverUrl(campaign.coverImage, campaign.updatedAt)}
              alt={t('editor.info.coverImage.label')}
            />
            <button
              type="button"
              className="upload-preview-remove"
              onClick={handleCoverRemove}
              disabled={coverBusy}
            >
              {coverBusy ? '⏳' : t('editor.info.coverImage.remove')}
            </button>
          </div>
        ) : (
          <div
            className={`upload${dragOver ? ' dragover' : ''}`}
            role="button"
            tabIndex={0}
            onClick={() => coverInputRef.current?.click()}
            onKeyDown={(e) => {
              if (e.key === 'Enter' || e.key === ' ') coverInputRef.current?.click();
            }}
            onDragOver={(e) => { e.preventDefault(); setDragOver(true); }}
            onDragLeave={() => setDragOver(false)}
            onDrop={handleCoverDrop}
          >
            <div className="upload-icon">🖼️</div>
            <p>{coverBusy ? t('editor.info.coverImage.uploading') : t('editor.info.coverImage.uploadPrompt')}</p>
            <small>{t('editor.info.coverImage.hint')}</small>
          </div>
        )}
        <input
          ref={coverInputRef}
          type="file"
          hidden
          accept={COVER_ALLOWED_MIME.join(',')}
          onChange={(e) => {
            const file = e.target.files?.[0];
            // Réinitialise l'input pour que re-sélectionner le même fichier redéclenche onChange.
            e.target.value = '';
            if (file) uploadCover(file);
          }}
        />
        {coverError && <div className="upload-error">⚠ {coverError}</div>}
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
