import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import { AssoManualEntry } from '../AssoManualEntry';

vi.mock('next-intl', () => ({
  useTranslations: () => (key: string) => key,
}));

/** One `results[]` entry, shaped as the live Recherche d'entreprises API returns it. */
const registryEntry = {
  siren: '775672272',
  nom_raison_sociale: 'CROIX ROUGE FRANCAISE',
  nom_complet: 'CROIX ROUGE FRANCAISE (CRF)',
  nature_juridique: '9230',
  etat_administratif: 'A',
  complements: { est_association: true },
  siege: {
    libelle_commune: 'PARIS',
    code_postal: '75014',
    adresse: 'SITE CROIX ROUGE 98 RUE DIDOT 75014 PARIS',
  },
};

const mockRegistry = (body: unknown, status = 200) => {
  (global.fetch as ReturnType<typeof vi.fn>).mockResolvedValue({
    ok: status >= 200 && status < 300,
    status,
    json: async () => body,
  });
};

const sirenInput = () => screen.getByLabelText('assoManual.label');
const searchButton = () => screen.getByRole('button', { name: 'assoManual.search' });
const confirmButton = () => screen.findByRole('button', { name: /assoManual.confirm/ });

const runLookup = (value = '775672272') => {
  fireEvent.change(sirenInput(), { target: { value } });
  fireEvent.click(searchButton());
};

