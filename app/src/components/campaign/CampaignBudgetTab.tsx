'use client';

import { useEffect, useState } from 'react';
import { useTranslations } from 'next-intl';
import { useBudget, type BudgetTemplateType } from '@/hooks/campaign/useBudget';
import { BudgetSide } from '@/types/campaign';
import type { CampaignDto } from '@/types/campaign';

interface CampaignBudgetTabProps {
  campaign: CampaignDto;
  onBudgetSaved: (campaign: CampaignDto) => void;
}

function fmtEur(n: number): string {
  return n.toLocaleString('fr-FR', { minimumFractionDigits: 0, maximumFractionDigits: 2 }) + ' €';
}

interface AddSectionFormState {
  side: typeof BudgetSide[keyof typeof BudgetSide];
  code: string;
  name: string;
}

type ColFilter = 'both' | 'expense' | 'revenue';

const TEMPLATES_META: { type: BudgetTemplateType; emoji: string; nameKey: string; descKey: string }[] = [
  { type: 'standard', emoji: '📋', nameKey: 'tplStandard', descKey: 'tplStandardDesc' },
  { type: 'simple',   emoji: '📝', nameKey: 'tplSimple',   descKey: 'tplSimpleDesc'   },
  { type: 'blank',    emoji: '📄', nameKey: 'tplBlank',    descKey: 'tplBlankDesc'    },
];

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

  const [addForm, setAddForm] = useState<AddSectionFormState | null>(null);
  const [selectedTpl, setSelectedTpl] = useState<BudgetTemplateType>('standard');
  const [colFilter, setColFilter] = useState<ColFilter>('both');

  useEffect(() => {
    init(campaign.budgetSections);
  }, [campaign.budgetSections, init]);

  const isEmpty = sections.length === 0;

  const balancePill = (): { label: string; cls: string } => {
    if (Math.abs(balance) < 1) return { label: `✓ ${t('editor.budget.equilibrium')}`, cls: 'ok' };
    if (balance > 0)           return { label: `↑ ${t('editor.budget.surplus')}`,     cls: 'excess' };
    return                            { label: `↓ ${t('editor.budget.deficit')}`,      cls: 'deficit' };
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

  const handleResetBudget = () => {
    init([]);
    setColFilter('both');
  };

  const chargeSections = sections.map((s, i) => ({ s, i })).filter(({ s }) => s.side === BudgetSide.EXPENSE);
  const produitSections = sections.map((s, i) => ({ s, i })).filter(({ s }) => s.side === BudgetSide.REVENUE);

  return (
    <div>
      {/* ── Empty state ── */}
      {isEmpty && (
        <div id="bud-empty-state">
          <div className="cm-card">
            <div className="empty-state">
              <div className="empty-state-icon">📊</div>
              <div className="empty-state-title">{t('editor.budget.emptyTitle')}</div>
              <div className="empty-state-desc">{t('editor.budget.emptyDesc')}</div>
              <div className="empty-tpl-grid">
                {TEMPLATES_META.map(({ type, emoji, nameKey, descKey }) => (
                  <div
                    key={type}
                    className={`empty-tpl-card${selectedTpl === type ? ' selected' : ''}`}
                    onClick={() => setSelectedTpl(type)}
                  >
                    <div className="empty-tpl-emoji">{emoji}</div>
                    <div className="empty-tpl-name">{t(`editor.budget.${nameKey}` as Parameters<typeof t>[0])}</div>
                    <div className="empty-tpl-desc">{t(`editor.budget.${descKey}` as Parameters<typeof t>[0])}</div>
                  </div>
                ))}
              </div>
              <button className="btn btn-primary" onClick={() => initTemplate(selectedTpl)}>
                {t('editor.budget.useTpl')}
              </button>
              <div style={{ marginTop: '10px', fontSize: '11.5px', color: 'var(--slate-lavender)' }}>
                {t('editor.budget.initTemplateHint')}
              </div>
            </div>
          </div>
        </div>
      )}

      {/* ── Budget editor ── */}
      {!isEmpty && (
        <div id="bud-step-editor">
          {/* Toolbar */}
          <div className="bud-toolbar">
            <div className="col-filter">
              <button
                className={`col-filter-btn${colFilter === 'both' ? ' active' : ''}`}
                onClick={() => setColFilter('both')}
              >
                {t('editor.budget.colBoth')}
              </button>
              <button
                className={`col-filter-btn${colFilter === 'expense' ? ' active ch' : ' ch'}`}
                onClick={() => setColFilter('expense')}
              >
                📉 {t('editor.budget.charges')}
              </button>
              <button
                className={`col-filter-btn${colFilter === 'revenue' ? ' active pr' : ' pr'}`}
                onClick={() => setColFilter('revenue')}
              >
                📈 {t('editor.budget.produits')}
              </button>
            </div>
            <button className="cm-btn cm-btn-ghost cm-btn-sm" onClick={handleResetBudget}>
              {t('editor.budget.changeTemplate')}
            </button>
          </div>

          {/* Balance bar */}
          <div className="balance-bar">
            <div className="bal-side">
              <div className="bal-label">📉 {t('editor.budget.charges')}</div>
              <div className="bal-val ch">{fmtEur(totalCharges)}</div>
            </div>
            <div className="bal-center">
              <div style={{ fontFamily: "'Syne',sans-serif", fontSize: '14px', fontWeight: 700, color: 'var(--ink-navy)' }}>
                {fmtEur(balance)}
              </div>
              <div className={`bal-pill ${pill.cls}`}>{pill.label}</div>
            </div>
            <div className="bal-side">
              <div className="bal-label">📈 {t('editor.budget.produits')}</div>
              <div className="bal-val pr">{fmtEur(totalProduits)}</div>
            </div>
          </div>

          {/* Two-column grid */}
          <div className="budget-cols" id="bud-cols">
            {/* Charges column */}
            {colFilter !== 'revenue' && (
              <div id="bud-col-charges">
                <div className="col-head">
                  <span style={{ color: 'var(--warm-coral)' }}>📉</span>
                  {t('editor.budget.chargesHead')}
                </div>
                <div id="bud-charges">
                  {chargeSections.map(({ s, i }) => (
                    <BudgetSection
                      key={i}
                      section={s}
                      sIdx={i}
                      side="ch"
                      isOpen={!collapsedSections.has(i)}
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
                </div>
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
            )}

            {/* Produits column */}
            {colFilter !== 'expense' && (
              <div id="bud-col-produits">
                <div className="col-head">
                  <span style={{ color: 'var(--teal-dark)' }}>📈</span>
                  {t('editor.budget.produitsHead')}
                </div>
                <div id="bud-produits">
                  {produitSections.map(({ s, i }) => (
                    <BudgetSection
                      key={i}
                      section={s}
                      sIdx={i}
                      side="pr"
                      isOpen={!collapsedSections.has(i)}
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
                </div>
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
            )}
          </div>

          {/* Save */}
          <div className="flex justify-end" style={{ marginTop: '20px' }}>
            <button
              type="button"
              onClick={handleSave}
              disabled={!isDirty || isSaving}
              className="cm-btn cm-btn-primary cm-btn-sm"
            >
              {isSaving ? '…' : t('editor.budget.save')}
            </button>
          </div>
        </div>
      )}
    </div>
  );
}

/* ─── BudgetSection ─── */

interface BudgetSectionProps {
  section: { code: string; name: string; items: { label: string; amount: number }[] };
  sIdx: number;
  side: 'ch' | 'pr';
  isOpen: boolean;
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

function BudgetSection({
  section, sIdx, side, isOpen,
  onToggle, onUpdateLabel, onUpdateAmount, onAddItem, onRemoveItem, onRemoveSection,
  addItemLabel, labelPlaceholder, amountPlaceholder,
}: BudgetSectionProps) {
  const [pendingDelItem, setPendingDelItem] = useState<number | null>(null);
  const [pendingDelSection, setPendingDelSection] = useState(false);
  const sectionTotal = section.items.reduce((s, it) => s + (it.amount || 0), 0);

  return (
    <div className={`bsec${isOpen ? ' open' : ''}`}>
      <div className="bsec-h" onClick={() => { if (!pendingDelSection) onToggle(sIdx); }}>
        <div style={{ display: 'flex', alignItems: 'center', gap: '7px', fontSize: '12.5px', fontWeight: 600, color: 'var(--ink-navy)' }}>
          <span className={`bsec-code ${side}`}>{section.code}</span>
          {section.name}
        </div>
        <div style={{ display: 'flex', alignItems: 'center', gap: '6px' }}>
          <span className="bsec-tot">{sectionTotal.toLocaleString('fr-FR')} €</span>
          <div className={`bsec-del-confirm${pendingDelSection ? ' show' : ''}`}>
            <button
              className="line-del-ok"
              onClick={(e) => { e.stopPropagation(); onRemoveSection(sIdx); }}
            >✓</button>
            <button
              className="line-del-cancel"
              onClick={(e) => { e.stopPropagation(); setPendingDelSection(false); }}
            >✕</button>
          </div>
          {!pendingDelSection && (
            <button
              className="bsec-del"
              onClick={(e) => { e.stopPropagation(); setPendingDelSection(true); }}
            >✕</button>
          )}
          <span className="bsec-chev">▾</span>
        </div>
      </div>

      <div className="bsec-body">
        {section.items.map((item, iIdx) => (
          <div key={iIdx} className="line">
            <div className="line-lbl">
              <input
                type="text"
                value={item.label}
                onChange={(e) => { setPendingDelItem(null); onUpdateLabel(sIdx, iIdx, e.target.value); }}
                placeholder={labelPlaceholder}
              />
            </div>
            <div className="line-amt">
              <input
                type="number"
                min={0}
                step={50}
                value={item.amount || ''}
                onChange={(e) => onUpdateAmount(sIdx, iIdx, parseFloat(e.target.value) || 0)}
                placeholder={amountPlaceholder}
              />
            </div>
            <div className={`line-del-confirm${pendingDelItem === iIdx ? ' show' : ''}`}>
              <button
                className="line-del-ok"
                onClick={() => { onRemoveItem(sIdx, iIdx); setPendingDelItem(null); }}
              >✓</button>
              <button
                className="line-del-cancel"
                onClick={() => setPendingDelItem(null)}
              >✕</button>
            </div>
            {pendingDelItem !== iIdx && (
              <button className="line-del" onClick={() => setPendingDelItem(iIdx)}>✕</button>
            )}
          </div>
        ))}
        <button className="add-line" onClick={() => onAddItem(sIdx)}>
          + {addItemLabel}
        </button>
      </div>
    </div>
  );
}

/* ─── AddSectionButton ─── */

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

function AddSectionButton({
  side, label, addForm, onOpen, onConfirm, onCancel, onChangeCode, onChangeName,
  codePlaceholder, namePlaceholder,
}: AddSectionButtonProps) {
  const t = useTranslations('dashboard.campaigns');
  const isOpen = addForm?.side === side;

  if (!isOpen) {
    return (
      <button type="button" className="add-sec" onClick={() => onOpen(side)}>
        + {label}
      </button>
    );
  }

  return (
    <div className="bsec" style={{ padding: '10px', marginTop: '4px' }}>
      <div style={{ display: 'flex', gap: '6px', marginBottom: '6px' }}>
        <input
          type="text"
          value={addForm?.code ?? ''}
          onChange={(e) => onChangeCode(e.target.value)}
          placeholder={codePlaceholder}
          className="cm-fi"
          style={{ width: '80px' }}
        />
        <input
          type="text"
          value={addForm?.name ?? ''}
          onChange={(e) => onChangeName(e.target.value)}
          placeholder={namePlaceholder}
          className="cm-fi"
          style={{ flex: 1 }}
          onKeyDown={(e) => { if (e.key === 'Enter') onConfirm(); if (e.key === 'Escape') onCancel(); }}
          autoFocus
        />
      </div>
      <div style={{ display: 'flex', gap: '6px', justifyContent: 'flex-end' }}>
        <button type="button" className="cm-btn cm-btn-ghost cm-btn-sm" onClick={onCancel}>
          {t('editor.budget.cancel')}
        </button>
        <button type="button" className="cm-btn cm-btn-primary cm-btn-sm" onClick={onConfirm}>
          {t('editor.budget.confirm')}
        </button>
      </div>
    </div>
  );
}
