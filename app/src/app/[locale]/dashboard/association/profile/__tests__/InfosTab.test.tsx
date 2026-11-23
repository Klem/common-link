import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
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
  widgetAllowedOrigin: null,
  addressLine1: null,
  legalObject: null,
  signerName: null,
  signerRole: null,
  landingTheme: 'DEFAULT',
  landingLogo: null,
  gtmContainerId: null,
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

// The mandate state is re-read after every profile save: its receipt prerequisites (legal object,
// head-office address) come from the Informations fields.
const mockRefreshMandate = vi.fn().mockResolvedValue(undefined);
vi.mock('@/hooks/dashboard/useMandate', () => ({
  useMandate: () => ({
    state: { signed: false, mandateDocs: [], blocked: false, receiptPrerequisitesMissing: ['HEAD_OFFICE_ADDRESS'] },
    isLoading: false,
    uploadDoc: vi.fn(),
    deleteDoc: vi.fn(),
    sign: vi.fn(),
    revoke: vi.fn(),
    downloadPdf: vi.fn(),
    refresh: mockRefreshMandate,
  }),
}));

vi.mock('@/components/settings/diffusion/DiffusionTab', () => ({
  DiffusionTab: () => null,
}));

vi.mock('@/lib/api/campaign', () => ({
  getCampaigns: vi.fn().mockResolvedValue([]),
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

// ── Tests ────────────────────────────────────────────────────────────────────

describe('Onglet Informations — profile/page.tsx', () => {
  beforeEach(() => {
    mockUpdateProfile.mockClear();
    mockRefreshMandate.mockClear();
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

  it('no longer edits the authorised signer (moved to the mandate tab, attestation required)', () => {
    render(<AssociationProfilePage />);
    expect(screen.queryByLabelText(/association\.profile\.signerName/)).not.toBeInTheDocument();
    expect(screen.queryByLabelText(/association\.profile\.signerRole/)).not.toBeInTheDocument();
    expect(screen.queryByPlaceholderText('association.profile.signerNamePlaceholder')).not.toBeInTheDocument();
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
    const payload = mockUpdateProfile.mock.calls[0][0];
    expect(payload).not.toHaveProperty('signerName');
    expect(payload).not.toHaveProperty('signerRole');
    await waitFor(() => expect(mockRefreshMandate).toHaveBeenCalledOnce());
  });

  // ── Head-office address (receipt prerequisite: street, postal code and city) ──

  it('edits the head-office postal code and city', () => {
    render(<AssociationProfilePage />);
    expect(screen.getByLabelText('association.profile.postalCode')).not.toBeDisabled();
    expect(screen.getByLabelText('association.profile.city')).not.toBeDisabled();
  });

  it('refuses a postal code that is not 5 digits (mirror of the backend @Pattern)', async () => {
    render(<AssociationProfilePage />);
    fireEvent.change(screen.getByLabelText('association.profile.postalCode'), { target: { value: '7500' } });

    await act(async () => { fireEvent.click(screen.getByRole('button', { name: /association\.profile\.save/i })); });

    await waitFor(() => {
      expect(screen.getByText('dashboard.association.profile.errors.postalCodeFormat')).toBeInTheDocument();
    });
    expect(mockUpdateProfile).not.toHaveBeenCalled();
  });

  it('sends the postal code and city on submit', async () => {
    render(<AssociationProfilePage />);
    fireEvent.change(screen.getByLabelText('association.profile.postalCode'), { target: { value: '75009' } });
    fireEvent.change(screen.getByLabelText('association.profile.city'), { target: { value: 'Paris' } });

    await act(async () => { fireEvent.click(screen.getByRole('button', { name: /association\.profile\.save/i })); });

    await waitFor(() => {
      expect(mockUpdateProfile).toHaveBeenCalledWith(expect.objectContaining({ postalCode: '75009', city: 'Paris' }));
    });
  });

  // ── Debounced autosave ──────────────────────────────────────────────────────

  describe('autosave', () => {
    beforeEach(() => {
      vi.useFakeTimers();
    });

    afterEach(() => {
      vi.useRealTimers();
    });

    /**
     * Types a value, then lets the (asynchronous) zod validation settle.
     * Without this flush, the timer would advance before the patch is even scheduled.
     */
    const type = async (currentValue: string, next: string) => {
      fireEvent.change(screen.getByDisplayValue(currentValue), { target: { value: next } });
      await act(async () => {});
    };

    it("n'enregistre pas immédiatement à la frappe", async () => {
      render(<AssociationProfilePage />);
      await type('01 23 45 67 89', '06 12 34 56 78');
      expect(mockUpdateProfile).not.toHaveBeenCalled();
    });

    it('envoie un patch silencieux du seul champ modifié après 800 ms', async () => {
      render(<AssociationProfilePage />);
      await type('01 23 45 67 89', '06 12 34 56 78');

      await act(async () => { vi.advanceTimersByTime(800); });

      expect(mockUpdateProfile).toHaveBeenCalledTimes(1);
      expect(mockUpdateProfile).toHaveBeenCalledWith({ phone: '06 12 34 56 78' }, true);
    });

    it('re-reads the mandate state after an autosave, so the receipt prerequisites stay current', async () => {
      render(<AssociationProfilePage />);
      fireEvent.change(screen.getByLabelText('association.profile.city'), { target: { value: 'Paris' } });
      await act(async () => {});
      expect(mockRefreshMandate).not.toHaveBeenCalled();

      await act(async () => { vi.advanceTimersByTime(800); });

      expect(mockUpdateProfile).toHaveBeenCalledWith({ city: 'Paris' }, true);
      expect(mockRefreshMandate).toHaveBeenCalledOnce();
    });

    it('fusionne plusieurs champs modifiés dans la même fenêtre de debounce', async () => {
      render(<AssociationProfilePage />);
      await type('01 23 45 67 89', '06 12 34 56 78');
      await act(async () => { vi.advanceTimersByTime(300); });
      await type('contact@asso.org', 'new@asso.org');

      await act(async () => { vi.advanceTimersByTime(800); });

      expect(mockUpdateProfile).toHaveBeenCalledTimes(1);
      expect(mockUpdateProfile).toHaveBeenCalledWith(
        { phone: '06 12 34 56 78', contactEmail: 'new@asso.org' },
        true,
      );
    });

    it("n'envoie rien tant que le champ est invalide", async () => {
      render(<AssociationProfilePage />);
      await type('123456789', 'INVALID');

      await act(async () => { vi.advanceTimersByTime(800); });

      expect(mockUpdateProfile).not.toHaveBeenCalled();
    });

    it("l'autosave d'un champ valide ne masque pas l'erreur d'un autre champ", async () => {
      render(<AssociationProfilePage />);
      await type('123456789', 'INVALID');
      await type('01 23 45 67 89', '06 12 34 56 78');

      await act(async () => { vi.advanceTimersByTime(800); });

      expect(mockUpdateProfile).toHaveBeenCalledWith({ phone: '06 12 34 56 78' }, true);
      expect(
        screen.getByText('dashboard.association.profile.errors.sirenFormat'),
      ).toBeInTheDocument();
      expect(screen.getByRole('button', { name: /association\.profile\.save/i })).not.toBeDisabled();
    });
  });
});