describe('AssoManualEntry', () => {
  beforeEach(() => {
    global.fetch = vi.fn();
  });

  afterEach(() => {
    vi.restoreAllMocks();
  });

  it('keeps the lookup disabled until the SIREN is complete', () => {
    render(<AssoManualEntry onSelect={vi.fn()} onBack={vi.fn()} />);

    expect(searchButton()).toBeDisabled();

    fireEvent.change(sirenInput(), { target: { value: '77567227' } });
    expect(searchButton()).toBeDisabled();

    fireEvent.change(sirenInput(), { target: { value: '775672272' } });
    expect(searchButton()).toBeEnabled();
  });

  it('strips display separators from a pasted SIREN', () => {
    render(<AssoManualEntry onSelect={vi.fn()} onBack={vi.fn()} />);

    fireEvent.change(sirenInput(), { target: { value: '775 672-272' } });

    expect(sirenInput()).toHaveValue('775672272');
    expect(searchButton()).toBeEnabled();
  });

  it('rejects a pasted RNA instead of truncating it to 9 digits', () => {
    render(<AssoManualEntry onSelect={vi.fn()} onBack={vi.fn()} />);

    fireEvent.change(sirenInput(), { target: { value: 'W123456789' } });

    expect(sirenInput()).toHaveValue('W12345678');
    expect(searchButton()).toBeDisabled();
  });

  it('queries the registry directly, with no backend hop', async () => {
    mockRegistry({ results: [registryEntry] });
    render(<AssoManualEntry onSelect={vi.fn()} onBack={vi.fn()} />);

    runLookup();

    await screen.findByText('CROIX ROUGE FRANCAISE');
    const url = (global.fetch as ReturnType<typeof vi.fn>).mock.calls[0][0] as string;
    expect(url).toContain('https://recherche-entreprises.api.gouv.fr/search');
    expect(url).toContain('q=775672272');
  });

  it('prefers the bare legal name over the acronym-suffixed display name', async () => {
    mockRegistry({ results: [registryEntry] });
    render(<AssoManualEntry onSelect={vi.fn()} onBack={vi.fn()} />);

    runLookup();

    expect(await screen.findByText('CROIX ROUGE FRANCAISE')).toBeInTheDocument();
    expect(screen.queryByText('CROIX ROUGE FRANCAISE (CRF)')).not.toBeInTheDocument();
  });

  it('falls back to nom_complet when the registry has no raison sociale', async () => {
    mockRegistry({ results: [{ siren: '775672272', nom_complet: 'ASSO SANS RAISON' }] });
    render(<AssoManualEntry onSelect={vi.fn()} onBack={vi.fn()} />);

    runLookup();

    expect(await screen.findByText('ASSO SANS RAISON')).toBeInTheDocument();
  });

  it('ignores a fuzzy top hit and keeps the exact SIREN match', async () => {
    mockRegistry({
      results: [
        { siren: '999999999', nom_raison_sociale: 'AUTRE ENTITE' },
        { siren: '775672272', nom_raison_sociale: 'LA BONNE' },
      ],
    });
    render(<AssoManualEntry onSelect={vi.fn()} onBack={vi.fn()} />);

    runLookup();

    expect(await screen.findByText('LA BONNE')).toBeInTheDocument();
  });

  it('emits the SIREN as identifier so later sign-up steps are unchanged', async () => {
    mockRegistry({ results: [registryEntry] });
    const onSelect = vi.fn();
    render(<AssoManualEntry onSelect={onSelect} onBack={vi.fn()} />);

    runLookup();
    fireEvent.click(await confirmButton());

    expect(onSelect).toHaveBeenCalledWith({
      identifier: '775672272',
      nom: 'CROIX ROUGE FRANCAISE',
      ville: 'PARIS',
      codePostal: '75014',
    });
  });

  it('falls back to empty strings when the registry has no head office', async () => {
    mockRegistry({ results: [{ siren: '775672272', nom_raison_sociale: 'ASSO SANS SIEGE' }] });
    const onSelect = vi.fn();
    render(<AssoManualEntry onSelect={onSelect} onBack={vi.fn()} />);

    runLookup();
    fireEvent.click(await confirmButton());

    expect(onSelect).toHaveBeenCalledWith(
      expect.objectContaining({ ville: '', codePostal: '' }),
    );
  });

  it('warns without blocking when the entity is not flagged as an association', async () => {
    mockRegistry({
      results: [{
        siren: '775672272',
        nom_raison_sociale: 'SARL MACHIN',
        nature_juridique: '5499',
        etat_administratif: 'C',
        complements: { est_association: false },
      }],
    });
    render(<AssoManualEntry onSelect={vi.fn()} onBack={vi.fn()} />);

    runLookup();

    expect(await screen.findByText(/assoManual.notAssociation/)).toBeInTheDocument();
    expect(screen.getByText(/assoManual.inactive/)).toBeInTheDocument();
    expect(await confirmButton()).toBeEnabled();
  });

  it('reports an unknown SIREN when no entry matches exactly', async () => {
    mockRegistry({ results: [{ siren: '999999999', nom_raison_sociale: 'AUTRE' }] });
    render(<AssoManualEntry onSelect={vi.fn()} onBack={vi.fn()} />);

    runLookup();

    expect(await screen.findByText('assoManual.errors.notFound')).toBeInTheDocument();
  });

  it('reports an unknown SIREN on an empty result set', async () => {
    mockRegistry({ results: [] });
    render(<AssoManualEntry onSelect={vi.fn()} onBack={vi.fn()} />);

    runLookup();

    expect(await screen.findByText('assoManual.errors.notFound')).toBeInTheDocument();
  });

  it('reports throttling when the registry returns 429', async () => {
    mockRegistry({}, 429);
    render(<AssoManualEntry onSelect={vi.fn()} onBack={vi.fn()} />);

    runLookup();

    expect(await screen.findByText('assoManual.errors.rateLimited')).toBeInTheDocument();
  });

  it('reports the registry as unavailable on a 5xx', async () => {
    mockRegistry({}, 503);
    render(<AssoManualEntry onSelect={vi.fn()} onBack={vi.fn()} />);

    runLookup();

    expect(await screen.findByText('assoManual.errors.unavailable')).toBeInTheDocument();
  });

  it('reports the registry as unavailable when the request throws', async () => {
    (global.fetch as ReturnType<typeof vi.fn>).mockRejectedValue(new Error('offline'));
    render(<AssoManualEntry onSelect={vi.fn()} onBack={vi.fn()} />);

    runLookup();

    expect(await screen.findByText('assoManual.errors.unavailable')).toBeInTheDocument();
  });

  it('returns to the RNA search on back', async () => {
    const onBack = vi.fn();
    render(<AssoManualEntry onSelect={vi.fn()} onBack={onBack} />);

    fireEvent.click(screen.getByRole('button', { name: /assoManual.backToRna/ }));

    await waitFor(() => expect(onBack).toHaveBeenCalled());
  });
});
