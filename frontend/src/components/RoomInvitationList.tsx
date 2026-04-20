import React from 'react';
import type { RoomInvitation } from '../types/roomModeration';

interface Props {
  invitations: RoomInvitation[];
  onAccept: (id: string) => Promise<void> | void;
  onDecline: (id: string) => Promise<void> | void;
}

export const RoomInvitationList: React.FC<Props> = ({ invitations, onAccept, onDecline }) => {
  if (invitations.length === 0) return null;
  return (
    <section className="mb-6 border rounded bg-yellow-50 p-4 dark:bg-yellow-900/20 dark:border-yellow-700/50 dark:text-discord-text">
      <h2 className="font-semibold mb-2">Pending invitations</h2>
      <ul className="space-y-2">
        {invitations.map((inv) => (
          <li
            key={inv.id}
            className="flex justify-between items-center bg-white rounded px-3 py-2 border dark:bg-discord-sidebar dark:border-discord-border"
          >
            <span>
              <strong>{inv.inviterUsername}</strong> invited you to <strong>{inv.roomName}</strong>
            </span>
            <div className="space-x-2">
              <button
                onClick={() => onAccept(inv.id)}
                className="px-3 py-1 bg-green-500 text-white rounded hover:bg-green-600 dark:bg-green-700 dark:hover:bg-green-600"
              >
                Accept
              </button>
              <button
                onClick={() => onDecline(inv.id)}
                className="px-3 py-1 border rounded hover:bg-gray-100 dark:border-discord-border dark:hover:bg-discord-hover"
              >
                Decline
              </button>
            </div>
          </li>
        ))}
      </ul>
    </section>
  );
};
