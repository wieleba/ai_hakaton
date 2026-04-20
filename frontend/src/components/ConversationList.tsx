import React from 'react';
import { Link } from 'react-router-dom';
import type { ConversationView } from '../types/directMessage';

interface Props {
  conversations: ConversationView[];
}

export const ConversationList: React.FC<Props> = ({ conversations }) => {
  if (conversations.length === 0) {
    return (
      <p className="text-gray-500 italic dark:text-discord-dim">
        No direct messages yet. Start one from your Friends page.
      </p>
    );
  }
  return (
    <ul className="divide-y border rounded dark:divide-discord-border dark:border-discord-border">
      {conversations.map((c) => (
        <li key={c.id}>
          <Link
            to={`/dms/${c.id}`}
            className="flex justify-between items-center p-3 hover:bg-gray-50 dark:text-discord-text dark:hover:bg-discord-hover"
          >
            <div>
              <div className="font-medium">{c.otherUsername}</div>
              <div className="text-sm text-gray-500 truncate max-w-xs dark:text-discord-dim">
                {c.lastMessage ?? 'No messages yet'}
              </div>
            </div>
            {c.lastMessageAt && (
              <div className="text-xs text-gray-400 dark:text-discord-dim">
                {new Date(c.lastMessageAt).toLocaleString()}
              </div>
            )}
          </Link>
        </li>
      ))}
    </ul>
  );
};
