import React from 'react';
import { Link, useNavigate, useLocation } from 'react-router-dom';
import type { FriendView } from '../types/friendship';
import { directMessageService } from '../services/directMessageService';

interface Props {
  friends: FriendView[];
}

export const SideTreeContactList: React.FC<Props> = ({ friends }) => {
  const navigate = useNavigate();
  const { pathname } = useLocation();

  const openDm = async (userId: string) => {
    try {
      const conv = await directMessageService.getOrCreateWith(userId);
      navigate(`/dms/${conv.id}`);
    } catch (e) {
      console.error('Failed to open DM', e);
    }
  };

  return (
    <details open className="px-2 py-1">
      <summary className="cursor-pointer text-xs font-semibold uppercase text-gray-500 py-1">
        Contacts
      </summary>
      {friends.length === 0 ? (
        <p className="pl-4 pr-2 text-xs text-gray-400 italic py-1">No friends yet</p>
      ) : (
        <ul>
          {friends.map((f) => {
            const dmPath = pathname.startsWith('/dms/') ? pathname : '';
            return (
              <li key={f.userId}>
                <Link
                  to={dmPath}
                  onClick={(e) => {
                    e.preventDefault();
                    openDm(f.userId);
                  }}
                  className="flex items-center justify-between pl-4 pr-2 py-1 text-sm hover:bg-gray-100 rounded"
                >
                  <span className="truncate">
                    <span className="text-gray-400 mr-1" aria-label="offline">
                      ○
                    </span>
                    {f.username}
                  </span>
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
