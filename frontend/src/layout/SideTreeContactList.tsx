import React from 'react';
import { Link, useNavigate, useLocation } from 'react-router-dom';
import type { FriendView } from '../types/friendship';
import type { ConversationView } from '../types/directMessage';
import { directMessageService } from '../services/directMessageService';
import { usePresence } from '../hooks/usePresence';
import { useUnread } from '../hooks/useUnread';
import type { PresenceState } from '../types/presence';

function dotFor(state: PresenceState): { symbol: string; className: string; label: string } {
  if (state === 'ONLINE') return { symbol: '●', className: 'text-green-500 mr-1', label: 'online' };
  if (state === 'AFK') return { symbol: '◐', className: 'text-yellow-500 mr-1', label: 'AFK' };
  return { symbol: '○', className: 'text-gray-400 mr-1', label: 'offline' };
}

interface Props {
  friends: FriendView[];
  conversations: ConversationView[];
}

export const SideTreeContactList: React.FC<Props> = ({ friends, conversations }) => {
  const navigate = useNavigate();
  const { pathname } = useLocation();

  const friendIds = friends.map((f) => f.userId);
  const getPresence = usePresence(friendIds);
  const { dmCount } = useUnread();

  const convByUser = new Map<string, string>();
  for (const c of conversations) convByUser.set(c.otherUserId, c.id);

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
                    {(() => {
                      const { symbol, className, label } = dotFor(getPresence(f.userId));
                      return <span className={className} aria-label={label}>{symbol}</span>;
                    })()}
                    {f.username}
                  </span>
                  {(() => {
                    const convId = convByUser.get(f.userId);
                    const count = convId ? dmCount(convId) : 0;
                    if (count === 0) return null;
                    return (
                      <span
                        className="ml-1 inline-block bg-blue-600 text-white text-xs font-semibold px-1.5 py-0.5 rounded-full"
                        aria-label={`${count} unread`}
                      >
                        {count}
                      </span>
                    );
                  })()}
                </Link>
              </li>
            );
          })}
        </ul>
      )}
    </details>
  );
};
