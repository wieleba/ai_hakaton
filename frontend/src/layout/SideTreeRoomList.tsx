import React from 'react';
import { Link, useLocation } from 'react-router-dom';
import type { ChatRoom } from '../types/room';
import { useUnread } from '../hooks/useUnread';

interface Props {
  title: string;
  rooms: ChatRoom[];
  emptyHint: string;
}

export const SideTreeRoomList: React.FC<Props> = ({ title, rooms, emptyHint }) => {
  const { pathname } = useLocation();
  const { roomCount } = useUnread();
  return (
    <details open className="px-2 py-1">
      <summary className="cursor-pointer text-xs font-semibold uppercase text-gray-500 py-1 dark:text-discord-dim">
        {title}
      </summary>
      {rooms.length === 0 ? (
        <p className="pl-4 pr-2 text-xs text-gray-400 italic py-1 dark:text-discord-dim">{emptyHint}</p>
      ) : (
        <ul>
          {rooms.map((r) => {
            const to = `/rooms/${r.id}`;
            const active = pathname === to;
            return (
              <li key={r.id}>
                <Link
                  to={to}
                  className={`block pl-4 pr-2 py-1 text-sm truncate rounded ${
                    active
                      ? 'bg-blue-100 text-blue-700 dark:bg-discord-accent/20 dark:text-blue-300'
                      : 'hover:bg-gray-100 dark:text-discord-text dark:hover:bg-discord-hover'
                  }`}
                >
                  # {r.name}
                  {roomCount(r.id) > 0 && (
                    <span
                      className="ml-1 inline-block bg-blue-600 text-white text-xs font-semibold px-1.5 py-0.5 rounded-full align-middle"
                      aria-label={`${roomCount(r.id)} unread`}
                    >
                      {roomCount(r.id)}
                    </span>
                  )}
                </Link>
              </li>
            );
          })}
        </ul>
      )}
    </details>
  );
};
