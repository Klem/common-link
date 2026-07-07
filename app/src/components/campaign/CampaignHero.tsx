'use client';

import { Fragment, useState, useEffect, useRef } from 'react';
import { useTranslations } from 'next-intl';
import type { CampaignDto, CampaignStatus } from '@/types/campaign';

const EMOJIS = [
  '🏕','🏙','🌍','🎗','🎀','🎁','🎓','🏥','🌱','🌊','🏡','🎭','🎪','🏛','⚡','🏃',
  '🌺','🏄','🌿🍃','🏔','🎯','🦋','🦄🌈','🎵','🎤','🌸🌼','🏇','🌞','🎠','🌻','🌈','🎡🎢','🦅',
];

function statusPill(status: CampaignStatus): { cls: string; glyph: string; labelKey: string } {
  switch (status) {
    case 'LIVE':    return { cls: 'camp-status-pill live',    glyph: '●',  labelKey: 'status.live' };
    case 'PRIVATE': return { cls: 'camp-status-pill private', glyph: '🔗', labelKey: 'status.private' };
    case 'ENDED':   return { cls: 'camp-status-pill ended',   glyph: '●',  labelKey: 'status.ended' };
    default:        return { cls: 'camp-status-pill draft',   glyph: '●',  labelKey: 'status.draft' };
  }
}

function formatDateRange(startDate: string | null, endDate: string | null): string {
  const fmt = (d: string) =>
    new Date(d).toLocaleDateString('fr-FR', { day: 'numeric', month: 'short', year: 'numeric' });
  if (startDate && endDate) return `${fmt(startDate)} → ${fmt(endDate)}`;
  if (startDate) return `À partir du ${fmt(startDate)}`;
  if (endDate) return `Jusqu'au ${fmt(endDate)}`;
  return '';
}

function budgetTotals(campaign: CampaignDto): { charges: number; produits: number } {
  let charges = 0;
  let produits = 0;
  for (const sec of campaign.budgetSections) {
    const total = sec.items.reduce((s, it) => s + (it.amount || 0), 0);
    if (sec.side === 'EXPENSE') charges += total;
    else produits += total;
  }
  return { charges, produits };
}

interface CompStep {
  key: string;
  labelKey: string;
  cls: 'done' | 'missing' | 'warn' | 'boost';
  tab: string;
  fieldId?: string;
}

function buildCompletionSteps(campaign: CampaignDto): CompStep[] {
  const required: CompStep[] = [
    {
      key: 'name',
      labelKey: 'editor.completion.name',
      cls: campaign.name.trim().length > 0 ? 'done' : 'missing',
      tab: 'info',
      fieldId: 'info-name',
    },
    {
      key: 'description',
      labelKey: 'editor.completion.description',
      cls: (campaign.description ?? '').length >= 10 ? 'done' : 'missing',
      tab: 'info',
      fieldId: 'info-desc',
    },
    {
      key: 'dates',
      labelKey: 'editor.completion.dates',
      cls: (campaign.startDate && campaign.endDate) ? 'done' : 'missing',
      tab: 'info',
      fieldId: 'info-start',
    },
    {
      key: 'goal',
      labelKey: 'editor.completion.goal',
      cls: campaign.goal > 0 ? 'done' : 'missing',
      tab: 'info',
      fieldId: 'info-goal',
    },
  ];

  const { charges, produits } = budgetTotals(campaign);
  const budgetHasData = charges > 0 || produits > 0;
  const budgetBalanced = budgetHasData && Math.abs(produits - charges) < 1;

  const msAllComplete =
    campaign.milestones.length >= 1 &&
    campaign.milestones.every((m) => m.title && m.targetAmount > 0);
  const msSomeIncomplete =
    campaign.milestones.length >= 1 && !msAllComplete;

  const boosters: CompStep[] = [
    {
      key: 'budget',
      labelKey: 'editor.completion.budget',
      cls: budgetBalanced ? 'done' : budgetHasData ? 'warn' : 'boost',
      tab: 'budget',
    },
    {
      key: 'milestones',
      labelKey: 'editor.completion.milestones',
      cls: msAllComplete ? 'done' : msSomeIncomplete ? 'warn' : 'boost',
      tab: 'milestones',
    },
    // reason + impact: Step 6 fields not yet in DTO → always boost
    { key: 'reason', labelKey: 'editor.completion.reason', cls: 'boost', tab: 'info', fieldId: 'info-reason' },
    { key: 'impact', labelKey: 'editor.completion.impact', cls: 'boost', tab: 'info', fieldId: 'info-impact-goals' },
  ];

  return [...required, ...boosters];
}

function stepIcon(cls: CompStep['cls']) {
  if (cls === 'done') return '✓';
  if (cls === 'missing') return '○';
  if (cls === 'warn') return '⚠';
  return '★';
}

