'use client';

import { useState, useEffect, useRef, useCallback } from 'react';
import { useTranslations } from 'next-intl';
import { useMilestones } from '@/hooks/campaign/useMilestones';
import { MilestoneStatus } from '@/types/campaign';
import type { CampaignDto, MilestoneDto, MilestoneStatus as MilestoneStatusType } from '@/types/campaign';

const EMOJIS = [
  '🌱','🌿','🌳','🏠','🏫','🏥','🌊','💧','🍎','📚','🎒','🩺','🤝','🌟','⭐','💡',
  '🎨','🌈','🕊️','🌻','🏆','🚀','❤️','💚','💙','☀️','🌍','🎓','🍲','🔥','🎁','❄️','🧣',
];

interface CampaignMilestonesTabProps {
  /** Campaign with its milestones. */
  campaign: CampaignDto;
  /** Called after any milestone mutation so the parent can refresh campaign data. */
  onMilestonesChanged: () => void;
}

/**
 * Milestones tab of the campaign editor.
 *
 * Milestones are sorted by sortOrder. Each card knows its min/max amount bounds:
 * - min = previous milestone's targetAmount (or 0 for the first)
 * - max = campaign.goal
 *
 * Edits are debounced (800ms) and only sent when the value is within bounds.
 */
export function CampaignMilestonesTab({ campaign, onMilestonesChanged }: CampaignMilestonesTabProps) {
  const t = useTranslations('dashboard.campaigns');
  const { isSaving, addNewMilestone, updateExistingMilestone, removeExistingMilestone } =
    useMilestones();

  /* Milestones sorted by sortOrder for consistent bound computation */
  const sorted = [...campaign.milestones].sort((a, b) => a.sortOrder - b.sortOrder);

  const handleAdd = async () => {
    const lastAmount = sorted.length > 0 ? sorted[sorted.length - 1].targetAmount : 0;
    const defaultAmount = Math.min(lastAmount, campaign.goal);
    await addNewMilestone(campaign.id, sorted.length, defaultAmount);
    onMilestonesChanged();
  };

  const handleDelete = async (milestoneId: string) => {
    if (!window.confirm(t('editor.milestones.deleteConfirm'))) return;
    await removeExistingMilestone(campaign.id, milestoneId);
    onMilestonesChanged();
  };

  return (
    <div>
      {/* Header */}
      <div className="flex items-start justify-between mb-[16px]">
        <div>
          <div
            className="text-[15px] font-bold text-[var(--color-text)]"
            style={{ fontFamily: 'var(--font-display)' }}
          >
            {t('editor.milestones.title')}
          </div>
          <div className="text-[12px] text-[var(--color-text-2)] mt-[2px]">
            {t('editor.milestones.subtitle')}
          </div>
        </div>
        <button
          type="button"
          onClick={handleAdd}
          disabled={isSaving}
          className="px-[14px] py-[8px] rounded-[8px] text-[12px] font-semibold bg-[var(--color-green)] text-[var(--color-bg)] cursor-pointer hover:opacity-90 disabled:opacity-40 disabled:cursor-not-allowed transition-all"
        >
          + {t('editor.milestones.add')}
        </button>
      </div>

      {/* Milestone cards — each knows its allowed range */}
      <div>
        {sorted.map((milestone, idx) => {
          const minAmount = idx > 0 ? sorted[idx - 1].targetAmount : 0;
          const maxAmount = campaign.goal;
          return (
            <MilestoneCard
              key={milestone.id}
              milestone={milestone}
              campaignRaised={campaign.raised}
              campaignId={campaign.id}
              minAmount={minAmount}
              maxAmount={maxAmount}
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
  campaignRaised: number;
  campaignId: string;
  /** Minimum allowed targetAmount (= previous milestone's targetAmount, or 0). */
  minAmount: number;
  /** Maximum allowed targetAmount (= campaign.goal). */
  maxAmount: number;
  onUpdate: (campaignId: string, milestoneId: string, data: { title?: string; emoji?: string; description?: string; targetAmount?: number }) => Promise<void>;
  onDelete: (milestoneId: string) => Promise<void>;
  t: ReturnType<typeof useTranslations<'dashboard.campaigns'>>;
}

/** Returns border and background classes based on milestone status. */
function statusClasses(status: MilestoneStatusType): string {
  switch (status) {
    case MilestoneStatus.REACHED:
      return 'border-[var(--color-green)]/30 bg-gradient-to-br from-[var(--color-green)]/4 to-transparent';
    case MilestoneStatus.CURRENT:
      return 'border-[var(--color-yellow)]/30';
    default:
      return 'opacity-50';
  }
}

/** Returns the progress bar fill colour based on milestone status. */
function barColor(status: MilestoneStatusType): string {
  switch (status) {
    case MilestoneStatus.REACHED: return 'var(--color-green)';
    case MilestoneStatus.CURRENT: return 'var(--color-yellow)';
    default: return 'var(--color-muted)';
  }
}

/**
 * Individual milestone card with inline editing and an emoji picker.
 *
 * The targetAmount input is validated against [minAmount, maxAmount].
 * An error hint is shown when the value is out of bounds, and the API
 * call is suppressed until the value is valid.
 */
function MilestoneCard({
  milestone,
  campaignRaised,
  campaignId,
  minAmount,
  maxAmount,
  onUpdate,
  onDelete,
  t,
}: MilestoneCardProps) {
  const [title, setTitle] = useState(milestone.title);
  const [description, setDescription] = useState(milestone.description ?? '');
  const [targetAmount, setTargetAmount] = useState(String(milestone.targetAmount));
  const [pickerOpen, setPickerOpen] = useState(false);
  const pickerRef = useRef<HTMLDivElement>(null);
  const debounceTimer = useRef<ReturnType<typeof setTimeout> | null>(null);

  /* Sync local state if parent milestone data changes (after refresh) */
  useEffect(() => {
    setTitle(milestone.title);
    setDescription(milestone.description ?? '');
    setTargetAmount(String(milestone.targetAmount));
  }, [milestone]);

  /* Close emoji picker on outside click */
  useEffect(() => {
    function handle(e: MouseEvent) {
      if (pickerRef.current && !pickerRef.current.contains(e.target as Node)) {
        setPickerOpen(false);
      }
    }
    if (pickerOpen) document.addEventListener('mousedown', handle);
    return () => document.removeEventListener('mousedown', handle);
  }, [pickerOpen]);

  /** Schedules a debounced API update. */
  const scheduleUpdate = useCallback(
    (patch: { title?: string; description?: string; targetAmount?: number }) => {
      if (debounceTimer.current) clearTimeout(debounceTimer.current);
      debounceTimer.current = setTimeout(() => {
        onUpdate(campaignId, milestone.id, patch);
      }, 800);
    },
    [campaignId, milestone.id, onUpdate],
  );

  const handleTitleChange = (v: string) => { setTitle(v); scheduleUpdate({ title: v }); };
  const handleDescChange = (v: string) => { setDescription(v); scheduleUpdate({ description: v }); };

  const handleTargetChange = (v: string) => {
    setTargetAmount(v);
    const num = parseFloat(v);
    if (!isNaN(num) && num >= minAmount && num <= maxAmount) {
      scheduleUpdate({ targetAmount: num });
    }
  };

  const handleEmojiSelect = (emoji: string) => {
    setPickerOpen(false);
    onUpdate(campaignId, milestone.id, { emoji });
  };

  /* Amount validation */
  const numericAmount = parseFloat(targetAmount);
  const amountError: string | null = (() => {
    if (isNaN(numericAmount)) return null;
    if (numericAmount > maxAmount)
      return `Max : ${maxAmount.toLocaleString('fr-FR')} € (objectif de la campagne)`;
    if (numericAmount < minAmount)
      return `Min : ${minAmount.toLocaleString('fr-FR')} € (palier précédent)`;
    return null;
  })();

  const pct =
    milestone.targetAmount > 0
      ? Math.min(Math.round((campaignRaised / milestone.targetAmount) * 100), 100)
      : 0;

  const displayPct = milestone.status === MilestoneStatus.REACHED ? 100 : pct;

  return (
    <div
      className={`relative rounded-[9px] border p-[16px] mb-[10px] ${statusClasses(milestone.status)}`}
    >
      {/* Delete button */}
      <button
        type="button"
        onClick={() => onDelete(milestone.id)}
        className="absolute top-[10px] right-[10px] w-[22px] h-[22px] flex items-center justify-center rounded-[4px] text-[var(--color-muted)] hover:text-[var(--color-red)] hover:bg-[var(--color-red)]/8 transition-colors text-[11px]"
      >
        ✕
      </button>

      {/* Top row: emoji + title + target */}
      <div className="flex items-start gap-[10px] pr-[28px]">
        {/* Emoji picker */}
        <div className="relative" ref={pickerRef}>
          <button
            type="button"
            onClick={() => setPickerOpen((o) => !o)}
            className="text-[22px] leading-none cursor-pointer hover:opacity-80 transition-opacity"
          >
            {milestone.emoji || '🎯'}
          </button>
          {pickerOpen && (
            <div
              className="absolute top-[calc(100%+6px)] left-0 z-50 flex flex-wrap gap-[4px] w-[220px] rounded-xl border border-[var(--color-border)] p-[8px] shadow-lg"
              style={{ background: 'var(--color-bg-2)' }}
            >
              {EMOJIS.map((em) => (
                <button
                  key={em}
                  type="button"
                  onClick={() => handleEmojiSelect(em)}
                  className="p-[4px] rounded-[6px] text-[15px] hover:bg-[var(--color-bg-3)] cursor-pointer transition-colors"
                >
                  {em}
                </button>
              ))}
            </div>
          )}
        </div>

        {/* Title input */}
        <input
          type="text"
          value={title}
          onChange={(e) => handleTitleChange(e.target.value)}
          placeholder={t('editor.milestones.namePlaceholder')}
          className="flex-1 bg-transparent border-none outline-none text-[15px] font-bold text-[var(--color-text)] placeholder:text-[var(--color-muted)] focus:border-b border-[var(--color-border)]"
          style={{ fontFamily: 'var(--font-display)' }}
        />

        {/* Target amount + validation */}
        <div className="flex flex-col items-end gap-[2px]">
          <div className="flex items-center gap-[4px]">
            <span className="text-[12px] text-[var(--color-text-2)]">€</span>
            <input
              type="number"
              min={minAmount}
              max={maxAmount}
              value={targetAmount}
              onChange={(e) => handleTargetChange(e.target.value)}
              className={`w-[110px] text-right text-[13px] font-semibold px-[8px] py-[5px] rounded-[6px] outline-none border-[1.5px] transition-colors
                ${amountError
                  ? 'border-[var(--color-red)]/60 text-[var(--color-red)]'
                  : 'border-[var(--color-border)]/30 text-[var(--color-text)] focus:border-[var(--color-green)]/45'
                }`}
              style={{ background: 'var(--color-bg)' }}
              placeholder="0"
            />
          </div>
          {amountError && (
            <span className="text-[10px] text-[var(--color-red)] text-right max-w-[180px] leading-tight">
              {amountError}
            </span>
          )}
        </div>
      </div>

      {/* Description */}
      <textarea
        value={description}
        onChange={(e) => handleDescChange(e.target.value)}
        placeholder={t('editor.milestones.descPlaceholder')}
        rows={2}
        className="w-full mt-[10px] bg-transparent outline-none resize-none text-[12.5px] text-[var(--color-text-2)] placeholder:text-[var(--color-muted)] border-none focus:border-b border-[var(--color-border)]"
      />

      {/* Progress bar */}
      <div className="mt-[10px] h-[5px] rounded-[3px] overflow-hidden" style={{ background: 'var(--color-bg)' }}>
        <div
          className="h-full rounded-[3px] transition-all duration-500"
          style={{ width: `${displayPct}%`, background: barColor(milestone.status) }}
        />
      </div>

      {/* Badges row */}
      <div className="flex items-center gap-[8px] mt-[8px] flex-wrap">
        <StatusBadge status={milestone.status} t={t} />
        <span className="text-[11px] font-semibold text-[var(--color-green)]">
          {milestone.targetAmount.toLocaleString('fr-FR')} €
        </span>
        {milestone.reachedAt && (
          <span className="text-[11px] text-[var(--color-text-2)]">
            · {new Date(milestone.reachedAt).toLocaleDateString('fr-FR', { day: 'numeric', month: 'short', year: 'numeric' })}
          </span>
        )}
        <span className="text-[11px] text-[var(--color-muted)] ml-auto">{displayPct}%</span>
      </div>
    </div>
  );
}

/** Status badge for a milestone. */
function StatusBadge({
  status,
  t,
}: {
  status: MilestoneStatusType;
  t: ReturnType<typeof useTranslations<'dashboard.campaigns'>>;
}) {
  switch (status) {
    case MilestoneStatus.REACHED:
      return (
        <span className="text-[11px] font-semibold px-[7px] py-[2px] rounded-full bg-[var(--color-green)]/12 text-[var(--color-green)]">
          ✓ {t('editor.milestones.reached')}
        </span>
      );
    case MilestoneStatus.CURRENT:
      return (
        <span className="text-[11px] font-semibold px-[7px] py-[2px] rounded-full bg-[var(--color-yellow)]/12 text-[var(--color-yellow)]">
          ⏳ {t('editor.milestones.current')}
        </span>
      );
    default:
      return (
        <span className="text-[11px] font-semibold px-[7px] py-[2px] rounded-full bg-[var(--color-muted)]/20 text-[var(--color-muted)]">
          🔒 {t('editor.milestones.locked')}
        </span>
      );
  }
}
