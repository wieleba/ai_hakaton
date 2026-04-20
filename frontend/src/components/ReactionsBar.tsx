import React from 'react';
import type { ReactionSummary } from '../types/room';

interface Props {
  reactions: ReactionSummary[] | undefined;
  onToggle: (emoji: string) => void;
}

export const ReactionsBar: React.FC<Props> = ({ reactions, onToggle }) => {
  if (!reactions || reactions.length === 0) return null;
  return (
    <div className="flex flex-wrap gap-1 mt-2">
      {reactions.map((r) => (
        <button
          key={r.emoji}
          type="button"
          onClick={() => onToggle(r.emoji)}
          className={`text-xs px-2 py-0.5 rounded border ${
            r.reactedByMe
              ? 'bg-blue-50 border-blue-300 text-blue-700 dark:bg-discord-accent/20 dark:border-discord-accent/60 dark:text-blue-300'
              : 'bg-gray-50 border-gray-200 text-gray-700 hover:bg-gray-100 dark:bg-discord-base dark:border-discord-border dark:text-discord-text dark:hover:bg-discord-hover'
          }`}
          aria-label={`Toggle reaction ${r.emoji}`}
        >
          <span className="mr-1">{r.emoji}</span>
          <span className="tabular-nums">{r.count}</span>
        </button>
      ))}
    </div>
  );
};
