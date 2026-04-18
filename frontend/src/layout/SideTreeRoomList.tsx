import React from 'react';
import { Link, useLocation } from 'react-router-dom';
import type { ChatRoom } from '../types/room';

interface Props {
  title: string;
  rooms: ChatRoom[];
  emptyHint: string;
}

export const SideTreeRoomList: React.FC<Props> = ({ title, rooms, emptyHint }) => {
  const { pathname } = useLocation();
  return (
    <details open className="px-2 py-1">
      <summary className="cursor-pointer text-xs font-semibold uppercase text-gray-500 py-1">
        {title}
      </summary>
      {rooms.length === 0 ? (
        <p className="pl-4 pr-2 text-xs text-gray-400 italic py-1">{emptyHint}</p>
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
                    active ? 'bg-blue-100 text-blue-700' : 'hover:bg-gray-100'
                  }`}
                >
                  # {r.name}{' '}
                  <span className="text-xs text-gray-400" aria-label="unread count">
                    (0)
                  </span>
                </Link>
              </li>
            );
          })}
        </ul>
      )}
    </details>
  );
};
