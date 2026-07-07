import { describe, it, expect, vi, beforeEach } from 'vitest';
import { renderHook, act } from '@testing-library/react';
import { useBudget, isFixedRevenueSection } from '../useBudget';
import { BudgetSide } from '@/types/campaign';

vi.mock('@/lib/api/campaign', () => ({
  saveBudget: vi.fn(),
}));

vi.mock('@/stores/toastStore', () => ({
  useToastStore: () => ({ addToast: vi.fn() }),
}));

import { saveBudget } from '@/lib/api/campaign';

const mockSaveBudget = saveBudget as ReturnType<typeof vi.fn>;

describe('useBudget', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  describe('fixed "Produit / Dons" revenue section', () => {
    it('initTemplate appends a single fixed revenue section derived from goal', () => {
      const { result } = renderHook(() => useBudget(5000));

      act(() => { result.current.initTemplate('standard'); });

      const revenueSections = result.current.sections.filter((s) => s.side === BudgetSide.REVENUE);
      expect(revenueSections).toHaveLength(1);
      expect(isFixedRevenueSection(revenueSections[0])).toBe(true);
      expect(revenueSections[0].items).toEqual([{ label: 'Dons', amount: 5000 }]);
    });

    it('initTemplate works the same for the simple template', () => {
      const { result } = renderHook(() => useBudget(1200));

      act(() => { result.current.initTemplate('simple'); });

      const revenueSections = result.current.sections.filter((s) => s.side === BudgetSide.REVENUE);
      expect(revenueSections).toHaveLength(1);
      expect(revenueSections[0].items[0].amount).toBe(1200);
    });

    it('init() overrides a stale persisted amount with the current goal (zero desync)', () => {
      const { result } = renderHook(() => useBudget(9000));

      act(() => {
        result.current.init([
          { side: BudgetSide.EXPENSE, code: '60', name: 'Achats', items: [{ label: 'Fournitures', amount: 200 }] },
          { side: BudgetSide.REVENUE, code: 'PRODUIT', name: 'Produit', items: [{ label: 'Dons', amount: 1 }] },
        ]);
      });

      const revenueSection = result.current.sections.find((s) => s.side === BudgetSide.REVENUE)!;
      expect(revenueSection.items[0].amount).toBe(9000);
    });

    it('re-syncs the fixed amount when the goal prop changes, without a manual resync step', () => {
      const { result, rerender } = renderHook(({ goal }) => useBudget(goal), { initialProps: { goal: 1000 } });

      act(() => { result.current.initTemplate('standard'); });
      expect(result.current.sections.find((s) => s.side === BudgetSide.REVENUE)!.items[0].amount).toBe(1000);

      rerender({ goal: 2500 });
      act(() => {
        result.current.init(
          result.current.sections.map((s) => ({
            side: s.side,
            code: s.code,
            name: s.name,
            items: s.items,
          })),
        );
      });

      expect(result.current.sections.find((s) => s.side === BudgetSide.REVENUE)!.items[0].amount).toBe(2500);
    });

    it('save() forces the fixed item amount to the current goal even if local state was corrupted', async () => {
      const { result } = renderHook(() => useBudget(7500));
      mockSaveBudget.mockResolvedValue(null);

      act(() => { result.current.initTemplate('standard'); });
      act(() => { result.current.updateItemAmount(result.current.sections.length - 1, 0, 1); });

      await act(async () => { await result.current.save('campaign-1'); });

      const payload = mockSaveBudget.mock.calls[0][1];
      const revenueSection = payload.sections.find((s: { side: string }) => s.side === BudgetSide.REVENUE);
      expect(revenueSection.items[0].amount).toBe(7500);
    });
  });
});
