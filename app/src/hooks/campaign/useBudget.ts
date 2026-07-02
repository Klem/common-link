'use client';

import { useState, useCallback, useMemo } from 'react';
import { saveBudget } from '@/lib/api/campaign';
import { useToastStore } from '@/stores/toastStore';
import { BudgetSide } from '@/types/campaign';
import type { BudgetSectionDto, CampaignDto, SaveBudgetRequest } from '@/types/campaign';

/** A single editable budget line item. */
export interface EditableItem {
  label: string;
  amount: number;
}

/** An editable budget section with its line items. */
export interface EditableBudgetSection {
  side: typeof BudgetSide[keyof typeof BudgetSide];
  code: string;
  name: string;
  items: EditableItem[];
}

export type BudgetTemplateType = 'standard' | 'simple';

/** Reserved name for the single, non-deletable revenue category on new campaigns. */
export const FIXED_REVENUE_SECTION_NAME = 'Produit';
/** Reserved label for the single, non-deletable revenue item on new campaigns. */
export const FIXED_REVENUE_ITEM_LABEL = 'Dons';

/** Return type of {@link useBudget}. */
export interface UseBudgetReturn {
  /** Current editable sections. */
  sections: EditableBudgetSection[];
  /** Indices of collapsed sections. */
  collapsedSections: Set<number>;
  /** True if any local change has not yet been saved. */
  isDirty: boolean;
  /** True while the save API call is in-flight. */
  isSaving: boolean;
  /** Total of all EXPENSE item amounts. */
  totalCharges: number;
  /** Total of all REVENUE item amounts. */
  totalProduits: number;
  /** Balance = totalProduits - totalCharges. */
  balance: number;
  /** Initialises local state from a campaign's budget sections. */
  init: (budgetSections: BudgetSectionDto[]) => void;
  /** Pre-fills with the chosen template (amounts at 0). Defaults to 'standard'. */
  initTemplate: (type?: BudgetTemplateType) => void;
  /** Updates the label of an item. */
  updateItemLabel: (sIdx: number, iIdx: number, label: string) => void;
  /** Updates the amount of an item. */
  updateItemAmount: (sIdx: number, iIdx: number, amount: number) => void;
  /** Adds an empty item to a section. */
  addItem: (sIdx: number) => void;
  /** Removes an item from a section. */
  removeItem: (sIdx: number, iIdx: number) => void;
  /** Adds a new section. */
  addSection: (side: typeof BudgetSide[keyof typeof BudgetSide], code: string, name: string) => void;
  /** Removes a section. */
  removeSection: (sIdx: number) => void;
  /** Toggles a section's collapsed state. */
  toggleSection: (sIdx: number) => void;
  /** Saves the budget to the API. `silent` suppresses the success toast (used for autosave). */
  save: (campaignId: string, silent?: boolean) => Promise<CampaignDto | null>;
}

/** French association accounting plan template. */
const ACCOUNTING_TEMPLATE: EditableBudgetSection[] = [
  {
    side: BudgetSide.EXPENSE,
    code: '60',
    name: 'Achats',
    items: [
      { label: 'Prestations de services', amount: 0 },
      { label: 'Matières premières', amount: 0 },
      { label: 'Fournitures', amount: 0 },
      { label: 'Eau / gaz / électricité', amount: 0 },
    ],
  },
  {
    side: BudgetSide.EXPENSE,
    code: '61',
    name: 'Services extérieurs',
    items: [
      { label: 'Sous-traitance', amount: 0 },
      { label: 'Locations', amount: 0 },
      { label: 'Entretien / réparations', amount: 0 },
      { label: 'Assurance', amount: 0 },
    ],
  },
  {
    side: BudgetSide.EXPENSE,
    code: '62',
    name: 'Autres services ext.',
    items: [
      { label: 'Honoraires', amount: 0 },
      { label: 'Communication', amount: 0 },
      { label: 'Transports', amount: 0 },
      { label: 'Restauration', amount: 0 },
      { label: 'Télécoms', amount: 0 },
      { label: 'Services bancaires', amount: 0 },
    ],
  },
  {
    side: BudgetSide.EXPENSE,
    code: '64',
    name: 'Personnel',
    items: [
      { label: 'Rémunérations', amount: 0 },
      { label: 'Charges sociales', amount: 0 },
      { label: 'Autres charges personnel', amount: 0 },
    ],
  },
  {
    side: BudgetSide.EXPENSE,
    code: '65-68',
    name: 'Autres charges',
    items: [
      { label: 'Gestion courante', amount: 0 },
      { label: 'Charges financières', amount: 0 },
      { label: 'Charges exceptionnelles', amount: 0 },
      { label: 'Amortissements', amount: 0 },
    ],
  },
  {
    side: BudgetSide.EXPENSE,
    code: '86',
    name: 'Emplois bénévolat',
    items: [
      { label: 'Mise à dispo. biens', amount: 0 },
      { label: 'Prestations', amount: 0 },
      { label: 'Personnel bénévole', amount: 0 },
    ],
  },
];

