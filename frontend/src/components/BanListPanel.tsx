import React, { useCallback, useEffect, useState } from 'react';
import { roomService } from '../services/roomService';
import type { RoomBan } from '../types/roomModeration';

interface Props {
  isOpen: boolean;
  onClose: () => void;
  roomId: string;
}

export const BanListPanel: React.FC<Props> = ({ isOpen, onClose, roomId }) => {
  const [bans, setBans] = useState<RoomBan[]>([]);
  const [error, setError] = useState<string | null>(null);

  const reload = useCallback(async () => {
    try {
      setBans(await roomService.listBans(roomId));
    } catch (e) {
      setError(e instanceof Error ? e.message : String(e));
    }
  }, [roomId]);

  useEffect(() => {
    if (isOpen) reload();
  }, [isOpen, reload]);

  const unban = async (userId: string) => {
    await roomService.unbanMember(roomId, userId);
    setBans((prev) => prev.filter((b) => b.bannedUserId !== userId));
  };

  if (!isOpen) return null;
  return (
    <div className="fixed inset-0 bg-black bg-opacity-50 flex items-center justify-center z-50">
      <div className="bg-white rounded-lg p-6 w-[28rem]">
        <div className="flex justify-between items-center mb-4">
          <h2 className="text-xl font-bold">Banned users</h2>
          <button onClick={onClose} className="text-gray-500 hover:text-gray-700">
            ×
          </button>
        </div>
        {error && <div className="text-red-500 text-sm mb-4">{error}</div>}
        {bans.length === 0 ? (
          <p className="text-gray-500 italic">No banned users.</p>
        ) : (
          <ul className="divide-y">
            {bans.map((b) => (
              <li key={b.bannedUserId} className="flex justify-between items-center py-2">
                <div>
                  <div className="font-medium">{b.bannedUsername}</div>
                  <div className="text-xs text-gray-500">
                    banned by {b.bannedByUsername} on {new Date(b.bannedAt).toLocaleString()}
                  </div>
                </div>
                <button
                  onClick={() => unban(b.bannedUserId)}
                  className="px-3 py-1 border rounded hover:bg-gray-100"
                >
                  Unban
                </button>
              </li>
            ))}
          </ul>
        )}
      </div>
    </div>
  );
};
