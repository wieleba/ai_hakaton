import React from 'react';

interface Props {
  authorUsername: string;
  textPreview: string;
  onDismiss: () => void;
}

export const ReplyPill: React.FC<Props> = ({ authorUsername, textPreview, onDismiss }) => (
  <div className="flex items-center gap-2 bg-blue-50 border border-blue-200 rounded px-3 py-1 text-xs">
    <span className="truncate">
      Replying to <strong>@{authorUsername}</strong>: {textPreview}
    </span>
    <button
      onClick={onDismiss}
      className="ml-auto text-gray-500 hover:text-gray-700"
      aria-label="Cancel reply"
    >
      ×
    </button>
  </div>
);