/** Simplified 4-section template for small structures. */
const SIMPLE_TEMPLATE: EditableBudgetSection[] = [
  {
    side: BudgetSide.EXPENSE,
    code: '60',
    name: 'Achats & services',
    items: [{ label: 'Prestations de services', amount: 0 }, { label: 'Fournitures', amount: 0 }],
  },
  {
    side: BudgetSide.EXPENSE,
    code: '64',
    name: 'Personnel',
    items: [{ label: 'Rémunérations', amount: 0 }, { label: 'Charges sociales', amount: 0 }],
  },
];

const TEMPLATES = { standard: ACCOUNTING_TEMPLATE, simple: SIMPLE_TEMPLATE } as const;

/** Builds the fixed, non-deletable revenue section whose amount tracks the campaign's goal. */
function buildFixedRevenueSection(goal: number): EditableBudgetSection {
  return {
    side: BudgetSide.REVENUE,
    code: 'PRODUIT',
    name: FIXED_REVENUE_SECTION_NAME,
    items: [{ label: FIXED_REVENUE_ITEM_LABEL, amount: goal }],
  };
}

/** True if this section is the fixed "Produit / Dons" revenue category. */
export function isFixedRevenueSection(s: { side: typeof BudgetSide[keyof typeof BudgetSide]; name: string }): boolean {
  return s.side === BudgetSide.REVENUE && s.name === FIXED_REVENUE_SECTION_NAME;
}

/** Returns sections with the fixed revenue item's amount forced to the campaign's current goal. */
function withFixedRevenueGoal(sections: EditableBudgetSection[], goal: number): EditableBudgetSection[] {
  return sections.map((s) => (isFixedRevenueSection(s)
    ? { ...s, items: s.items.map((it) => (it.label === FIXED_REVENUE_ITEM_LABEL ? { ...it, amount: goal } : it)) }
    : s));
}

/**
 * Hook managing local budget editing state for a campaign.
 *
 * Budget changes are kept locally until the user clicks "Sauvegarder".
 * The hook exposes computed totals and actions for editing sections and items.
 *
 * @param goal - The campaign's current fundraising goal. The fixed "Produit / Dons"
 *   revenue item is continuously re-derived from this value (never persisted stale)
 *   so it stays in sync whenever the campaign's goal changes, with no manual resync step.
 */
