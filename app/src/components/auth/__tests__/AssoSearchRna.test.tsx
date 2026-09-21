import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, fireEvent, waitFor, act } from '@testing-library/react';
import { AssoSearch } from '../AssoSearch';

vi.mock('next-intl', () => ({
  useTranslations: () => (key: string) => key,
}));

const fetchMock = vi.fn();
global.fetch = fetchMock as unknown as typeof fetch;

/** Builds a JOAFE response with a single announcement row. */
function joafeRow(fields: Record<string, string>) {
  return { ok: true, json: async () => ({ records: [{ record: { fields } }] }) };
}

/** Builds a `recherche-entreprises` response. */
function registry(results: unknown[]) {
  return { ok: true, json: async () => ({ results }) };
}

const LEGACY_ROW = {
  id: '198700031469',
  numero_rna: 'ASS01469',
  titre: 'ASSOCIATION ZO PROD.',
  typeavis: 'Création',
  commune_actuelle: 'Poitiers',
  codepostal_actuel: '86000',
};

/** Types a query and lets the 320 ms debounce elapse. */
async function searchFor(term: string) {
  vi.useFakeTimers();
  fireEvent.change(screen.getByRole('textbox'), { target: { value: term } });
  await act(async () => {
    vi.advanceTimersByTime(400);
  });
  vi.useRealTimers();
}

describe('AssoSearch — RNA integrity', () => {
  beforeEach(() => {
    fetchMock.mockReset();
  });

  it('escapes an apostrophe with a double-quoted ODSQL literal, which the API accepts', async () => {
    fetchMock.mockResolvedValue({ ok: true, json: async () => ({ records: [] }) });

    render(<AssoSearch onSelect={vi.fn()} />);
    await searchFor("SAINT JULIEN L'ARS");

    await waitFor(() => expect(fetchMock).toHaveBeenCalled());
    const where = new URL(String(fetchMock.mock.calls[0][0])).searchParams.get('where');
    expect(where).toBe(`titre like "%SAINT JULIEN L'ARS%"`);
    // The SQL-style doubling previously used here is rejected with HTTP 400.
    expect(where).not.toContain("L''ARS");
  });

  it('does not present a legacy announcement number as an RNA', async () => {
    fetchMock.mockResolvedValue(joafeRow(LEGACY_ROW));

    render(<AssoSearch onSelect={vi.fn()} />);
    await searchFor('ZO PROD');

    await waitFor(() => expect(screen.getByText('ASSOCIATION ZO PROD.')).toBeInTheDocument());
    expect(screen.queryByText(/ASS01469/)).not.toBeInTheDocument();
  });

  it('resolves the real RNA from the registry before handing the selection over', async () => {
    const onSelect = vi.fn();
    fetchMock
      .mockResolvedValueOnce(joafeRow(LEGACY_ROW))
      .mockResolvedValueOnce(
        registry([
          {
            nom_complet: 'ASSOCIATION ZO PROD',
            complements: { est_association: true, identifiant_association: 'W863001492' },
          },
        ]),
      );

    render(<AssoSearch onSelect={onSelect} />);
    await searchFor('ZO PROD');
    await waitFor(() => expect(screen.getByText('ASSOCIATION ZO PROD.')).toBeInTheDocument());

    fireEvent.click(screen.getByText(/signup.association.search.select/));

    await waitFor(() =>
      expect(onSelect).toHaveBeenCalledWith(
        expect.objectContaining({ identifier: 'W863001492', nom: 'ASSOCIATION ZO PROD.' }),
      ),
    );
  });

  it('refuses the selection when the registry answers with several different RNAs', async () => {
    const onSelect = vi.fn();
    fetchMock
      .mockResolvedValueOnce(joafeRow(LEGACY_ROW))
      .mockResolvedValueOnce(
        registry([
          {
            nom_complet: 'ASSOCIATION ZO PROD',
            complements: { est_association: true, identifiant_association: 'W863001492' },
          },
          {
            nom_complet: 'ASSOCIATION ZO PROD',
            complements: { est_association: true, identifiant_association: 'W863009999' },
          },
        ]),
      );

    render(<AssoSearch onSelect={onSelect} />);
    await searchFor('ZO PROD');
    await waitFor(() => expect(screen.getByText('ASSOCIATION ZO PROD.')).toBeInTheDocument());

    fireEvent.click(screen.getByText(/signup.association.search.select/));

    await waitFor(() =>
      expect(screen.getByText('assoSearch.rnaUnresolved')).toBeInTheDocument(),
    );
    expect(onSelect).not.toHaveBeenCalled();
  });

  it('refuses the selection when the registry knows no matching association', async () => {
    const onSelect = vi.fn();
    fetchMock
      .mockResolvedValueOnce(joafeRow(LEGACY_ROW))
      .mockResolvedValueOnce(registry([]));

    render(<AssoSearch onSelect={onSelect} />);
    await searchFor('ZO PROD');
    await waitFor(() => expect(screen.getByText('ASSOCIATION ZO PROD.')).toBeInTheDocument());

    fireEvent.click(screen.getByText(/signup.association.search.select/));

    await waitFor(() =>
      expect(screen.getByText('assoSearch.rnaUnresolved')).toBeInTheDocument(),
    );
    expect(onSelect).not.toHaveBeenCalled();
  });

  it('hands a real RNA straight over without querying the registry', async () => {
    const onSelect = vi.fn();
    fetchMock.mockResolvedValue(
      joafeRow({
        id: '201000060509',
        numero_rna: 'W313013399',
        titre: 'CULTURE PROD',
        typeavis: 'Création',
        commune_actuelle: 'Toulouse',
        codepostal_actuel: '31000',
      }),
    );

    render(<AssoSearch onSelect={onSelect} />);
    await searchFor('CULTURE PROD');
    await waitFor(() => expect(screen.getByText('CULTURE PROD')).toBeInTheDocument());

    fireEvent.click(screen.getByText(/signup.association.search.select/));

    await waitFor(() =>
      expect(onSelect).toHaveBeenCalledWith(
        expect.objectContaining({ identifier: 'W313013399' }),
      ),
    );
    // One call only: the JOAFE search. No registry round-trip was needed.
    expect(fetchMock).toHaveBeenCalledTimes(1);
  });

  it('keeps two associations sharing one legacy number as distinct rows', async () => {
    fetchMock.mockResolvedValue({
      ok: true,
      json: async () => ({
        records: [
          { record: { fields: LEGACY_ROW } },
          {
            record: {
              fields: {
                id: '198500031469',
                numero_rna: 'ASS01469',
                titre: 'AMNESTY INTERNATIONAL GROUPE 421',
                typeavis: 'Création',
                commune_actuelle: 'Fort-de-France',
                codepostal_actuel: '97200',
              },
            },
          },
        ],
      }),
    });

    render(<AssoSearch onSelect={vi.fn()} />);
    await searchFor('groupe');

    await waitFor(() => expect(screen.getByText('ASSOCIATION ZO PROD.')).toBeInTheDocument());
    expect(screen.getByText('AMNESTY INTERNATIONAL GROUPE 421')).toBeInTheDocument();
  });
});