interface CampaignHeroProps {
  campaign: CampaignDto;
  onNameChange: (name: string) => void;
  onEmojiChange: (emoji: string) => void;
  onTabChange: (tab: string) => void;
}

export function CampaignHero({ campaign, onNameChange, onEmojiChange, onTabChange }: CampaignHeroProps) {
  const t = useTranslations('dashboard.campaigns');
  const [pickerOpen, setPickerOpen] = useState(false);
  const pickerRef = useRef<HTMLDivElement>(null);

  const percentage = campaign.goal > 0 ? Math.min((campaign.raised / campaign.goal) * 100, 100) : 0;
  const { cls: pillCls, glyph: pillGlyph, labelKey: pillLabelKey } = statusPill(campaign.status);
  const dateRange = formatDateRange(campaign.startDate, campaign.endDate);
  const steps = buildCompletionSteps(campaign);

  useEffect(() => {
    function handleClickOutside(e: MouseEvent) {
      if (pickerRef.current && !pickerRef.current.contains(e.target as Node)) {
        setPickerOpen(false);
      }
    }
    if (pickerOpen) document.addEventListener('mousedown', handleClickOutside);
    return () => document.removeEventListener('mousedown', handleClickOutside);
  }, [pickerOpen]);

  function jumpToField(tab: string, fieldId?: string) {
    onTabChange(tab);
    if (fieldId) {
      requestAnimationFrame(() => {
        const el = document.getElementById(fieldId);
        if (!el) return;
        el.scrollIntoView({ behavior: 'smooth', block: 'center' });
        el.focus();
        el.style.animation = 'fieldFlash .8s ease forwards';
        setTimeout(() => { el.style.animation = ''; }, 900);
      });
    }
  }

  return (
    <div className="camp-hero">
      <div className="camp-hero-top">
        {/* Emoji picker */}
        <div className="camp-emoji" ref={pickerRef} onClick={() => setPickerOpen((o) => !o)}>
          {campaign.emoji || '🏕'}
          {pickerOpen && (
            <div className="ms-emoji-pick">
              {EMOJIS.map((em) => (
                <button
                  key={em}
                  type="button"
                  onClick={(e) => {
                    e.stopPropagation();
                    onEmojiChange(em);
                    setPickerOpen(false);
                  }}
                >
                  {em}
                </button>
              ))}
            </div>
          )}
        </div>

        {/* Name + meta */}
        <div className="camp-info">
          <input
            id="info-name"
            className="camp-name-edit"
            type="text"
            value={campaign.name}
            onChange={(e) => onNameChange(e.target.value)}
            placeholder={t('editor.info.name.placeholder')}
          />
          <div style={{ display: 'flex', alignItems: 'center', gap: '10px', marginTop: '6px', fontSize: '12px', color: 'var(--slate-lavender)', flexWrap: 'wrap' }}>
            {dateRange && <span>{dateRange}</span>}
            {dateRange && <span>·</span>}
            <span>
              {t('editor.hero.goal')} :{' '}
              <strong style={{ color: 'var(--teal-dark)' }}>
                {campaign.goal.toLocaleString('fr-FR')} €
              </strong>
            </span>
          </div>
        </div>

        {/* Status pill */}
        <span className={pillCls}>{pillGlyph} {t(pillLabelKey)}</span>
      </div>

      {/* Collecté / barre de progression */}
      <div style={{ marginTop: '4px' }}>
        <div style={{ display: 'flex', justifyContent: 'space-between', fontSize: '12px', marginBottom: '4px' }}>
          <span style={{ color: 'var(--slate-lavender)' }}>{t('editor.hero.collected')}</span>
          <span>
            <strong style={{ color: 'var(--teal-dark)' }}>{campaign.raised.toLocaleString('fr-FR')} €</strong>
            <span style={{ color: 'var(--slate-lavender)' }}> / {campaign.goal.toLocaleString('fr-FR')} €</span>
          </span>
        </div>
        <div className="camp-progress-bar">
          <div className="camp-progress-fill" style={{ width: `${percentage}%` }} />
        </div>
      </div>

      {/* Completion pills */}
      <div className="completion-bar-wrap">
        <div className="completion-steps">
          {steps.map((step, i) => (
            <Fragment key={step.key}>
              {i === 4 && (
                <span style={{ color: 'var(--slate-lavender)', fontSize: '11px', opacity: 0.5, padding: '0 4px', alignSelf: 'center' }}>·</span>
              )}
              <button
                type="button"
                className={`comp-step ${step.cls}`}
                onClick={() => jumpToField(step.tab, step.fieldId)}
              >
                <span className="comp-step-dot" />
                {stepIcon(step.cls)} {t(step.labelKey)}
              </button>
            </Fragment>
          ))}
        </div>
      </div>
    </div>
  );
}
