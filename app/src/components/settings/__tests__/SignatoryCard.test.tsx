import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, fireEvent, act, waitFor } from '@testing-library/react';
import { SignatoryCard } from '@/components/settings/SignatoryCard';
import { SignatureImageError, SignatureImageErrorCode } from '@/lib/signatureImage';
import type { SignatoryDto } from '@/types/signatory';

// ── Mocks ────────────────────────────────────────────────────────────────────

vi.mock('next-intl', () => ({
  useTranslations: () => (key: string) => key,
  useLocale: () => 'fr',
}));

const { mockProcessFile } = vi.hoisted(() => ({ mockProcessFile: vi.fn() }));

vi.mock('@/lib/signatureImage', async (importOriginal) => ({
  ...(await importOriginal<typeof import('@/lib/signatureImage')>()),
  processSignatureFile: mockProcessFile,
}));

const drawnBlob = new Blob(['drawn'], { type: 'image/png' });

// signature_pad needs a real canvas: the pad is replaced by a button emitting a drawn / cleared image.
vi.mock('@/components/settings/SignatureDrawPad', () => ({
  SignatureDrawPad: ({ onChange }: { onChange: (image: Blob | null) => void }) => (
    <div>
      <button type="button" onClick={() => onChange(drawnBlob)}>fake-draw</button>
      <button type="button" onClick={() => onChange(null)}>fake-clear</button>
    </div>
  ),
}));

const K = 'association.profile.mandate.signatory';

const emptySignatory: SignatoryDto = {
  signerName: null,
  signerRole: null,
  mode: null,
  hasSignature: false,
  attestedAt: null,
  updatedAt: null,
};

function renderCard(overrides: Partial<Parameters<typeof SignatoryCard>[0]> = {}) {
  const onSave = vi.fn().mockResolvedValue(true);
  const props = { signatory: emptySignatory, savedImage: null, savedImageUrl: null, onSave, ...overrides };
  render(<SignatoryCard {...props} />);
  return props;
}

const nameInput = () => screen.getByLabelText(/association\.profile\.signerName/);
const roleInput = () => screen.getByLabelText(/association\.profile\.signerRole/);
const saveButton = () => screen.getByRole('button', { name: `${K}.save` });
const attest = () => fireEvent.click(screen.getByRole('checkbox', { name: `${K}.attestation` }));
const selectMode = (labelKey: string) => fireEvent.click(screen.getByRole('radio', { name: `${K}.modes.${labelKey}` }));

function fillIdentity(name = 'Marie Dupont', role = 'Présidente') {
  fireEvent.change(nameInput(), { target: { value: name } });
  fireEvent.change(roleInput(), { target: { value: role } });
}

beforeEach(() => {
  mockProcessFile.mockReset();
  URL.createObjectURL = vi.fn(() => 'blob:preview');
  URL.revokeObjectURL = vi.fn();
});

// ── Tests ────────────────────────────────────────────────────────────────────

