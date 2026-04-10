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
  /** Pre-fills with the standard French association accounting template (amounts at 0). */
  initTemplate: () => void;
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
  /** Saves the budget to the API. */
  save: (campaignId: string) => Promise<CampaignDto | null>;
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
  {
    side: BudgetSide.REVENUE,
    code: '70',
    name: 'Ventes / prestations',
    items: [
      { label: 'Ventes de produits', amount: 0 },
      { label: 'Prestations de services', amount: 0 },
    ],
  },
  {
    side: BudgetSide.REVENUE,
    code: '74',
    name: 'Subventions',
    items: [
      { label: 'État', amount: 0 },
      { label: 'Régions', amount: 0 },
      { label: 'Départements', amount: 0 },
      { label: 'Communes', amount: 0 },
      { label: 'Organismes sociaux', amount: 0 },
      { label: 'Fonds européens', amount: 0 },
      { label: 'Mécénat / aides privées', amount: 0 },
    ],
  },
  {
    side: BudgetSide.REVENUE,
    code: '75-78',
    name: 'Autres produits',
    items: [
      { label: 'Produits de gestion', amount: 0 },
      { label: 'Produits financiers', amount: 0 },
      { label: 'Produits exceptionnels', amount: 0 },
    ],
  },
  {
    side: BudgetSide.REVENUE,
    code: '87',
    name: 'Contributions vol.',
    items: [
      { label: 'Bénévolat', amount: 0 },
      { label: 'Prestations en nature', amount: 0 },
      { label: 'Dons en nature', amount: 0 },
    ],
  },
];

/**
 * Hook managing local budget editing state for a campaign.
 *
 * Budget changes are kept locally until the user clicks "Sauvegarder".
 * The hook exposes computed totals and actions for editing sections and items.
 */
export function useBudget(): UseBudgetReturn {
  const [sections, setSections] = useState<EditableBudgetSection[]>([]);
  const [collapsedSections, setCollapsedSections] = useState<Set<number>>(new Set());
  const [isDirty, setIsDirty] = useState(false);
  const [isSaving, setIsSaving] = useState(false);
  const { addToast } = useToastStore();

  /** Initialises local state from campaign budget sections. */
  const init = useCallback((budgetSections: BudgetSectionDto[]) => {
    setSections(
      budgetSections.map((s) => ({
        side: s.side,
        code: s.code,
        name: s.name,
        items: s.items.map((it) => ({ label: it.label, amount: it.amount })),
      })),
    );
    setCollapsedSections(new Set());
    setIsDirty(false);
  }, []);

  /** Pre-fills with the standard French association accounting template. */
  const initTemplate = useCallback(() => {
    setSections(ACCOUNTING_TEMPLATE.map((s) => ({ ...s, items: s.items.map((it) => ({ ...it })) })));
    setCollapsedSections(new Set());
    setIsDirty(true);
  }, []);

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
   * On success, resets isDirty and shows a toast.
   *
   * @param campaignId - UUID of the campaign to save the budget for.
   * @returns The updated campaign DTO, or null on error.
   */
  const save = useCallback(
    async (campaignId: string): Promise<CampaignDto | null> => {
      setIsSaving(true);
      try {
        const payload: SaveBudgetRequest = {
          sections: sections.map((s, sIdx) => ({
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
        addToast('success', 'budgetSaved');
        return updated;
      } catch {
        addToast('error', 'errors.serverError');
        return null;
      } finally {
        setIsSaving(false);
      }
    },
    [sections, addToast],
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
