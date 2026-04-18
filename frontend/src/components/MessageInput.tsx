import React, { useState } from 'react';
import { ComposerActions } from './ComposerActions';

interface MessageInputProps {
  onSend: (text: string) => void;
  disabled: boolean;
  actions?: React.ReactNode;
}

const MAX_LENGTH = 3072;

export const MessageInput: React.FC<MessageInputProps> = ({ onSend, disabled, actions }) => {
  const [text, setText] = useState('');

  const send = () => {
    if (disabled) return;
    const trimmed = text.trim();
    if (!trimmed) return;
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

  const remaining = MAX_LENGTH - text.length;

  return (
    <form onSubmit={handleSubmit} className="border-t p-4 bg-white rounded">
      <ComposerActions>{actions}</ComposerActions>
      <div className="flex gap-2">
        <textarea
          value={text}
          onChange={(e) => setText(e.target.value.slice(0, MAX_LENGTH))}
          onKeyDown={handleKeyDown}
          placeholder="Type a message... (Ctrl+Enter to send)"
          disabled={disabled}
          rows={3}
          className="flex-1 border rounded px-3 py-2 resize-none"
        />
        <button
          type="submit"
          disabled={disabled || !text.trim()}
          className="px-4 py-2 bg-blue-500 text-white rounded hover:bg-blue-600 disabled:bg-gray-400 self-end"
        >
          Send
        </button>
      </div>
      <div className="text-xs text-gray-500 mt-2">
        {remaining} characters remaining · Ctrl+Enter (⌘+Enter on Mac) to send
      </div>
    </form>
  );
};
