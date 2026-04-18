import React from 'react';
import type { ChatRoom } from '../types/room';

interface Props {
  isOpen: boolean;
  onClose: () => void;
  roomId: string;
  currentUserId: string;
  room: ChatRoom | null;
}

// Real tabs ship in Task 8.
export const ManageRoomModal: React.FC<Props> = ({ isOpen, onClose }) => {
  if (!isOpen) return null;
  return (
    <div className="fixed inset-0 bg-black bg-opacity-50 flex items-center justify-center z-50">
      <div className="bg-white rounded-lg p-6 w-96">
        <h2 className="text-xl font-bold mb-2">Manage room</h2>
        <p className="text-sm text-gray-600 mb-4">Tabs ship in the next commit.</p>
        <div className="flex justify-end">
          <button onClick={onClose} className="px-4 py-2 border rounded hover:bg-gray-100">
            Close
          </button>
        </div>
      </div>
    </div>
  );
};
