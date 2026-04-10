'use client';

import { useEffect, useState } from 'react';
import { useTranslations } from 'next-intl';
import { useBudget } from '@/hooks/campaign/useBudget';
import { BudgetSide } from '@/types/campaign';
import type { CampaignDto } from '@/types/campaign';

interface CampaignBudgetTabProps {
  /** Campaign with its current budget sections. */
  campaign: CampaignDto;
  /** Called with the updated campaign DTO after a successful budget save. */
  onBudgetSaved: (campaign: CampaignDto) => void;
}

/** Formats a number as a French euro string. */
function fmtEur(n: number): string {
  return n.toLocaleString('fr-FR', { minimumFractionDigits: 0, maximumFractionDigits: 2 }) + ' €';
}

/** Inline form state for adding a new section. */
interface AddSectionFormState {
  side: typeof BudgetSide[keyof typeof BudgetSide];
  code: string;
  name: string;
}

/**
 * Budget prévisionnel tab of the campaign editor.
 *
 * Renders a two-column budget (charges / produits) with collapsible sections,
 * editable items, a balance bar, and a save button.
 */
export function CampaignBudgetTab({ campaign, onBudgetSaved }: CampaignBudgetTabProps) {
  const t = useTranslations('dashboard.campaigns');

  const {
    sections,
    collapsedSections,
    isDirty,
    isSaving,
    totalCharges,
    totalProduits,
    balance,
    init,
    initTemplate,
    updateItemLabel,
    updateItemAmount,
    addItem,
    removeItem,
    addSection,
    removeSection,
    toggleSection,
    save,
  } = useBudget();

  /* Inline "add section" form state (null = hidden) */
  const [addForm, setAddForm] = useState<AddSectionFormState | null>(null);

  /* Initialise from campaign on mount / campaign change */
  useEffect(() => {
    init(campaign.budgetSections);
  }, [campaign.budgetSections, init]);

  const isEmpty = sections.length === 0;

  /** Determine balance pill style. */
  const balancePill = () => {
    if (Math.abs(balance) < 1)
      return { label: `✓ ${t('editor.budget.equilibrium')}`, cls: 'text-[var(--color-green)] bg-[var(--color-green)]/10' };
    if (balance > 0)
      return { label: `↑ ${t('editor.budget.surplus')}`, cls: 'text-[var(--color-green)] bg-[var(--color-green)]/10' };
    return { label: `↓ ${t('editor.budget.deficit')}`, cls: 'text-[var(--color-red)] bg-[var(--color-red)]/10' };
  };
  const pill = balancePill();

  const handleSave = async () => {
    const updated = await save(campaign.id);
    if (updated) onBudgetSaved(updated);
  };

  const handleAddSection = (side: typeof BudgetSide[keyof typeof BudgetSide]) => {
    setAddForm({ side, code: '', name: '' });
  };

  const handleAddSectionConfirm = () => {
    if (!addForm || !addForm.name.trim()) return;
    addSection(addForm.side, addForm.code.trim() || '—', addForm.name.trim());
    setAddForm(null);
  };

  const chargeSections = sections
    .map((s, i) => ({ s, i }))
    .filter(({ s }) => s.side === BudgetSide.EXPENSE);

  const produitSections = sections
    .map((s, i) => ({ s, i }))
    .filter(({ s }) => s.side === BudgetSide.REVENUE);

  return (
    <div>
      {/* ── Balance bar ── */}
      <div
        className="flex items-center justify-between rounded-[14px] border border-[var(--color-border)] p-[14px_20px] mb-[20px] sticky top-[72px] z-50"
        style={{ background: 'var(--color-bg-3)', backdropFilter: 'blur(12px)' }}
      >
        <div className="text-center">
          <div className="text-[12px] text-[var(--color-text-2)] mb-[2px]">📉 {t('editor.budget.charges')}</div>
          <div
            className="text-[18px] font-bold text-[var(--color-red)]"
            style={{ fontFamily: 'var(--font-display)' }}
          >
            {fmtEur(totalCharges)}
          </div>
        </div>

        <div className="text-center">
          <div
            className="text-[14px] font-bold text-[var(--color-text)] mb-[4px]"
            style={{ fontFamily: 'var(--font-display)' }}
          >
            {fmtEur(balance)}
          </div>
          <span className={`text-[11px] font-semibold px-[8px] py-[2px] rounded-full ${pill.cls}`}>
            {pill.label}
          </span>
        </div>

        <div className="text-center">
          <div className="text-[12px] text-[var(--color-text-2)] mb-[2px]">📈 {t('editor.budget.produits')}</div>
          <div
            className="text-[18px] font-bold text-[var(--color-green)]"
            style={{ fontFamily: 'var(--font-display)' }}
          >
            {fmtEur(totalProduits)}
          </div>
        </div>
      </div>

      {/* ── Empty state ── */}
      {isEmpty && (
        <div className="text-center py-[32px]">
          <button
            type="button"
            onClick={initTemplate}
            className="inline-flex flex-col items-center gap-[8px] border border-dashed border-[var(--color-green)]/40 rounded-[14px] px-[32px] py-[20px] cursor-pointer hover:bg-[var(--color-green)]/5 transition-colors"
          >
            <span className="text-[24px]">📋</span>
            <span className="text-[13px] font-semibold text-[var(--color-green)]">
              {t('editor.budget.initTemplate')}
            </span>
            <span className="text-[11px] text-[var(--color-text-2)] max-w-[260px]">
              {t('editor.budget.initTemplateHint')}
            </span>
          </button>
        </div>
      )}

      {/* ── Two-column grid ── */}
      {!isEmpty && (
        <div className="grid grid-cols-2 gap-[18px]">
          {/* Charges column */}
          <div>
            <div className="flex items-center gap-[6px] text-[12px] font-semibold text-[var(--color-red)] mb-[12px]">
              <span>📉</span>
              <span>{t('editor.budget.charges')}</span>
            </div>

            {chargeSections.map(({ s, i }) => (
              <BudgetSection
                key={i}
                section={s}
                sIdx={i}
                collapsed={collapsedSections.has(i)}
                onToggle={toggleSection}
                onUpdateLabel={updateItemLabel}
                onUpdateAmount={updateItemAmount}
                onAddItem={addItem}
                onRemoveItem={removeItem}
                onRemoveSection={removeSection}
                addItemLabel={t('editor.budget.addItem')}
                labelPlaceholder={t('editor.budget.labelPlaceholder')}
                amountPlaceholder={t('editor.budget.amountPlaceholder')}
              />
            ))}

            <AddSectionButton
              side={BudgetSide.EXPENSE}
              label={t('editor.budget.addSection')}
              addForm={addForm}
              onOpen={handleAddSection}
              onConfirm={handleAddSectionConfirm}
              onCancel={() => setAddForm(null)}
              onChangeCode={(v) => setAddForm((f) => f ? { ...f, code: v } : f)}
              onChangeName={(v) => setAddForm((f) => f ? { ...f, name: v } : f)}
              codePlaceholder={t('editor.budget.sectionCode')}
              namePlaceholder={t('editor.budget.sectionName')}
            />
          </div>

          {/* Produits column */}
          <div>
            <div className="flex items-center gap-[6px] text-[12px] font-semibold text-[var(--color-green)] mb-[12px]">
              <span>📈</span>
              <span>{t('editor.budget.produits')}</span>
            </div>

            {produitSections.map(({ s, i }) => (
              <BudgetSection
                key={i}
                section={s}
                sIdx={i}
                collapsed={collapsedSections.has(i)}
                onToggle={toggleSection}
                onUpdateLabel={updateItemLabel}
                onUpdateAmount={updateItemAmount}
                onAddItem={addItem}
                onRemoveItem={removeItem}
                onRemoveSection={removeSection}
                addItemLabel={t('editor.budget.addItem')}
                labelPlaceholder={t('editor.budget.labelPlaceholder')}
                amountPlaceholder={t('editor.budget.amountPlaceholder')}
              />
            ))}

            <AddSectionButton
              side={BudgetSide.REVENUE}
              label={t('editor.budget.addSection')}
              addForm={addForm}
              onOpen={handleAddSection}
              onConfirm={handleAddSectionConfirm}
              onCancel={() => setAddForm(null)}
              onChangeCode={(v) => setAddForm((f) => f ? { ...f, code: v } : f)}
              onChangeName={(v) => setAddForm((f) => f ? { ...f, name: v } : f)}
              codePlaceholder={t('editor.budget.sectionCode')}
              namePlaceholder={t('editor.budget.sectionName')}
            />
          </div>
        </div>
      )}

      {/* ── Save button ── */}
      {!isEmpty && (
        <div className="flex justify-end mt-[20px]">
          <button
            type="button"
            onClick={handleSave}
            disabled={!isDirty || isSaving}
            className="px-[20px] py-[10px] rounded-[9px] text-[13px] font-semibold transition-all
              bg-[var(--color-green)] text-[var(--color-bg)] cursor-pointer
              hover:opacity-90 disabled:opacity-40 disabled:cursor-not-allowed"
          >
            {isSaving ? '…' : t('editor.budget.save')}
          </button>
        </div>
      )}
    </div>
  );
}

