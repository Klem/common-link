import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, fireEvent } from '@testing-library/react';
import { CampaignBudgetTab } from '../CampaignBudgetTab';
import type { CampaignDto } from '@/types/campaign';

// ── Mocks ─────────────────────────────────────────────────────────────────────

vi.mock('next-intl', () => ({
  useTranslations: () => (key: string) => key,
}));

vi.mock('@/hooks/campaign/useBudget');
vi.mock('@/stores/toastStore', () => ({
  useToastStore: () => ({ addToast: vi.fn() }),
}));

import { useBudget } from '@/hooks/campaign/useBudget';
const mockUseBudget = useBudget as ReturnType<typeof vi.fn>;

// ── Fixtures ──────────────────────────────────────────────────────────────────

const campaign: CampaignDto = {
  id: 'camp-1',
  name: 'Test',
  emoji: '🌍',
  description: '',
  goal: 10000,
  raised: 0,
  status: 'DRAFT',
  startDate: null,
  endDate: null,
  milestones: [],
  budgetSections: [],
  category: null,
  reason: null,
  impactGoals: null,
  coverImage: null,
};

const mockBudgetBase = {
  collapsedSections: new Set<number>(),
  isDirty: false,
  isSaving: false,
  totalCharges: 0,
  totalProduits: 0,
  balance: 0,
  init: vi.fn(),
  initTemplate: vi.fn(),
  updateItemLabel: vi.fn(),
  updateItemAmount: vi.fn(),
  addItem: vi.fn(),
  removeItem: vi.fn(),
  addSection: vi.fn(),
  removeSection: vi.fn(),
  toggleSection: vi.fn(),
  save: vi.fn().mockResolvedValue(null),
};

// ── Tests ──────────────────────────────────────────────────────────────────────

describe('CampaignBudgetTab', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  describe('empty state', () => {
    beforeEach(() => {
      mockUseBudget.mockReturnValue({ ...mockBudgetBase, sections: [] });
    });

    it('renders 3 template cards', () => {
      render(<CampaignBudgetTab campaign={campaign} onBudgetSaved={vi.fn()} />);
      expect(screen.getByText('editor.budget.tplStandard')).toBeInTheDocument();
      expect(screen.getByText('editor.budget.tplSimple')).toBeInTheDocument();
      expect(screen.getByText('editor.budget.tplBlank')).toBeInTheDocument();
    });

    it('selects standard template by default and calls initTemplate on CTA click', () => {
      const initTemplate = vi.fn();
      mockUseBudget.mockReturnValue({ ...mockBudgetBase, sections: [], initTemplate });
      render(<CampaignBudgetTab campaign={campaign} onBudgetSaved={vi.fn()} />);
      fireEvent.click(screen.getByText('editor.budget.useTpl'));
      expect(initTemplate).toHaveBeenCalledWith('standard');
    });

    it('calls initTemplate with selected type when user picks Simplifié', () => {
      const initTemplate = vi.fn();
      mockUseBudget.mockReturnValue({ ...mockBudgetBase, sections: [], initTemplate });
      render(<CampaignBudgetTab campaign={campaign} onBudgetSaved={vi.fn()} />);
      fireEvent.click(screen.getByText('editor.budget.tplSimple'));
      fireEvent.click(screen.getByText('editor.budget.useTpl'));
      expect(initTemplate).toHaveBeenCalledWith('simple');
    });
  });

  describe('editor state', () => {
    const sections = [
      { side: 'EXPENSE' as const, code: '60', name: 'Achats', items: [{ label: 'Fournitures', amount: 500 }] },
      { side: 'REVENUE' as const, code: '74', name: 'Subventions', items: [{ label: 'État', amount: 1000 }] },
    ];

    beforeEach(() => {
      mockUseBudget.mockReturnValue({
        ...mockBudgetBase,
        sections,
        totalCharges: 500,
        totalProduits: 1000,
        balance: 500,
      });
    });

    it('renders balance bar with charges and produits', () => {
      render(<CampaignBudgetTab campaign={campaign} onBudgetSaved={vi.fn()} />);
      expect(document.querySelector('.bal-val.ch')).toHaveTextContent('500');
      expect(document.querySelector('.bal-val.pr')).toHaveTextContent('1 000');
    });

    it('shows excess pill when balance > 0', () => {
      render(<CampaignBudgetTab campaign={campaign} onBudgetSaved={vi.fn()} />);
      const pill = document.querySelector('.bal-pill');
      expect(pill).toHaveClass('excess');
    });

    it('shows deficit pill when balance < 0', () => {
      mockUseBudget.mockReturnValue({
        ...mockBudgetBase, sections,
        totalCharges: 1000, totalProduits: 500, balance: -500,
      });
      render(<CampaignBudgetTab campaign={campaign} onBudgetSaved={vi.fn()} />);
      const pill = document.querySelector('.bal-pill');
      expect(pill).toHaveClass('deficit');
    });

    it('shows ok pill when balance ≈ 0', () => {
      mockUseBudget.mockReturnValue({
        ...mockBudgetBase, sections,
        totalCharges: 1000, totalProduits: 1000, balance: 0,
      });
      render(<CampaignBudgetTab campaign={campaign} onBudgetSaved={vi.fn()} />);
      const pill = document.querySelector('.bal-pill');
      expect(pill).toHaveClass('ok');
    });

    it('renders section names', () => {
      render(<CampaignBudgetTab campaign={campaign} onBudgetSaved={vi.fn()} />);
      expect(screen.getByText('Achats')).toBeInTheDocument();
      expect(screen.getByText('Subventions')).toBeInTheDocument();
    });

    it('hides charges column when produits filter active', () => {
      render(<CampaignBudgetTab campaign={campaign} onBudgetSaved={vi.fn()} />);
      const produitBtn = document.querySelector('.col-filter-btn.pr') as HTMLElement;
      fireEvent.click(produitBtn);
      expect(document.getElementById('bud-col-charges')).toBeNull();
      expect(document.getElementById('bud-col-produits')).toBeInTheDocument();
    });

    it('hides produits column when charges filter active', () => {
      render(<CampaignBudgetTab campaign={campaign} onBudgetSaved={vi.fn()} />);
      const chargesBtn = document.querySelector('.col-filter-btn.ch') as HTMLElement;
      fireEvent.click(chargesBtn);
      expect(document.getElementById('bud-col-produits')).toBeNull();
      expect(document.getElementById('bud-col-charges')).toBeInTheDocument();
    });

    it('calls init([]) when Changer de modèle is clicked', () => {
      const init = vi.fn();
      mockUseBudget.mockReturnValue({ ...mockBudgetBase, sections, init });
      render(<CampaignBudgetTab campaign={campaign} onBudgetSaved={vi.fn()} />);
      fireEvent.click(screen.getByText('editor.budget.changeTemplate'));
      expect(init).toHaveBeenCalledWith([]);
    });
  });
});
