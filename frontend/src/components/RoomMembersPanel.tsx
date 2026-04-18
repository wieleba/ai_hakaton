import React, { useEffect, useState, useCallback } from 'react';
import { friendshipService } from '../services/friendshipService';
import type { RoomMemberView } from '../types/roomModeration';
import { useRoomMembersWithRole } from '../hooks/useRoomMembersWithRole';
import { useRoomAdminActions } from '../hooks/useRoomAdminActions';
import { InviteUserModal } from './InviteUserModal';

interface Props {
  roomId: string;
  currentUserId: string;
  roomVisibility?: 'public' | 'private';
  onOpenBans?: () => void;
}

export const RoomMembersPanel: React.FC<Props> = ({
  roomId,
  currentUserId,
  roomVisibility,
  onOpenBans,
}) => {
  const { members, isAdmin, reload } = useRoomMembersWithRole(roomId, currentUserId);
  const [friendIds, setFriendIds] = useState<Set<string>>(new Set());
  const [isInviteOpen, setInviteOpen] = useState(false);
  const { kick, promote, demote, invite } = useRoomAdminActions(roomId);

  useEffect(() => {
    friendshipService.listFriends().then((fs) => setFriendIds(new Set(fs.map((f) => f.userId))));
  }, []);

  const sendRequest = useCallback(async (username: string, userId: string) => {
    try {
      await friendshipService.sendRequest(username);
      setFriendIds((prev) => new Set([...prev, userId]));
    } catch (e) {
      console.error('Friend request failed', e);
    }
  }, []);

  const doKick = async (userId: string) => {
    await kick(userId);
    await reload();
  };

  const doPromote = async (userId: string) => {
    await promote(userId);
    await reload();
  };

  const doDemote = async (userId: string) => {
    await demote(userId);
    await reload();
  };

  const doInvite = async (username: string) => {
    await invite(username);
  };

  const roleBadge = (m: RoomMemberView) => {
    if (m.isOwner)
      return (
        <span className="text-xs bg-yellow-100 text-yellow-800 px-2 py-0.5 rounded">owner</span>
      );
    if (m.role === 'admin')
      return <span className="text-xs bg-blue-100 text-blue-800 px-2 py-0.5 rounded">admin</span>;
    return null;
  };

  return (
    <aside className="w-64 border-l bg-white p-4 overflow-y-auto">
      <div className="flex justify-between items-center mb-2">
        <h3 className="font-semibold">Members</h3>
        {isAdmin && (
          <div className="flex gap-1">
            {roomVisibility === 'private' && (
              <button
                onClick={() => setInviteOpen(true)}
                className="text-xs px-2 py-1 border rounded hover:bg-blue-50"
              >
                Invite
              </button>
            )}
            {onOpenBans && (
              <button
                onClick={onOpenBans}
                className="text-xs px-2 py-1 border rounded hover:bg-blue-50"
              >
                Bans
              </button>
            )}
          </div>
        )}
      </div>

      <ul className="space-y-2">
        {members.map((m) => {
          const isMe = m.userId === currentUserId;
          const isFriend = friendIds.has(m.userId);
          return (
            <li key={m.userId} className="flex flex-col text-sm">
              <div className="flex justify-between items-center">
                <span className="flex items-center gap-1">
                  {m.username}
                  {isMe && ' (you)'}
                  {roleBadge(m)}
                </span>
                {!isMe && !isFriend && (
                  <button
                    onClick={() => sendRequest(m.username, m.userId)}
                    className="text-xs px-2 py-1 border rounded hover:bg-blue-50"
                  >
                    Add friend
                  </button>
                )}
              </div>
              {isAdmin && !isMe && !m.isOwner && (
                <div className="flex gap-1 mt-1 text-xs">
                  <button
                    onClick={() => doKick(m.userId)}
                    className="px-2 py-0.5 border border-red-400 text-red-600 rounded hover:bg-red-50"
                  >
                    Kick
                  </button>
                  {m.role === 'admin' ? (
                    <button
                      onClick={() => doDemote(m.userId)}
                      className="px-2 py-0.5 border rounded hover:bg-gray-100"
                    >
                      Demote
                    </button>
                  ) : (
                    <button
                      onClick={() => doPromote(m.userId)}
                      className="px-2 py-0.5 border rounded hover:bg-gray-100"
                    >
                      Promote
                    </button>
                  )}
                </div>
              )}
            </li>
          );
        })}
      </ul>

      <InviteUserModal
        isOpen={isInviteOpen}
        onClose={() => setInviteOpen(false)}
        onInvite={doInvite}
      />
    </aside>
  );
};
