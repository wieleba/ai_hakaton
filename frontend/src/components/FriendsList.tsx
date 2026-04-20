import React from 'react';
import { useNavigate } from 'react-router-dom';
import type { FriendView } from '../types/friendship';
import { directMessageService } from '../services/directMessageService';

interface Props {
  friends: FriendView[];
  onRemove: (userId: string) => void;
  onBan: (userId: string) => void;
}

export const FriendsList: React.FC<Props> = ({ friends, onRemove, onBan }) => {
  const navigate = useNavigate();

  const openDm = async (userId: string) => {
    const conv = await directMessageService.getOrCreateWith(userId);
    navigate(`/dms/${conv.id}`);
  };

  if (friends.length === 0) {
    return <div className="text-gray-500 italic dark:text-discord-dim">No friends yet</div>;
  }

  return (
    <ul className="divide-y dark:divide-discord-border">
      {friends.map((f) => (
        <li key={f.userId} className="flex justify-between items-center py-2">
          <span className="font-medium">{f.username}</span>
          <div className="space-x-2">
            <button
              onClick={() => openDm(f.userId)}
              className="px-3 py-1 bg-blue-500 text-white rounded dark:bg-discord-accent dark:hover:bg-indigo-500"
            >
              Message
            </button>
            <button
              onClick={() => onRemove(f.userId)}
              className="px-3 py-1 border rounded dark:border-discord-border dark:hover:bg-discord-hover"
            >
              Remove
            </button>
            <button
              onClick={() => onBan(f.userId)}
              className="px-3 py-1 border border-red-500 text-red-600 rounded dark:border-red-600 dark:text-red-300 dark:hover:bg-red-900/30"
            >
              Ban
            </button>
          </div>
        </li>
      ))}
    </ul>
  );
};
