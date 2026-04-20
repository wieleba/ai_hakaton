import React, { forwardRef, useImperativeHandle, useRef, useState } from 'react';
import { ComposerActions } from './ComposerActions';

export interface MessageInputHandle {
  insertText: (toInsert: string) => void;
}

interface MessageInputProps {
  onSend: (text: string) => void;
  disabled: boolean;
  actions?: React.ReactNode;
  /** When true, allow send with empty text (e.g. a file is staged). */
  canSubmitWithoutText?: boolean;
  /** Called when the user pastes a file into the textarea. */
  onPasteFile?: (file: File) => void;
}

const MAX_LENGTH = 3072;

export const MessageInput = forwardRef<MessageInputHandle, MessageInputProps>(
  ({ onSend, disabled, actions, canSubmitWithoutText, onPasteFile }, ref) => {
    const [text, setText] = useState('');
    const textareaRef = useRef<HTMLTextAreaElement>(null);

    const send = () => {
      if (disabled) return;
      const trimmed = text.trim();
      if (!trimmed && !canSubmitWithoutText) return;
      onSend(text);
      setText('');
    };

    const handleSubmit = (e: React.FormEvent) => {
      e.preventDefault();
      send();
    };

    const handleKeyDown = (e: React.KeyboardEvent<HTMLTextAreaElement>) => {
      if (e.key === 'Enter' && (e.ctrlKey || e.metaKey)) {
        e.preventDefault();
        send();
      }
    };

    const handlePaste = (e: React.ClipboardEvent<HTMLTextAreaElement>) => {
      if (!onPasteFile) return;
      const files = e.clipboardData?.files;
      if (!files || files.length === 0) return;
      e.preventDefault();
      onPasteFile(files[0]);
    };

    useImperativeHandle(ref, () => ({
      insertText: (toInsert: string) => {
        const el = textareaRef.current;
        if (!el) {
          setText((prev) => (prev + toInsert).slice(0, MAX_LENGTH));
          return;
        }
        const start = el.selectionStart ?? text.length;
        const end = el.selectionEnd ?? text.length;
        const next = (text.slice(0, start) + toInsert + text.slice(end)).slice(0, MAX_LENGTH);
        setText(next);
        // Restore focus and move caret to the end of inserted emoji after React renders.
        requestAnimationFrame(() => {
          el.focus();
          const caret = Math.min(start + toInsert.length, next.length);
          el.setSelectionRange(caret, caret);
        });
      },
    }));

    const remaining = MAX_LENGTH - text.length;

    return (
      <form
        onSubmit={handleSubmit}
        className="border-t p-4 bg-white rounded dark:bg-discord-sidebar dark:border-discord-border"
      >
        <ComposerActions>{actions}</ComposerActions>
        <div className="flex gap-2">
          <textarea
            ref={textareaRef}
            value={text}
            onChange={(e) => setText(e.target.value.slice(0, MAX_LENGTH))}
            onKeyDown={handleKeyDown}
            onPaste={handlePaste}
            placeholder="Type a message... (Ctrl+Enter to send)"
            disabled={disabled}
            rows={3}
            className="flex-1 border rounded px-3 py-2 resize-none dark:bg-discord-input dark:border-discord-border dark:text-discord-text dark:placeholder-discord-dim"
          />
          <button
            type="submit"
            disabled={disabled || (!text.trim() && !canSubmitWithoutText)}
            className="px-4 py-2 bg-blue-500 text-white rounded hover:bg-blue-600 disabled:bg-gray-400 self-end dark:bg-discord-accent dark:hover:bg-indigo-500 dark:disabled:bg-discord-hover dark:disabled:text-discord-dim"
          >
            Send
          </button>
        </div>
        <div className="text-xs text-gray-500 mt-2 dark:text-discord-dim">
          {remaining} characters remaining · Ctrl+Enter (⌘+Enter on Mac) to send
        </div>
      </form>
    );
  },
);

MessageInput.displayName = 'MessageInput';
