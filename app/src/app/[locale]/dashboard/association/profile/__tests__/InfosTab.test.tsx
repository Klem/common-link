import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, fireEvent, waitFor, act } from '@testing-library/react';
import AssociationProfilePage from '../page';
import type { AssociationProfileDto } from '@/types/association';

// ── Mocks ────────────────────────────────────────────────────────────────────

vi.mock('next-intl', () => ({
  useTranslations: () => (key: string) => key,
}));

vi.mock('next/navigation', () => ({
  useRouter: () => ({ push: vi.fn() }),
  useSearchParams: () => ({ get: () => null }),
}));

const mockUpdateProfile = vi.fn().mockResolvedValue(undefined);
const mockProfile: AssociationProfileDto = {
  id: 'uuid-1',
  name: 'Les Petits Écoliers',
  identifier: '123 456 789',
  city: null,
  postalCode: null,
  contactName: null,
  description: null,
  siren: '123456789',
  creationYear: 2018,
  contactEmail: 'contact@asso.org',
  phone: '01 23 45 67 89',
  verificationStatus: 'UNVERIFIED',
  verificationRejectionReason: null,
  widgetToken: null,
  widgetDestinationCampaignId: null,
};

vi.mock('@/hooks/dashboard/useAssociationProfile', () => ({
  useAssociationProfile: () => ({
    profile: mockProfile,
    isLoading: false,
    error: null,
    isSuccess: false,
    updateProfile: mockUpdateProfile,
    refreshProfile: vi.fn().mockResolvedValue(undefined),
  }),
}));

vi.mock('@/components/settings/WidgetTab', () => ({
  WidgetTab: () => null,
}));

vi.mock('@/lib/api/campaign', () => ({
  getCampaigns: vi.fn().mockResolvedValue([]),
}));

vi.mock('@/hooks/monerium/useMoneriumStatus', () => ({
  useMoneriumStatus: () => ({ connected: false, pending: false, isLoading: false, refresh: vi.fn() }),
}));

vi.mock('@/hooks/auth/useSetPassword', () => ({
  useSetPassword: () => ({ onSubmit: vi.fn(), loading: false }),
}));

vi.mock('@/stores/authStore', () => ({
  useAuthStore: (sel: (s: { user: { provider: 'EMAIL' } }) => unknown) =>
    sel({ user: { provider: 'EMAIL' } }),
}));

vi.mock('@/components/dashboard/Topbar', () => ({
  Topbar: () => null,
}));

vi.mock('@/components/auth/SetPasswordForm', () => ({
  SetPasswordForm: () => null,
}));

vi.mock('@/components/dashboard/MoneriumOnboardModal', () => ({
  default: () => null,
}));

// ── Tests ────────────────────────────────────────────────────────────────────

describe('Onglet Informations — profile/page.tsx', () => {
  beforeEach(() => {
    mockUpdateProfile.mockClear();
  });

  it('affiche les champs read-only Nom et SIRET désactivés', () => {
    render(<AssociationProfilePage />);
    expect(screen.getByDisplayValue('Les Petits Écoliers')).toBeDisabled();
    expect(screen.getByDisplayValue('123 456 789')).toBeDisabled();
  });

  it('affiche les champs éditables pré-remplis', () => {
    render(<AssociationProfilePage />);
    expect(screen.getByDisplayValue('123456789')).not.toBeDisabled();
    expect(screen.getByDisplayValue('contact@asso.org')).not.toBeDisabled();
    expect(screen.getByDisplayValue('01 23 45 67 89')).not.toBeDisabled();
  });

  it('affiche une erreur si le RNA ne respecte pas le format', async () => {
    render(<AssociationProfilePage />);

    const rnaInput = screen.getByDisplayValue('123456789');
    fireEvent.change(rnaInput, { target: { value: 'INVALID' } });

    const saveBtn = screen.getByRole('button', { name: /association\.profile\.save/i });
    await act(async () => { fireEvent.click(saveBtn); });

    await waitFor(() => {
      expect(
        screen.getByText('dashboard.association.profile.errors.sirenFormat'),
      ).toBeInTheDocument();
    });
    expect(mockUpdateProfile).not.toHaveBeenCalled();
  });

  it('appelle updateProfile avec les bons champs au submit valide', async () => {
    render(<AssociationProfilePage />);

    const phoneInput = screen.getByDisplayValue('01 23 45 67 89');
    fireEvent.change(phoneInput, { target: { value: '06 12 34 56 78' } });

    const saveBtn = screen.getByRole('button', { name: /association\.profile\.save/i });
    await act(async () => { fireEvent.click(saveBtn); });

    await waitFor(() => {
      expect(mockUpdateProfile).toHaveBeenCalledWith(
        expect.objectContaining({
          siren: '123456789',
          contactEmail: 'contact@asso.org',
          phone: '06 12 34 56 78',
        }),
      );
    });
  });
});