describe('SignatoryCard', () => {
  it('prefills the saved signer and shows the saved signature block', () => {
    renderCard({
      signatory: { ...emptySignatory, signerName: 'Marie Dupont', signerRole: 'Présidente', mode: 'DRAWN', hasSignature: true, updatedAt: '2026-11-20T10:00:00Z' },
    });
    expect(nameInput()).toHaveValue('Marie Dupont');
    expect(roleInput()).toHaveValue('Présidente');
    expect(screen.getByText(`${K}.savedTitle`)).toBeInTheDocument();
  });

  it('previews a saved image signature from the image endpoint URL', () => {
    renderCard({
      signatory: { ...emptySignatory, signerName: 'A', signerRole: 'B', mode: 'UPLOAD', hasSignature: true },
      savedImage: new Blob(['x'], { type: 'image/png' }),
      savedImageUrl: 'blob:saved',
    });
    expect(screen.getByAltText(`${K}.savedAlt`)).toHaveAttribute('src', 'blob:saved');
  });

  it('offers only the upload and draw modes, upload by default', () => {
    renderCard();
    expect(screen.getAllByRole('radio').map((r) => (r as HTMLInputElement).value)).toEqual(['UPLOAD', 'DRAWN']);
    expect(screen.getByRole('radio', { name: `${K}.modes.upload` })).toBeChecked();
  });

  it('switches the mode panel with the selector', () => {
    renderCard();
    expect(screen.getByText(`${K}.upload.cta`)).toBeInTheDocument();
    selectMode('drawn');
    expect(screen.getByRole('button', { name: 'fake-draw' })).toBeInTheDocument();
    expect(screen.queryByText(`${K}.upload.cta`)).not.toBeInTheDocument();
  });

  it('keeps save disabled until the attestation is ticked', () => {
    renderCard();
    fillIdentity();
    selectMode('drawn');
    fireEvent.click(screen.getByRole('button', { name: 'fake-draw' }));
    expect(saveButton()).toBeDisabled();
    attest();
    expect(saveButton()).not.toBeDisabled();
  });

  it('keeps save disabled without a signature image, even with identity and attestation', () => {
    renderCard();
    fillIdentity();
    attest();
    expect(saveButton()).toBeDisabled();
  });

  it('keeps save disabled when the role is blank', () => {
    renderCard();
    fillIdentity('Marie Dupont', '   ');
    selectMode('drawn');
    fireEvent.click(screen.getByRole('button', { name: 'fake-draw' }));
    attest();
    expect(saveButton()).toBeDisabled();
  });

  it('saves trimmed identity values with the image', async () => {
    const props = renderCard();
    fillIdentity('  Marie Dupont ', ' Présidente ');
    selectMode('drawn');
    fireEvent.click(screen.getByRole('button', { name: 'fake-draw' }));
    attest();
    await act(async () => { fireEvent.click(saveButton()); });
    expect(props.onSave).toHaveBeenCalledWith({
      signerName: 'Marie Dupont',
      signerRole: 'Présidente',
      mode: 'DRAWN',
      file: drawnBlob,
    });
  });

  it('UPLOAD: shows the client-side refusal of an unsupported type and stays unsavable', async () => {
    mockProcessFile.mockRejectedValue(new SignatureImageError(SignatureImageErrorCode.UNSUPPORTED_TYPE));
    renderCard();
    fillIdentity();
    attest();
    const file = new File(['<svg/>'], 'sig.svg', { type: 'image/svg+xml' });
    await act(async () => {
      fireEvent.change(screen.getByLabelText(`${K}.upload.inputLabel`), { target: { files: [file] } });
    });
    expect(screen.getByRole('alert')).toHaveTextContent(`${K}.errors.unsupportedType`);
    expect(saveButton()).toBeDisabled();
  });

  it('UPLOAD: shows the size refusal when the re-encoded image stays above 200 KB', async () => {
    mockProcessFile.mockRejectedValue(new SignatureImageError(SignatureImageErrorCode.TOO_LARGE));
    renderCard();
    const file = new File(['x'], 'sig.png', { type: 'image/png' });
    await act(async () => {
      fireEvent.change(screen.getByLabelText(`${K}.upload.inputLabel`), { target: { files: [file] } });
    });
    expect(screen.getByRole('alert')).toHaveTextContent(`${K}.errors.tooLarge`);
  });

  it('UPLOAD: sends the re-encoded image, never the original file', async () => {
    const encoded = new Blob(['png'], { type: 'image/png' });
    mockProcessFile.mockResolvedValue(encoded);
    const props = renderCard();
    fillIdentity();
    const file = new File(['original'], 'sig.jpg', { type: 'image/jpeg' });
    await act(async () => {
      fireEvent.change(screen.getByLabelText(`${K}.upload.inputLabel`), { target: { files: [file] } });
    });
    expect(mockProcessFile).toHaveBeenCalledWith(file);
    expect(screen.getByAltText(`${K}.previewAlt`)).toBeInTheDocument();
    attest();
    await act(async () => { fireEvent.click(saveButton()); });
    expect(props.onSave).toHaveBeenCalledWith(expect.objectContaining({ mode: 'UPLOAD', file: encoded }));
  });

  it('DRAWN: an empty pad is invalid, a drawn one is sent', async () => {
    const props = renderCard();
    fillIdentity();
    selectMode('drawn');
    attest();
    expect(saveButton()).toBeDisabled();
    fireEvent.click(screen.getByRole('button', { name: 'fake-draw' }));
    expect(saveButton()).not.toBeDisabled();
    fireEvent.click(screen.getByRole('button', { name: 'fake-clear' }));
    expect(saveButton()).toBeDisabled();
    fireEvent.click(screen.getByRole('button', { name: 'fake-draw' }));
    await act(async () => { fireEvent.click(saveButton()); });
    expect(props.onSave).toHaveBeenCalledWith(expect.objectContaining({ mode: 'DRAWN', file: drawnBlob }));
  });

  it('re-sends the saved image when only the identity changes in the same mode', async () => {
    const saved = new Blob(['saved'], { type: 'image/png' });
    const props = renderCard({
      signatory: { ...emptySignatory, signerName: 'Marie Dupont', signerRole: 'Présidente', mode: 'DRAWN', hasSignature: true },
      savedImage: saved,
      savedImageUrl: 'blob:saved',
    });
    expect(screen.getByText(`${K}.keepSaved`)).toBeInTheDocument();
    fireEvent.change(roleInput(), { target: { value: 'Trésorière' } });
    attest();
    await act(async () => { fireEvent.click(saveButton()); });
    expect(props.onSave).toHaveBeenCalledWith(
      expect.objectContaining({ signerRole: 'Trésorière', mode: 'DRAWN', file: saved }),
    );
  });

  it('does not reuse the saved image in another mode', () => {
    renderCard({
      signatory: { ...emptySignatory, signerName: 'Marie Dupont', signerRole: 'Présidente', mode: 'DRAWN', hasSignature: true },
      savedImage: new Blob(['saved'], { type: 'image/png' }),
    });
    selectMode('upload');
    attest();
    expect(saveButton()).toBeDisabled();
  });

  it('shows an inline error and unticks the attestation when the save is refused', async () => {
    const props = renderCard({ onSave: vi.fn().mockResolvedValue(false) });
    fillIdentity();
    selectMode('drawn');
    fireEvent.click(screen.getByRole('button', { name: 'fake-draw' }));
    attest();
    await act(async () => { fireEvent.click(saveButton()); });
    await waitFor(() => expect(screen.getByText(`${K}.errors.saveFailed`)).toBeInTheDocument());
    expect(props.onSave).toHaveBeenCalledOnce();
    expect(screen.getByRole('checkbox', { name: `${K}.attestation` })).not.toBeChecked();
    expect(saveButton()).toBeDisabled();
  });
});
