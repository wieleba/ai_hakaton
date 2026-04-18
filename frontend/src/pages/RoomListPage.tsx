import React, { useEffect, useState } from 'react';
import { ChatRoom } from '../types/room';
import { roomService } from '../services/roomService';
import { RoomCreateModal } from '../components/RoomCreateModal';
import { useNavigate } from 'react-router-dom';

export const RoomListPage: React.FC = () => {
  const navigate = useNavigate();
  const [publicRooms, setPublicRooms] = useState<ChatRoom[]>([]);
  const [error, setError] = useState<string | null>(null);
  const [isModalOpen, setIsModalOpen] = useState(false);

  const reload = async () => {
    try {
      const result = await roomService.listPublicRooms(0, 20);
      setPublicRooms(result.content);
    } catch (err) {
      setError(err instanceof Error ? err.message : String(err));
    }
  };

  useEffect(() => {
    reload();
  }, []);

  const handleCreateRoom = async (
    name: string,
    description?: string,
    visibility?: 'public' | 'private',
  ) => {
    const newRoom = await roomService.createRoom(name, description, visibility);
    navigate(`/rooms/${newRoom.id}`);
  };

  const renderRoomCard = (room: ChatRoom) => (
    <div
      key={room.id}
      className="bg-white rounded-lg shadow p-4 cursor-pointer hover:shadow-lg"
      onClick={() => navigate(`/rooms/${room.id}`)}
    >
      <div className="flex justify-between items-start mb-2">
        <h2 className="text-lg font-bold">{room.name}</h2>
        <span
          className={`text-xs px-2 py-1 rounded ${
            room.visibility === 'private'
              ? 'bg-purple-100 text-purple-700'
              : 'bg-green-100 text-green-700'
          }`}
        >
          {room.visibility}
        </span>
      </div>
      {room.description && <p className="text-gray-600 text-sm mb-4">{room.description}</p>}
      <div className="text-xs text-gray-400">
        Created {new Date(room.createdAt).toLocaleDateString()}
      </div>
    </div>
  );

  return (
    <div className="h-full bg-gray-100 p-6 overflow-y-auto">
      <div className="max-w-6xl mx-auto">
        <div className="flex justify-between items-center mb-6">
          <h1 className="text-3xl font-bold">Public Rooms</h1>
          <button
            onClick={() => setIsModalOpen(true)}
            className="px-4 py-2 bg-blue-500 text-white rounded hover:bg-blue-600"
          >
            New Room
          </button>
        </div>

        {error && <div className="text-red-500 mb-4">{error}</div>}

        <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-4">
          {publicRooms.map(renderRoomCard)}
        </div>
        {publicRooms.length === 0 && (
          <p className="text-gray-500 italic">No public rooms yet.</p>
        )}

        <RoomCreateModal
          isOpen={isModalOpen}
          onClose={() => setIsModalOpen(false)}
          onCreate={handleCreateRoom}
        />
      </div>
    </div>
  );
};