export function useBudget(goal: number): UseBudgetReturn {
  const [sections, setSections] = useState<EditableBudgetSection[]>([]);
  const [collapsedSections, setCollapsedSections] = useState<Set<number>>(new Set());
  const [isDirty, setIsDirty] = useState(false);
  const [isSaving, setIsSaving] = useState(false);
  const { addToast } = useToastStore();

  /** Initialises local state from campaign budget sections. */
  const init = useCallback((budgetSections: BudgetSectionDto[]) => {
    setSections(
      withFixedRevenueGoal(
        budgetSections.map((s) => ({
          side: s.side,
          code: s.code,
          name: s.name,
          items: s.items.map((it) => ({ label: it.label, amount: it.amount })),
        })),
        goal,
      ),
    );
    setCollapsedSections(new Set());
    setIsDirty(false);
  }, [goal]);

  /** Pre-fills with the chosen template (amounts at 0), plus the fixed revenue section. */
  const initTemplate = useCallback((type: BudgetTemplateType = 'standard') => {
    const tpl = TEMPLATES[type];
    setSections([...tpl.map((s) => ({ ...s, items: s.items.map((it) => ({ ...it })) })), buildFixedRevenueSection(goal)]);
    setCollapsedSections(new Set());
    setIsDirty(true);
  }, [goal]);

  /** Returns a shallow-cloned sections array after applying a mutation. */
  const mutateSections = useCallback(
    (mutate: (draft: EditableBudgetSection[]) => void) => {
      setSections((prev) => {
        const next = prev.map((s) => ({ ...s, items: [...s.items] }));
        mutate(next);
        return next;
      });
      setIsDirty(true);
    },
    [],
  );

  /** Updates the label of an item. */
  const updateItemLabel = useCallback(
    (sIdx: number, iIdx: number, label: string) => {
      mutateSections((d) => { d[sIdx].items[iIdx] = { ...d[sIdx].items[iIdx], label }; });
    },
    [mutateSections],
  );

  /** Updates the amount of an item. */
  const updateItemAmount = useCallback(
    (sIdx: number, iIdx: number, amount: number) => {
      mutateSections((d) => { d[sIdx].items[iIdx] = { ...d[sIdx].items[iIdx], amount }; });
    },
    [mutateSections],
  );

  /** Adds an empty item to a section. */
  const addItem = useCallback(
    (sIdx: number) => {
      mutateSections((d) => { d[sIdx].items.push({ label: '', amount: 0 }); });
    },
    [mutateSections],
  );

  /** Removes an item from a section. */
  const removeItem = useCallback(
    (sIdx: number, iIdx: number) => {
      mutateSections((d) => { d[sIdx].items.splice(iIdx, 1); });
    },
    [mutateSections],
  );

  /** Adds a new empty section. */
  const addSection = useCallback(
    (side: typeof BudgetSide[keyof typeof BudgetSide], code: string, name: string) => {
      mutateSections((d) => { d.push({ side, code, name, items: [{ label: '', amount: 0 }] }); });
    },
    [mutateSections],
  );

  /** Removes a section and cleans up its collapsed entry. */
  const removeSection = useCallback(
    (sIdx: number) => {
      mutateSections((d) => { d.splice(sIdx, 1); });
      setCollapsedSections((prev) => {
        const next = new Set<number>();
        prev.forEach((i) => { if (i < sIdx) next.add(i); else if (i > sIdx) next.add(i - 1); });
        return next;
      });
    },
    [mutateSections],
  );

  /** Toggles a section's collapsed state. */
  const toggleSection = useCallback((sIdx: number) => {
    setCollapsedSections((prev) => {
      const next = new Set(prev);
      if (next.has(sIdx)) next.delete(sIdx);
      else next.add(sIdx);
      return next;
    });
  }, []);

  /**
   * Converts local state to a {@link SaveBudgetRequest} and calls the API.
   * On success, resets isDirty and shows a toast, unless `silent` (autosave).
   *
   * @param campaignId - UUID of the campaign to save the budget for.
   * @param silent - If true, suppresses the success toast.
   * @returns The updated campaign DTO, or null on error.
   */
  const save = useCallback(
    async (campaignId: string, silent = false): Promise<CampaignDto | null> => {
      setIsSaving(true);
      try {
        const payload: SaveBudgetRequest = {
          sections: withFixedRevenueGoal(sections, goal).map((s, sIdx) => ({
            side: s.side,
            code: s.code,
            name: s.name,
            sortOrder: sIdx,
            items: s.items.map((it, iIdx) => ({
              label: it.label,
              amount: it.amount,
              sortOrder: iIdx,
            })),
          })),
        };
        const updated = await saveBudget(campaignId, payload);
        setIsDirty(false);
        if (!silent) addToast('success', 'budgetSaved');
        return updated;
      } catch {
        addToast('error', 'errors.serverError');
        return null;
      } finally {
        setIsSaving(false);
      }
    },
    [sections, goal, addToast],
  );

  const totalCharges = useMemo(
    () =>
      sections
        .filter((s) => s.side === BudgetSide.EXPENSE)
        .flatMap((s) => s.items)
        .reduce((acc, it) => acc + (it.amount || 0), 0),
    [sections],
  );

  const totalProduits = useMemo(
    () =>
      sections
        .filter((s) => s.side === BudgetSide.REVENUE)
        .flatMap((s) => s.items)
        .reduce((acc, it) => acc + (it.amount || 0), 0),
    [sections],
  );

  const balance = totalProduits - totalCharges;

  return {
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
  };
}
