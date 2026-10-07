import { describe, it, expect, vi } from 'vitest';
import { render, screen, waitFor } from '@testing-library/react';
import { RichTextEditor } from '../RichTextEditor';

vi.mock('next-intl', () => ({
  useTranslations: () => (key: string) => key,
}));

describe('RichTextEditor', () => {
  it('renders the toolbar and the initial content', async () => {
    render(
      <RichTextEditor value="<p>Bonjour</p>" onChange={vi.fn()} onImageUpload={vi.fn()} />,
    );

    await waitFor(() => expect(screen.getByRole('toolbar')).toBeInTheDocument());
    expect(screen.getByLabelText('bold')).toBeInTheDocument();
    expect(screen.getByLabelText('italic')).toBeInTheDocument();
    expect(screen.getByLabelText('image')).toBeInTheDocument();
    expect(screen.getByText('Bonjour')).toBeInTheDocument();
  });

  it('only offers formatting the backend sanitizer keeps -- no strike, code or blockquote buttons', async () => {
    render(<RichTextEditor value="" onChange={vi.fn()} onImageUpload={vi.fn()} />);

    await waitFor(() => expect(screen.getByRole('toolbar')).toBeInTheDocument());
    expect(screen.queryByLabelText(/strike/i)).not.toBeInTheDocument();
    expect(screen.queryByLabelText(/code/i)).not.toBeInTheDocument();
    expect(screen.queryByLabelText(/quote/i)).not.toBeInTheDocument();
  });

  it('has a hidden file input restricted to the accepted image types', async () => {
    const { container } = render(
      <RichTextEditor value="" onChange={vi.fn()} onImageUpload={vi.fn()} />,
    );

    await waitFor(() => expect(screen.getByRole('toolbar')).toBeInTheDocument());
    const fileInput = container.querySelector('input[type="file"]');
    expect(fileInput).toHaveAttribute('accept', 'image/jpeg,image/png,image/webp');
  });

  it('carries the placeholder text on the empty paragraph node when the document is empty', async () => {
    const { container } = render(
      <RichTextEditor value="" onChange={vi.fn()} onImageUpload={vi.fn()} placeholder="Décrivez le récit…" />,
    );

    await waitFor(() => expect(screen.getByRole('toolbar')).toBeInTheDocument());
    const emptyNode = container.querySelector('.rte-content p.is-editor-empty');
    expect(emptyNode).not.toBeNull();
    expect(emptyNode).toHaveAttribute('data-placeholder', 'Décrivez le récit…');
  });
});
