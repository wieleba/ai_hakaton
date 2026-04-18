import React, { useCallback, useEffect, useState } from 'react';
import { roomService } from '../services/roomService';
import { friendshipService } from '../services/friendshipService';

interface Props {
  roomId: string;
  currentUserId: string;
}

type Member = { userId: string; username: string };

export const RoomMembersPanel: React.FC<Props> = ({ roomId, currentUserId }) => {
  const [members, setMembers] = useState<Member[]>([]);
  const [friendIds, setFriendIds] = useState<Set<string>>(new Set());

  useEffect(() => {
    let cancelled = false;
    roomService.listMembers(roomId).then((m) => {
      if (!cancelled) setMembers(m);
    });
    friendshipService.listFriends().then((fs) => {
      if (!cancelled) setFriendIds(new Set(fs.map((f) => f.userId)));
    });
    return () => {
      cancelled = true;
    };
  }, [roomId]);

  const sendRequest = useCallback(async (username: string, userId: string) => {
    try {
      await friendshipService.sendRequest(username);
      setFriendIds((prev) => new Set([...prev, userId]));
    } catch (e) {
      console.error('Friend request failed', e);
    }
  }, []);

  return (
    <aside className="w-56 border-l bg-white p-4 overflow-y-auto">
      <h3 className="font-semibold mb-2">Members</h3>
      <ul className="space-y-2">
        {members.map((m) => {
          const isMe = m.userId === currentUserId;
          const isFriend = friendIds.has(m.userId);
          return (
            <li key={m.userId} className="flex justify-between items-center text-sm">
              <span>
                {m.username}
                {isMe && ' (you)'}
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
    </aside>
  );
};
