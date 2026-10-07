'use client';

import { useRef } from 'react';
import { useEditor, EditorContent } from '@tiptap/react';
import StarterKit from '@tiptap/starter-kit';
import Underline from '@tiptap/extension-underline';
import Link from '@tiptap/extension-link';
import Image from '@tiptap/extension-image';
import Placeholder from '@tiptap/extension-placeholder';
import { useTranslations } from 'next-intl';

interface Props {
  value: string;
  onChange: (html: string) => void;
  /** Uploads the file and resolves to the public URL to embed as `<img src>`. */
  onImageUpload: (file: File) => Promise<string>;
  placeholder?: string;
}

const ACCEPTED_IMAGE_TYPES = ['image/jpeg', 'image/png', 'image/webp'];

/**
 * Rich-text editor for the campaign story (Tiptap). The allowed formatting mirrors exactly what
 * `StoryHtmlSanitizer` keeps server-side (`p, br, strong, em, u, h2, h3, ul, ol, li, a, img`) --
 * marks/nodes StarterKit ships with but the backend strips (strike, code, blockquote, horizontal
 * rule) are disabled here too, so the toolbar never offers formatting that silently disappears
 * on save.
 */
export function RichTextEditor({ value, onChange, onImageUpload, placeholder }: Props) {
  const t = useTranslations('dashboard.campaigns.editor.story.toolbar');
  const fileInputRef = useRef<HTMLInputElement>(null);

  const editor = useEditor({
    extensions: [
      StarterKit.configure({
        heading: { levels: [2, 3] },
        strike: false,
        code: false,
        codeBlock: false,
        blockquote: false,
        horizontalRule: false,
        // StarterKit (v3) bundles its own Link and Underline -- disabled here so the explicit,
        // separately-configured instances below don't collide with them (Tiptap warns loudly on
        // "duplicate extension names" and only one instance's config actually takes effect).
        link: false,
        underline: false,
      }),
      Underline,
      Link.configure({ openOnClick: false, autolink: true }),
      Image,
      Placeholder.configure({ placeholder: placeholder ?? '' }),
    ],
    content: value,
    immediatelyRender: false,
    onUpdate: ({ editor }) => onChange(editor.getHTML()),
    editorProps: {
      attributes: { class: 'rte-content' },
    },
  });

  if (!editor) return null;

  function handleImageButtonClick(): void {
    fileInputRef.current?.click();
  }

  async function handleFileSelected(e: React.ChangeEvent<HTMLInputElement>): Promise<void> {
    const file = e.target.files?.[0];
    e.target.value = '';
    if (!file || !editor) return;
    if (!ACCEPTED_IMAGE_TYPES.includes(file.type)) return;
    try {
      const url = await onImageUpload(file);
      editor.chain().focus().setImage({ src: url }).run();
    } catch {
      // Upload failures surface via the caller's own toast (CampaignStoryTab) — nothing to add here.
    }
  }

  // Only ever invoked from the toolbar button below, which doesn't render until past the
  // `if (!editor) return null` guard above — TypeScript can't see that through a function
  // declaration, so the non-null assertions here are safe, not a bypass.
  function handleLinkButtonClick(): void {
    const previousUrl = editor!.getAttributes('link').href as string | undefined;
    const url = window.prompt(t('linkPrompt'), previousUrl ?? 'https://');
    if (url === null) return;
    if (url === '') {
      editor!.chain().focus().extendMarkRange('link').unsetLink().run();
      return;
    }
    editor!.chain().focus().extendMarkRange('link').setLink({ href: url }).run();
  }

  return (
    <div className="rte">
      <div className="rte-toolbar" role="toolbar" aria-label={t('label')}>
        <button
          type="button"
          className={`cm-btn cm-btn-xs${editor.isActive('bold') ? ' cm-btn-primary' : ' cm-btn-ghost'}`}
          onClick={() => editor.chain().focus().toggleBold().run()}
          aria-pressed={editor.isActive('bold')}
          aria-label={t('bold')}
        >
          <strong>B</strong>
        </button>
        <button
          type="button"
          className={`cm-btn cm-btn-xs${editor.isActive('italic') ? ' cm-btn-primary' : ' cm-btn-ghost'}`}
          onClick={() => editor.chain().focus().toggleItalic().run()}
          aria-pressed={editor.isActive('italic')}
          aria-label={t('italic')}
        >
          <em>I</em>
        </button>
        <button
          type="button"
          className={`cm-btn cm-btn-xs${editor.isActive('underline') ? ' cm-btn-primary' : ' cm-btn-ghost'}`}
          onClick={() => editor.chain().focus().toggleUnderline().run()}
          aria-pressed={editor.isActive('underline')}
          aria-label={t('underline')}
        >
          <u>U</u>
        </button>
        <span className="rte-sep" aria-hidden="true" />
        <button
          type="button"
          className={`cm-btn cm-btn-xs${editor.isActive('heading', { level: 2 }) ? ' cm-btn-primary' : ' cm-btn-ghost'}`}
          onClick={() => editor.chain().focus().toggleHeading({ level: 2 }).run()}
          aria-pressed={editor.isActive('heading', { level: 2 })}
          aria-label={t('heading2')}
        >
          H2
        </button>
        <button
          type="button"
          className={`cm-btn cm-btn-xs${editor.isActive('heading', { level: 3 }) ? ' cm-btn-primary' : ' cm-btn-ghost'}`}
          onClick={() => editor.chain().focus().toggleHeading({ level: 3 }).run()}
          aria-pressed={editor.isActive('heading', { level: 3 })}
          aria-label={t('heading3')}
        >
          H3
        </button>
        <span className="rte-sep" aria-hidden="true" />
        <button
          type="button"
          className={`cm-btn cm-btn-xs${editor.isActive('bulletList') ? ' cm-btn-primary' : ' cm-btn-ghost'}`}
          onClick={() => editor.chain().focus().toggleBulletList().run()}
          aria-pressed={editor.isActive('bulletList')}
          aria-label={t('bulletList')}
        >
          •≡
        </button>
        <button
          type="button"
          className={`cm-btn cm-btn-xs${editor.isActive('orderedList') ? ' cm-btn-primary' : ' cm-btn-ghost'}`}
          onClick={() => editor.chain().focus().toggleOrderedList().run()}
          aria-pressed={editor.isActive('orderedList')}
          aria-label={t('orderedList')}
        >
          1≡
        </button>
        <span className="rte-sep" aria-hidden="true" />
        <button
          type="button"
          className={`cm-btn cm-btn-xs${editor.isActive('link') ? ' cm-btn-primary' : ' cm-btn-ghost'}`}
          onClick={handleLinkButtonClick}
          aria-pressed={editor.isActive('link')}
          aria-label={t('link')}
        >
          🔗
        </button>
        <button
          type="button"
          className="cm-btn cm-btn-xs cm-btn-ghost"
          onClick={handleImageButtonClick}
          aria-label={t('image')}
        >
          🖼️
        </button>
        <input
          ref={fileInputRef}
          type="file"
          hidden
          accept={ACCEPTED_IMAGE_TYPES.join(',')}
          onChange={handleFileSelected}
        />
      </div>
      <EditorContent editor={editor} />
    </div>
  );
}