/* ─── Sub-components ─── */

interface BudgetSectionProps {
  section: { code: string; name: string; items: { label: string; amount: number }[] };
  sIdx: number;
  collapsed: boolean;
  onToggle: (sIdx: number) => void;
  onUpdateLabel: (sIdx: number, iIdx: number, v: string) => void;
  onUpdateAmount: (sIdx: number, iIdx: number, v: number) => void;
  onAddItem: (sIdx: number) => void;
  onRemoveItem: (sIdx: number, iIdx: number) => void;
  onRemoveSection: (sIdx: number) => void;
  addItemLabel: string;
  labelPlaceholder: string;
  amountPlaceholder: string;
}

/** Collapsible budget section with items. */
function BudgetSection({
  section,
  sIdx,
  collapsed,
  onToggle,
  onUpdateLabel,
  onUpdateAmount,
  onAddItem,
  onRemoveItem,
  onRemoveSection,
  addItemLabel,
  labelPlaceholder,
  amountPlaceholder,
}: BudgetSectionProps) {
  const sectionTotal = section.items.reduce((s, it) => s + (it.amount || 0), 0);

  return (
    <div
      className="rounded-[9px] border border-[var(--color-border)]/35 mb-[8px] overflow-hidden"
      style={{ background: 'var(--color-bg-3)' }}
    >
      {/* Section header */}
      <button
        type="button"
        onClick={() => onToggle(sIdx)}
        className="w-full flex items-center justify-between px-[12px] py-[9px] cursor-pointer hover:bg-[var(--color-bg)]/20 transition-colors"
      >
        <div className="flex items-center gap-[7px] text-[12.5px] font-semibold text-[var(--color-text)]">
          <span
            className="text-[10px] font-bold px-[6px] py-[2px] rounded-[4px]"
            style={{ fontFamily: 'var(--font-display)' }}
          >
            {section.code}
          </span>
          {section.name}
        </div>
        <div className="flex items-center gap-[8px]">
          <span
            className="text-[12px] font-bold text-[var(--color-text-2)]"
            style={{ fontFamily: 'var(--font-display)' }}
          >
            {sectionTotal.toLocaleString('fr-FR')} €
          </span>
          <span
            className="text-[10px] text-[var(--color-muted)] transition-transform duration-200"
            style={{ transform: collapsed ? 'rotate(-90deg)' : 'rotate(0deg)' }}
          >
            ▾
          </span>
        </div>
      </button>

      {/* Section body */}
      {!collapsed && (
        <div className="px-[12px] pb-[10px]">
          {section.items.map((item, iIdx) => (
            <div key={iIdx} className="flex items-center gap-[6px] py-[4px]">
              <input
                type="text"
                value={item.label}
                onChange={(e) => onUpdateLabel(sIdx, iIdx, e.target.value)}
                placeholder={labelPlaceholder}
                className="flex-1 bg-transparent text-[12.5px] text-[var(--color-text)] outline-none placeholder:text-[var(--color-muted)] border-b border-transparent focus:border-[var(--color-border)]"
              />
              <input
                type="number"
                min={0}
                step={50}
                value={item.amount || ''}
                onChange={(e) => onUpdateAmount(sIdx, iIdx, parseFloat(e.target.value) || 0)}
                placeholder={amountPlaceholder}
                className="w-[100px] text-right text-[13px] text-[var(--color-text)] px-[10px] py-[6px] rounded-[6px] outline-none border-[1.5px] border-[var(--color-border)]/30 focus:border-[var(--color-green)]/45 transition-colors"
                style={{ background: 'var(--color-bg)' }}
              />
              <button
                type="button"
                onClick={() => onRemoveItem(sIdx, iIdx)}
                className="w-[24px] h-[24px] flex items-center justify-center rounded-[5px] text-[var(--color-muted)] hover:text-[var(--color-red)] hover:bg-[var(--color-red)]/8 transition-colors text-[12px]"
              >
                ✕
              </button>
            </div>
          ))}

          <div className="flex justify-between items-center mt-[6px]">
            <button
              type="button"
              onClick={() => onAddItem(sIdx)}
              className="text-[11.5px] text-[var(--color-cyan)] cursor-pointer hover:underline"
            >
              + {addItemLabel}
            </button>
            <button
              type="button"
              onClick={() => onRemoveSection(sIdx)}
              className="text-[11px] text-[var(--color-muted)] hover:text-[var(--color-red)] cursor-pointer"
            >
              ✕ section
            </button>
          </div>
        </div>
      )}
    </div>
  );
}

