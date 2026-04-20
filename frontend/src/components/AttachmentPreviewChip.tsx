import React from 'react';
import { prettySize } from '../types/attachment';

interface Props {
  file: File;
  onRemove: () => void;
}

export const AttachmentPreviewChip: React.FC<Props> = ({ file, onRemove }) => (
  <div className="flex items-center gap-2 bg-gray-100 border border-gray-300 rounded px-3 py-1 text-xs dark:bg-discord-hover dark:border-discord-border dark:text-discord-text">
    <span className="truncate max-w-xs">
      📎 {file.name} · <span className="text-gray-500 dark:text-discord-dim">{prettySize(file.size)}</span>
    </span>
    <button
      onClick={onRemove}
      className="ml-auto text-gray-500 hover:text-gray-700 dark:text-discord-dim dark:hover:text-discord-text"
      aria-label="Remove attachment"
      type="button"
    >
      ×
    </button>
  </div>
);
