import { describe, it, expect } from 'vitest';
import { render, screen } from '@testing-library/react';
import { RecentActivityList } from '../RecentActivityList';
import { ActivityType, type ActivityItem } from '@/types/association';

vi.mock('next-intl', () => ({
  useTranslations: () => (key: string) => key,
}));

const donationItem: ActivityItem = {
  type: ActivityType.DONATION,
  label: 'Marie L. a donné 50€',
  amount: 50,
  occurredAt: new Date(Date.now() - 12 * 60 * 1000).toISOString(),
};

const paymentItem: ActivityItem = {
  type: ActivityType.PAYMENT,
  label: 'Dépense : 25 kits scolaires',
  occurredAt: new Date(Date.now() - 2 * 60 * 60 * 1000).toISOString(),
};

const milestoneItem: ActivityItem = {
  type: ActivityType.MILESTONE_REACHED,
  label: 'Jalon atteint : 5 000 €',
  occurredAt: new Date(Date.now() - 24 * 60 * 60 * 1000).toISOString(),
};

describe('RecentActivityList', () => {
  it('renders empty state when no items', () => {
    render(<RecentActivityList items={[]} />);
    expect(screen.getByText('empty')).toBeInTheDocument();
  });

  it('renders donation item with green icon', () => {
    const { container } = render(<RecentActivityList items={[donationItem]} />);
    expect(screen.getByText('Marie L. a donné 50€')).toBeInTheDocument();
    expect(container.querySelector('.act-ic.g')).toBeInTheDocument();
  });

  it('renders payment item with amber icon', () => {
    const { container } = render(<RecentActivityList items={[paymentItem]} />);
    expect(container.querySelector('.act-ic.a')).toBeInTheDocument();
  });

  it('renders milestone item with indigo icon', () => {
    const { container } = render(<RecentActivityList items={[milestoneItem]} />);
    expect(container.querySelector('.act-ic.i')).toBeInTheDocument();
  });

  it('renders multiple items', () => {
    render(<RecentActivityList items={[donationItem, paymentItem, milestoneItem]} />);
    expect(screen.getAllByRole('generic').filter((el) => el.className.includes('act-ic'))).toHaveLength(3);
  });
});
