'use client';

import { useState, useEffect } from 'react';
import { useTranslations } from 'next-intl';
import { CampaignStatus } from '@/types/campaign';
import type { CampaignDto, UpdateCampaignRequest } from '@/types/campaign';

interface CampaignInfoTabProps {
  /** Current campaign data to pre-fill the form. */
  campaign: CampaignDto;
  /** Called with the changed fields when the user clicks "Enregistrer". */
  onSave: (data: UpdateCampaignRequest) => void;
  /** True while the save request is in-flight. */
  isSaving: boolean;
}

/** Input field shared styling classes. */
const INPUT_CLS =
  'w-full bg-[var(--color-bg-3)] border-[1.5px] border-[var(--color-border)] ' +
  'text-[var(--color-text)] px-[14px] py-[11px] rounded-[9px] text-[14px] outline-none ' +
  'placeholder:text-[var(--color-muted)] ' +
  'focus:border-[var(--color-green)]/45 focus:shadow-[0_0_0_3px_rgba(0,184,154,.07)] transition-all';

/** Label shared styling classes. */
const LABEL_CLS =
  'block text-[11.5px] font-semibold text-[var(--color-text-2)] uppercase tracking-wider mb-[6px]';

/**
 * Info tab of the campaign editor.
 *
 * Renders a form pre-filled from campaign data. The save button becomes enabled
 * only when changes have been made, and is disabled while a save is in-flight.
 */
export function CampaignInfoTab({ campaign, onSave, isSaving }: CampaignInfoTabProps) {
  const t = useTranslations('dashboard.campaigns');

  const [name, setName] = useState(campaign.name);
  const [goal, setGoal] = useState(String(campaign.goal));
  const [startDate, setStartDate] = useState(campaign.startDate ?? '');
  const [endDate, setEndDate] = useState(campaign.endDate ?? '');
  const [description, setDescription] = useState(campaign.description ?? '');
  const [status, setStatus] = useState<CampaignStatus>(campaign.status);
  const [contractAddress, setContractAddress] = useState(campaign.contractAddress ?? '');

  /* Sync when parent campaign data changes (e.g. after a save from the hero) */
  useEffect(() => {
    setName(campaign.name);
    setGoal(String(campaign.goal));
    setStartDate(campaign.startDate ?? '');
    setEndDate(campaign.endDate ?? '');
    setDescription(campaign.description ?? '');
    setStatus(campaign.status);
    setContractAddress(campaign.contractAddress ?? '');
  }, [campaign]);

  const isDirty =
    name !== campaign.name ||
    goal !== String(campaign.goal) ||
    startDate !== (campaign.startDate ?? '') ||
    endDate !== (campaign.endDate ?? '') ||
    description !== (campaign.description ?? '') ||
    status !== campaign.status ||
    contractAddress !== (campaign.contractAddress ?? '');

  const handleSave = () => {
    const data: UpdateCampaignRequest = {};
    if (name !== campaign.name) data.name = name;
    if (goal !== String(campaign.goal)) data.goal = Number(goal);
    if (startDate !== (campaign.startDate ?? '')) data.startDate = startDate || undefined;
    if (endDate !== (campaign.endDate ?? '')) data.endDate = endDate || undefined;
    if (description !== (campaign.description ?? '')) data.description = description;
    if (status !== campaign.status) data.status = status;
    if (contractAddress !== (campaign.contractAddress ?? '')) data.contractAddress = contractAddress || undefined;
    onSave(data);
  };

  return (
    <div
      className="rounded-[18px] border border-[var(--color-border)] p-[24px_28px]"
      style={{ background: 'var(--color-bg-2)' }}
    >
      {/* Card title */}
      <div className="flex items-center gap-[8px] mb-[16px]">
        <span className="inline-block w-[3px] h-[13px] rounded-[2px]" style={{ background: 'var(--color-green)' }} />
        <span
          className="text-[13px] font-bold text-[var(--color-text-2)] uppercase tracking-wider"
          style={{ fontFamily: 'var(--font-display)' }}
        >
          {t('editor.info.title')}
        </span>
      </div>

      {/* Row 1: name + goal */}
      <div className="grid grid-cols-2 gap-[14px] mb-[14px]">
        <div>
          <label className={LABEL_CLS}>{t('editor.info.name.label')}</label>
          <input
            type="text"
            className={INPUT_CLS}
            value={name}
            onChange={(e) => setName(e.target.value)}
            placeholder={t('editor.info.name.placeholder')}
          />
        </div>
        <div>
          <label className={LABEL_CLS}>{t('editor.info.goal.label')}</label>
          <input
            type="number"
            className={INPUT_CLS}
            value={goal}
            onChange={(e) => setGoal(e.target.value)}
            placeholder={t('editor.info.goal.placeholder')}
            min={0}
          />
        </div>
      </div>

      {/* Row 2: start date + end date */}
      <div className="grid grid-cols-2 gap-[14px] mb-[14px]">
        <div>
          <label className={LABEL_CLS}>{t('editor.info.startDate.label')}</label>
          <input
            type="date"
            className={INPUT_CLS}
            value={startDate}
            onChange={(e) => setStartDate(e.target.value)}
          />
        </div>
        <div>
          <label className={LABEL_CLS}>{t('editor.info.endDate.label')}</label>
          <input
            type="date"
            className={INPUT_CLS}
            value={endDate}
            onChange={(e) => setEndDate(e.target.value)}
          />
        </div>
      </div>

      {/* Row 3: description */}
      <div className="mb-[14px]">
        <label className={LABEL_CLS}>{t('editor.info.description.label')}</label>
        <textarea
          className={`${INPUT_CLS} resize-none min-h-[90px]`}
          value={description}
          onChange={(e) => setDescription(e.target.value)}
          placeholder={t('editor.info.description.placeholder')}
        />
      </div>

      {/* Row 4: status + contract */}
      <div className="grid grid-cols-2 gap-[14px]">
        <div>
          <label className={LABEL_CLS}>{t('editor.info.status.label')}</label>
          <select
            className={INPUT_CLS}
            value={status}
            onChange={(e) => setStatus(e.target.value as CampaignStatus)}
          >
            <option value={CampaignStatus.DRAFT}>⚙️ {t('status.draft')}</option>
            <option value={CampaignStatus.LIVE}>● {t('status.live')}</option>
            <option value={CampaignStatus.ENDED}>✕ {t('status.ended')}</option>
          </select>
        </div>
        <div>
          <label className={LABEL_CLS}>{t('editor.info.contract.label')}</label>
          <input
            type="text"
            className={`${INPUT_CLS} font-mono`}
            value={contractAddress}
            onChange={(e) => setContractAddress(e.target.value)}
            placeholder={t('editor.info.contract.placeholder')}
          />
        </div>
      </div>

      {/* Save button */}
      <div className="flex justify-end mt-[20px]">
        <button
          type="button"
          onClick={handleSave}
          disabled={!isDirty || isSaving}
          className="px-[20px] py-[10px] rounded-[9px] text-[13px] font-semibold transition-all
            bg-[var(--color-green)] text-[var(--color-bg)] cursor-pointer
            hover:opacity-90 disabled:opacity-40 disabled:cursor-not-allowed"
        >
          {isSaving ? '…' : t('editor.info.save')}
        </button>
      </div>
    </div>
  );
}