interface AddSectionButtonProps {
  side: typeof BudgetSide[keyof typeof BudgetSide];
  label: string;
  addForm: AddSectionFormState | null;
  onOpen: (side: typeof BudgetSide[keyof typeof BudgetSide]) => void;
  onConfirm: () => void;
  onCancel: () => void;
  onChangeCode: (v: string) => void;
  onChangeName: (v: string) => void;
  codePlaceholder: string;
  namePlaceholder: string;
}

/** Dashed button that expands to an inline add-section form. */
function AddSectionButton({
  side,
  label,
  addForm,
  onOpen,
  onConfirm,
  onCancel,
  onChangeCode,
  onChangeName,
  codePlaceholder,
  namePlaceholder,
}: AddSectionButtonProps) {
  const isOpen = addForm?.side === side;

  if (!isOpen) {
    return (
      <button
        type="button"
        onClick={() => onOpen(side)}
        className="w-full mt-[4px] py-[8px] border border-dashed border-[var(--color-border)] rounded-[9px] text-[12px] text-[var(--color-text-2)] hover:text-[var(--color-green)] hover:border-[var(--color-green)]/40 transition-colors cursor-pointer"
      >
        + {label}
      </button>
    );
  }

  return (
    <div
      className="mt-[4px] rounded-[9px] border border-[var(--color-border)] p-[10px] flex flex-col gap-[6px]"
      style={{ background: 'var(--color-bg-3)' }}
    >
      <div className="flex gap-[6px]">
        <input
          type="text"
          value={addForm?.code ?? ''}
          onChange={(e) => onChangeCode(e.target.value)}
          placeholder={codePlaceholder}
          className="w-[80px] bg-[var(--color-bg)] border border-[var(--color-border)] rounded-[6px] px-[8px] py-[5px] text-[12px] text-[var(--color-text)] outline-none focus:border-[var(--color-green)]/45"
        />
        <input
          type="text"
          value={addForm?.name ?? ''}
          onChange={(e) => onChangeName(e.target.value)}
          placeholder={namePlaceholder}
          className="flex-1 bg-[var(--color-bg)] border border-[var(--color-border)] rounded-[6px] px-[8px] py-[5px] text-[12px] text-[var(--color-text)] outline-none focus:border-[var(--color-green)]/45"
          onKeyDown={(e) => { if (e.key === 'Enter') onConfirm(); if (e.key === 'Escape') onCancel(); }}
          autoFocus
        />
      </div>
      <div className="flex gap-[6px] justify-end">
        <button type="button" onClick={onCancel} className="text-[11px] text-[var(--color-muted)] hover:text-[var(--color-text)] px-[8px] py-[4px] cursor-pointer">
          Annuler
        </button>
        <button
          type="button"
          onClick={onConfirm}
          className="text-[11px] font-semibold bg-[var(--color-green)] text-[var(--color-bg)] px-[10px] py-[4px] rounded-[5px] cursor-pointer hover:opacity-90"
        >
          Ajouter
        </button>
      </div>
    </div>
  );
}
