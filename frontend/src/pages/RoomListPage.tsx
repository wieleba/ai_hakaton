import React, { useState, useEffect } from 'react';
import { ChatRoom } from '../types/room';
import { roomService } from '../services/roomService';
import { RoomCreateModal } from '../components/RoomCreateModal';
import { useNavigate } from 'react-router-dom';

export const RoomListPage: React.FC = () => {
  const navigate = useNavigate();
  const [rooms, setRooms] = useState<ChatRoom[]>([]);
  const [isLoading, setIsLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [isModalOpen, setIsModalOpen] = useState(false);
  const [currentPage, setCurrentPage] = useState(0);

  useEffect(() => {
    loadRooms();
  }, [currentPage]);

  const loadRooms = async () => {
    setIsLoading(true);
    setError(null);
    try {
      const result = await roomService.listPublicRooms(currentPage, 20);
      setRooms(result.content);
    } catch (err: any) {
      setError(err.message);
    } finally {
      setIsLoading(false);
    }
  };

  const handleCreateRoom = async (name: string, description?: string) => {
    try {
      const newRoom = await roomService.createRoom(name, description);
      navigate(`/rooms/${newRoom.id}`);
    } catch (err: any) {
      throw new Error(err.message);
    }
  };

  return (
    <div className="min-h-screen bg-gray-100 p-6">
      <div className="max-w-6xl mx-auto">
        <div className="flex justify-between items-center mb-8">
          <h1 className="text-3xl font-bold">Chat Rooms</h1>
          <button onClick={() => setIsModalOpen(true)} className="px-4 py-2 bg-blue-500 text-white rounded hover:bg-blue-600">New Room</button>
        </div>

        {error && <div className="text-red-500 mb-4">{error}</div>}
        {isLoading && <div>Loading rooms...</div>}

        <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-4">
          {rooms.map((room) => (
            <div key={room.id} className="bg-white rounded-lg shadow p-4 cursor-pointer hover:shadow-lg" onClick={() => navigate(`/rooms/${room.id}`)}>
              <h2 className="text-lg font-bold mb-2">{room.name}</h2>
              {room.description && <p className="text-gray-600 text-sm mb-4">{room.description}</p>}
              <div className="text-xs text-gray-400">Created {new Date(room.createdAt).toLocaleDateString()}</div>
            </div>
          ))}
        </div>

        <RoomCreateModal isOpen={isModalOpen} onClose={() => setIsModalOpen(false)} onCreate={handleCreateRoom} />
      </div>
    </div>
  );
};
