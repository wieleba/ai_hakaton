import React, { useState } from 'react';

interface Props {
  initialText: string;
  onSave: (text: string) => Promise<void>;
  onCancel: () => void;
}

const MAX = 3072;

export const InlineMessageEditor: React.FC<Props> = ({ initialText, onSave, onCancel }) => {
  const [text, setText] = useState(initialText);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const save = async () => {
    const trimmed = text.trim();
    if (!trimmed) return;
    if (trimmed === initialText) {
      onCancel();
      return;
    }
    setBusy(true);
    setError(null);
    try {
      await onSave(trimmed);
    } catch (e) {
      setError(e instanceof Error ? e.message : String(e));
      setBusy(false);
    }
  };

  const onKeyDown = (e: React.KeyboardEvent<HTMLTextAreaElement>) => {
    if (e.key === 'Enter' && (e.ctrlKey || e.metaKey)) {
      e.preventDefault();
      save();
    } else if (e.key === 'Escape') {
      e.preventDefault();
      onCancel();
    }
  };

  return (
    <div className="space-y-1">
      <textarea
        value={text}
        onChange={(e) => setText(e.target.value.slice(0, MAX))}
        onKeyDown={onKeyDown}
        rows={3}
        disabled={busy}
        className="w-full border rounded px-2 py-1 resize-none text-sm"
        autoFocus
      />
      {error && <div className="text-xs text-red-500">{error}</div>}
      <div className="flex gap-2 text-xs">
        <button
          onClick={save}
          disabled={busy || !text.trim()}
          className="px-3 py-1 bg-blue-500 text-white rounded disabled:bg-gray-400"
        >
          {busy ? 'Saving…' : 'Save'}
        </button>
        <button onClick={onCancel} disabled={busy} className="px-3 py-1 border rounded">
          Cancel
        </button>
        <span className="text-gray-400 self-center">Ctrl+Enter · Esc</span>
      </div>
    </div>
  );
};
