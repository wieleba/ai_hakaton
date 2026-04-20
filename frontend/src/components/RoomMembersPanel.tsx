import React, { useEffect, useState, useCallback } from 'react';
import { friendshipService } from '../services/friendshipService';
import { roomService } from '../services/roomService';
import type { ChatRoom } from '../types/room';
import type { RoomMemberView } from '../types/roomModeration';
import { useRoomMembersWithRole } from '../hooks/useRoomMembersWithRole';
import { InviteUserModal } from './InviteUserModal';
import { ManageRoomModal } from './ManageRoomModal';
import { useRoomAdminActions } from '../hooks/useRoomAdminActions';
import { usePresence } from '../hooks/usePresence';
import { dotFor } from '../utils/presenceDot';
import type { PresenceState } from '../types/presence';

interface Props {
  roomId: string;
  currentUserId: string;
}

export const RoomMembersPanel: React.FC<Props> = ({ roomId, currentUserId }) => {
  const { members, isAdmin, reload } = useRoomMembersWithRole(roomId, currentUserId);
  const [room, setRoom] = useState<ChatRoom | null>(null);
  const [friendIds, setFriendIds] = useState<Set<string>>(new Set());
  const [isInviteOpen, setInviteOpen] = useState(false);
  const [isManageOpen, setManageOpen] = useState(false);
  const { invite } = useRoomAdminActions(roomId);

  useEffect(() => {
    roomService
      .getRoomById(roomId)
      .then(setRoom)
      .catch((e) => console.error('Failed to load room', e));
  }, [roomId]);

  useEffect(() => {
    friendshipService
      .listFriends()
      .then((fs) => setFriendIds(new Set(fs.map((f) => f.userId))));
  }, []);

  const sendRequest = useCallback(async (username: string, userId: string) => {
    try {
      await friendshipService.sendRequest(username);
      setFriendIds((prev) => new Set([...prev, userId]));
    } catch (e) {
      console.error('Friend request failed', e);
    }
  }, []);

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

  const memberIds = members.map((m) => m.userId);
  const getPresence = usePresence(memberIds);

  const online: RoomMemberView[] = [];
  const afk: RoomMemberView[] = [];
  const offline: RoomMemberView[] = [];
  for (const m of members) {
    const state = getPresence(m.userId);
    if (state === 'ONLINE') online.push(m);
    else if (state === 'AFK') afk.push(m);
    else offline.push(m);
  }

  const renderGroup = (title: string, state: PresenceState, list: RoomMemberView[]) => {
    const { symbol, className, label } = dotFor(state);
    return (
      <div>
        <div className="text-xs font-semibold text-gray-500 uppercase mt-3 mb-1">
          {title} ({list.length})
        </div>
        <ul className="space-y-1">
          {list.map((m) => {
            const isMe = m.userId === currentUserId;
            const isFriend = friendIds.has(m.userId);
            return (
              <li key={m.userId} className="flex justify-between items-center text-sm">
                <span className="flex items-center gap-1 truncate">
                  <span className={className} aria-label={label}>
                    {symbol}
                  </span>
                  <span className="truncate">
                    {m.username}
                    {isMe && ' (you)'}
                  </span>
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
              </li>
            );
          })}
        </ul>
      </div>
    );
  };

  return (
    <aside className="w-72 border-l bg-white p-4 overflow-y-auto">
      <div>
        <h3 className="font-semibold text-lg truncate">{room?.name ?? 'Loading\u2026'}</h3>
        {room && (
          <span
            className={`text-xs px-2 py-0.5 rounded ${
              room.visibility === 'private'
                ? 'bg-purple-100 text-purple-700'
                : 'bg-green-100 text-green-700'
            }`}
          >
            {room.visibility}
          </span>
        )}
      </div>

      {renderGroup('Online', 'ONLINE', online)}
      {renderGroup('AFK', 'AFK', afk)}
      {renderGroup('Offline', 'OFFLINE', offline)}

      {isAdmin && (
        <div className="mt-4 pt-4 border-t space-y-2">
          {room?.visibility === 'private' && (
            <button
              onClick={() => setInviteOpen(true)}
              className="w-full px-3 py-2 border rounded hover:bg-blue-50 text-sm"
            >
              Invite user
            </button>
          )}
          <button
            onClick={() => setManageOpen(true)}
            className="w-full px-3 py-2 border rounded hover:bg-blue-50 text-sm"
          >
            Manage room
          </button>
        </div>
      )}

      <InviteUserModal
        isOpen={isInviteOpen}
        onClose={() => setInviteOpen(false)}
        onInvite={doInvite}
      />
      <ManageRoomModal
        isOpen={isManageOpen}
        onClose={() => {
          setManageOpen(false);
          reload();
        }}
        roomId={roomId}
        currentUserId={currentUserId}
        room={room}
      />
    </aside>
  );
};
